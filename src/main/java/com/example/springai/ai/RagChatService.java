package com.example.springai.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RAG（检索增强生成）核心服务。
 *
 * <p>负责知识库的写入、向量检索与基于 {@link QuestionAnswerAdvisor} 的问答：
 * <ul>
 *   <li><b>写入</b>：{@link #ingest} 把文本片段向量化后落库到 Milvus；</li>
 *   <li><b>检索</b>：{@link #search} 只走向量库、不调用大模型，用于调试召回效果；</li>
 *   <li><b>问答</b>：{@link #ask} 由 {@link QuestionAnswerAdvisor} 自动完成
 *       「取用户问题 → 向量检索 → 把命中片段拼进 Prompt」三步，模型只负责基于资料作答。</li>
 * </ul>
 *
 * <p>与 {@link AiChatService} 的分工：本服务是<b>无状态</b>的单轮问答，
 * 不读写 {@code ChatMemory}、不注册工具，避免与主对话链路相互干扰；
 * 知识库的召回范围与相似度阈值支持按请求覆盖。
 */
@Service
@Slf4j
public class RagChatService {

    /**
     * RAG 专用系统提示词。
     *
     * <p>核心目的是「防幻觉」：把模型约束在检索到的资料范围内，
     * 资料不足时明确说不知道，而不是用自身先验知识编一个像样的答案。
     */
    private static final String SYSTEM_PROMPT = """
            你是一位严谨的知识库问答助手，服务于一个内部资料库检索系统。
            回答必须严格依据用户消息中提供的资料，遵守以下规则：
            - 只使用资料中的信息作答，禁止使用资料以外的知识，也不要自行推测或补充细节；
            - 资料不足以回答问题时，直接说明「知识库中未找到相关信息」，并提示用户补充或调整问法，
              绝对不要编造答案；
            - 使用简体中文回答，可用 Markdown 组织内容（列表、表格、加粗），不要输出 HTML。
            """;

    /**
     * 问答提示词模板，用于替换 {@link QuestionAnswerAdvisor} 的英文默认模板。
     *
     * <p>两个占位符由框架填充，名称不可改动：
     * {@code query} 为用户问题，{@code question_answer_context} 为检索到的资料拼接文本。
     */
    private static final PromptTemplate QA_PROMPT_TEMPLATE = new PromptTemplate("""
            {query}

            --------------------
            以下是知识库检索到的资料：
            {question_answer_context}
            --------------------

            请严格依据以上资料回答上面的问题。资料中没有提及的内容不要回答。
            """);

    private final VectorStore vectorStore;

    /** 未挂任何 Advisor 的基础客户端，检索能力在每次请求时按参数动态挂载。 */
    private final ChatClient chatClient;

    /** 默认召回片段数 */
    private final int defaultTopK;

    /** 默认相似度阈值（COSINE，0~1） */
    private final double defaultSimilarityThreshold;

    public RagChatService(ChatModel chatModel,
                          VectorStore vectorStore,
                          @Value("${app.ai.rag.top-k:4}") int defaultTopK,
                          @Value("${app.ai.rag.similarity-threshold:0.5}") double defaultSimilarityThreshold) {
        this.vectorStore = vectorStore;
        this.defaultTopK = defaultTopK;
        this.defaultSimilarityThreshold = defaultSimilarityThreshold;
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    /**
     * 写入知识库：片段向量化后存入 Milvus。
     *
     * @param requests 待入库片段列表
     * @return 向量库为每条片段生成的文档 ID，可用于后续删除
     */
    public List<String> ingest(List<RagDocumentRequest> requests) {
        List<Document> documents = requests.stream()
                .map(request -> new Document(request.getContent(),
                        request.getMetadata() == null ? new HashMap<>() : new HashMap<>(request.getMetadata())))
                .toList();

        vectorStore.add(documents);

        List<String> ids = documents.stream().map(Document::getId).toList();
        log.info("知识库写入完成, 片段数={}", ids.size());
        return ids;
    }

    /**
     * 长文本入库：按段落切分后批量写入知识库。
     *
     * <p>切分策略见 {@link RagTextSplitter}。每个片段都会带上 {@code source} 元数据，
     * 这样检索命中后前端能展示「这段话出自哪份文件」。
     *
     * @param text      原始文本（如上传的 txt / md 内容）
     * @param source    来源标识，通常为文件名；为空时不写入该元数据
     * @param chunkSize 单片最大字符数，为空取默认值
     * @return 向量库生成的文档 ID 列表；文本为空时返回空列表
     */
    public List<String> ingestText(String text, String source, Integer chunkSize) {
        List<String> chunks = RagTextSplitter.split(text, chunkSize);
        if (chunks.isEmpty()) {
            return List.of();
        }

        String resolvedSource = (source == null || source.isBlank()) ? null : source.strip();
        List<RagDocumentRequest> requests = new ArrayList<>(chunks.size());
        for (String chunk : chunks) {
            RagDocumentRequest request = new RagDocumentRequest();
            request.setContent(chunk);
            if (resolvedSource != null) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", resolvedSource);
                request.setMetadata(metadata);
            }
            requests.add(request);
        }
        return ingest(requests);
    }

    /**
     * 纯向量检索：只查 Milvus，不调用大模型。
     *
     * <p>用途是观察召回质量（命中哪些片段、相似度多高），
     * 排查「答非所问」时可以先看这里，快速区分是召回问题还是生成问题。
     *
     * @param query      查询文本
     * @param topK       召回数量，为空取配置默认值
     * @param threshold  相似度阈值，为空取配置默认值
     * @return 命中的片段（按相似度降序）
     */
    public List<RagSource> search(String query, Integer topK, Double threshold) {
        List<Document> documents = vectorStore.similaritySearch(buildSearchRequest(query, topK, threshold));
        return toSources(documents);
    }

    /**
     * RAG 问答：检索 + 生成。
     *
     * <p>{@link QuestionAnswerAdvisor} 会在请求前自动用用户问题发起向量检索，
     * 把命中片段按 {@link #QA_PROMPT_TEMPLATE} 格式化后替换用户消息，
     * 并把命中结果写入上下文，因此这里能从中取出「实际喂给模型的资料」一并返回。
     *
     * @param request 问答请求（问题 + 可选召回参数）
     * @return 模型回答及其依据的知识库片段
     */
    public RagChatResponse ask(RagChatRequest request) {
        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(buildSearchRequest(request.getMessage(),
                        request.getTopK(), request.getSimilarityThreshold()))
                .promptTemplate(QA_PROMPT_TEMPLATE)
                .build();

        ChatClientResponse response = chatClient.prompt()
                .user(request.getMessage())
                .advisors(advisor)
                .call()
                .chatClientResponse();

        List<RagSource> sources = toSources(extractRetrievedDocuments(response));
        String answer = extractAnswer(response);
        log.debug("RAG 问答完成, 召回片段数={}", sources.size());
        return new RagChatResponse(answer, sources);
    }

    /**
     * RAG 流式问答：与 {@link #ask} 走同一条检索链路，但以事件流逐段产出。
     *
     * <p><b>来源帧一定先到</b>：{@link QuestionAnswerAdvisor} 是在发起模型请求
     * <em>之前</em>同步完成检索并写入上下文的，因此流中首个
     * {@link ChatClientResponse} 必然携带命中片段，前端可以先渲染「引用来源」再等正文。
     * 这也意味着流式链路<b>不需要二次检索</b>，来源与模型看到的资料严格一致。
     *
     * @param request 问答请求（问题 + 可选召回参数）
     * @return 事件流：先一个 {@code SOURCES} 事件，随后若干 {@code CONTENT} 事件
     */
    public Flux<RagStreamEvent> chatStream(RagChatRequest request) {
        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(buildSearchRequest(request.getMessage(),
                        request.getTopK(), request.getSimilarityThreshold()))
                .promptTemplate(QA_PROMPT_TEMPLATE)
                .build();

        // 来源帧只需推一次；用 CAS 标记，保证并发下也只有一个分片携带它
        AtomicBoolean sourcesPushed = new AtomicBoolean(false);

        return chatClient.prompt()
                .user(request.getMessage())
                .advisors(advisor)
                .stream()
                .chatClientResponse()
                .concatMap(response -> {
                    String content = extractAnswer(response);
                    // 部分分片（如仅含 usage 统计的收尾块）文本为 null，跳过避免 Reactor 抛 NPE
                    Flux<RagStreamEvent> contentFlux = content == null || content.isEmpty()
                            ? Flux.empty()
                            : Flux.just(RagStreamEvent.content(content));

                    if (sourcesPushed.compareAndSet(false, true)) {
                        List<RagSource> sources = toSources(extractRetrievedDocuments(response));
                        log.debug("RAG 流式问答已召回片段, 数量={}", sources.size());
                        return Flux.concat(Flux.just(RagStreamEvent.sources(sources)), contentFlux);
                    }
                    return contentFlux;
                });
    }

    /**
     * 按文档 ID 批量删除知识库片段。
     *
     * @param ids 入库时返回的文档 ID
     */
    public void delete(List<String> ids) {
        vectorStore.delete(ids);
        log.info("知识库删除完成, 片段数={}", ids.size());
    }

    /**
     * 组装检索请求；未传的参数回落到配置默认值。
     *
     * <p>注意 {@code query} 对被 {@link QuestionAnswerAdvisor} 使用的请求而言只是占位，
     * 真正生效的是运行时从用户消息取到的文本；纯检索场景下它就是查询语句本身。
     */
    private SearchRequest buildSearchRequest(String query, Integer topK, Double threshold) {
        return SearchRequest.builder()
                .query(query)
                .topK(topK != null && topK > 0 ? topK : defaultTopK)
                .similarityThreshold(threshold != null ? threshold : defaultSimilarityThreshold)
                .build();
    }

    /**
     * 从响应上下文中取出 {@link QuestionAnswerAdvisor} 实际召回并注入 Prompt 的片段。
     *
     * <p>该 Advisor 在请求前把结果以 {@code List<Document>} 写入上下文键
     * {@link QuestionAnswerAdvisor#RETRIEVED_DOCUMENTS}，响应上下文原样透传，
     * 因此这里读到的与模型看到的是同一批资料，可作为「引用来源」返回给前端。
     */
    @SuppressWarnings("unchecked")
    private List<Document> extractRetrievedDocuments(ChatClientResponse response) {
        Map<String, Object> context = response.context();
        if (context == null) {
            return List.of();
        }
        Object retrieved = context.get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        return retrieved instanceof List<?> list ? (List<Document>) list : List.of();
    }

    /** 提取模型回复文本；模型未返回内容（异常收尾块）时返回 null。 */
    private String extractAnswer(ChatClientResponse response) {
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null || chatResponse.getResult() == null) {
            return null;
        }
        return chatResponse.getResult().getOutput().getText();
    }

    /** 把向量库文档转换为接口返回结构。 */
    private List<RagSource> toSources(List<Document> documents) {
        return documents.stream()
                .map(document -> new RagSource(document.getId(), document.getText(),
                        document.getScore(), document.getMetadata()))
                .toList();
    }
}
