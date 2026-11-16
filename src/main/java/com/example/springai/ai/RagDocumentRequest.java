package com.example.springai.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 知识库入库请求中的单条文档片段。
 *
 * <p>一个业务文档通常会被切分成多个片段分批入库，检索时命中的是片段而非整篇文档。
 */
@Data
public class RagDocumentRequest {

    /** 片段正文，向量化后写入 Milvus */
    @NotBlank(message = "文档内容不能为空")
    private String content;

    /**
     * 自定义元数据，可选。
     *
     * <p>用途是给片段打标（如 {@code {"source": "产品手册", "page": 12}}），
     * 既方便前端展示引用出处，也可作为检索过滤条件。
     */
    private Map<String, Object> metadata;
}
