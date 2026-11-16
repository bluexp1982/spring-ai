package com.example.springai.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * 知识库列表中的单个片段。
 *
 * <p>与 {@link RagSource} 的区别：列表项没有参与检索，不存在相似度概念，
 * 因此这里不含 {@code score} 字段，避免出现恒为 null 的无意义属性。
 */
@Data
@AllArgsConstructor
public class RagDocument {

    /** 向量库中的文档 ID，删除时使用 */
    private String id;

    /** 片段正文 */
    private String content;

    /** 入库时携带的元数据 */
    private Map<String, Object> metadata;
}
