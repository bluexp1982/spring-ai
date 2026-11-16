package com.example.springai.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * RAG 检索命中的知识库片段。
 *
 * <p>纯检索接口与 RAG 问答接口共用该结构：问答接口把它一并返回，
 * 前端即可在答案下方展示「引用来源」，让回答可追溯。
 */
@Data
@AllArgsConstructor
public class RagSource {

    /** 向量库中的文档 ID（入库时自动生成，删除时使用） */
    private String id;

    /** 片段原文 */
    private String content;

    /** 相似度得分（当前配置为 COSINE，越接近 1 越相关） */
    private Double score;

    /** 入库时携带的元数据，可用于展示来源、页码等信息 */
    private Map<String, Object> metadata;
}
