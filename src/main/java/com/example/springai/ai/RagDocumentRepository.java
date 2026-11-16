package com.example.springai.ai;

import com.example.springai.common.PageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.QueryResults;
import io.milvus.param.R;
import io.milvus.param.dml.QueryParam;
import io.milvus.response.QueryResultsWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 知识库片段的查询仓储。
 *
 * <p>存在的理由：Spring AI 的 {@link VectorStore} 接口只有
 * {@code add / delete / similaritySearch}，<b>不具备遍历能力</b>，
 * 因此「列出知识库里有哪些片段」必须绕过接口、通过 {@link VectorStore#getNativeClient()}
 * 拿 Milvus 原生客户端直接查集合。
 *
 * <p>本类只读，不承担写入与删除职责（那些仍走 {@link VectorStore} 接口）。
 */
@Component
@Slf4j
public class RagDocumentRepository {

    /** Milvus 的「匹配全部」表达式：主键非空即命中集合内所有片段 */
    private static final String MATCH_ALL_EXPR = "doc_id != \"\"";

    /** 统计总数时使用的聚合输出字段名，由 Milvus 固定返回 */
    private static final String COUNT_FIELD = "count(*)";

    /** 列表查询的返回字段；不取 embedding，避免把 1024 维向量拉回内存 */
    private static final List<String> LIST_OUT_FIELDS = List.of("doc_id", "content", "metadata");

    /** 单页最大条数，防止前端传入超大 size 把 Milvus 查爆 */
    private static final long MAX_PAGE_SIZE = 100L;

    /** 复用的 JSON 解析器，用于把 Milvus 返回的 metadata 转成 Map */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VectorStore vectorStore;

    /** 集合名，与 spring.ai.vectorstore.milvus 配置保持一致 */
    private final String collectionName;

    /** 数据库名，与 spring.ai.vectorstore.milvus 配置保持一致 */
    private final String databaseName;

    public RagDocumentRepository(VectorStore vectorStore,
                                 @Value("${spring.ai.vectorstore.milvus.collection-name:vector_store}") String collectionName,
                                 @Value("${spring.ai.vectorstore.milvus.database-name:default}") String databaseName) {
        this.vectorStore = vectorStore;
        this.collectionName = collectionName;
        this.databaseName = databaseName;
    }

    /**
     * 分页查询知识库片段。
     *
     * @param keyword 关键词，非空时按正文模糊匹配
     * @param page    页码，从 1 开始；非法值回落到 1
     * @param size    每页条数；会被夹逼到 [1, {@value #MAX_PAGE_SIZE}]
     * @return 分页结果，字段含义与项目其他分页接口一致
     */
    public PageResult<RagDocument> page(String keyword, long page, long size) {
        long safePage = Math.max(page, 1L);
        long safeSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        String expr = buildExpression(keyword);

        // 先统计总数再决定是否取明细：空集合时省掉一次 Milvus 查询
        long total = count(expr);

        PageResult<RagDocument> result = new PageResult<>();
        result.setTotal(total);
        result.setPage(safePage);
        result.setSize(safeSize);
        result.setRecords(total == 0 ? List.<RagDocument>of() : list(expr, safePage, safeSize));
        return result;
    }

    /** 统计符合条件的片段总数；Milvus 用 {@code count(*)} 聚合返回单个 Long。 */
    private long count(String expr) {
        QueryParam param = QueryParam.newBuilder()
                .withDatabaseName(databaseName)
                .withCollectionName(collectionName)
                .withExpr(expr)
                .withOutFields(List.of(COUNT_FIELD))
                .build();

        QueryResultsWrapper wrapper = new QueryResultsWrapper(execute(param, "统计知识库片段数量").getData());
        List<?> values = wrapper.getFieldWrapper(COUNT_FIELD).getFieldData();
        return values.isEmpty() ? 0L : ((Number) values.get(0)).longValue();
    }

    /**
     * 取指定页的片段明细。
     *
     * <p>Milvus 查询结果是「按字段并列的多个列表」，因此这里按索引把三个字段
     * 拼回对象；三个列表长度由服务端保证一致。
     */
    private List<RagDocument> list(String expr, long page, long size) {
        QueryParam param = QueryParam.newBuilder()
                .withDatabaseName(databaseName)
                .withCollectionName(collectionName)
                .withExpr(expr)
                .withOutFields(LIST_OUT_FIELDS)
                .withOffset((page - 1) * size)
                .withLimit(size)
                .build();

        QueryResultsWrapper wrapper = new QueryResultsWrapper(execute(param, "查询知识库片段").getData());
        List<?> ids = wrapper.getFieldWrapper("doc_id").getFieldData();
        List<?> contents = wrapper.getFieldWrapper("content").getFieldData();
        List<?> metadatas = wrapper.getFieldWrapper("metadata").getFieldData();

        List<RagDocument> documents = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            documents.add(new RagDocument(
                    String.valueOf(ids.get(i)),
                    String.valueOf(contents.get(i)),
                    toMetadata(i < metadatas.size() ? metadatas.get(i) : null)));
        }
        return documents;
    }

    /** 执行查询并校验 Milvus 状态码，失败时抛出带业务语义的异常。 */
    private R<QueryResults> execute(QueryParam param, String action) {
        R<QueryResults> response = milvusClient().query(param);
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException(action + "失败: " + response.getMessage());
        }
        return response;
    }

    /**
     * 拼装过滤表达式。
     *
     * <p>关键词按正文做中缀模糊匹配。用户输入会原样进入 Milvus 表达式，
     * 因此必须转义反斜杠与双引号——否则一个引号就能拼出非法表达式。
     */
    private String buildExpression(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return MATCH_ALL_EXPR;
        }
        String escaped = keyword.replace("\\", "\\\\").replace("\"", "\\\"");
        return "content like \"%" + escaped + "%\"";
    }

    /**
     * 把 Milvus 返回的 JSON 字段转成 Map。
     *
     * <p>JSON 字段在 SDK 中是 gson 的 {@code JsonObject}，这里统一转成
     * {@code Map} 以便直接序列化给前端；解析失败时退化为空元数据，不影响列表可用性。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMetadata(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        try {
            return OBJECT_MAPPER.readValue(raw.toString(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("解析知识库片段元数据失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 取 Milvus 原生客户端；若当前 VectorStore 不是 Milvus 实现则明确报错。 */
    private MilvusServiceClient milvusClient() {
        Optional<MilvusServiceClient> client = vectorStore.getNativeClient();
        return client.orElseThrow(() -> new IllegalStateException(
                "当前 VectorStore 未暴露 Milvus 原生客户端，知识库列表功能不可用"));
    }
}
