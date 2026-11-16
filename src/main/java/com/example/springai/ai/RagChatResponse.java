package com.example.springai.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * RAG 问答响应。
 *
 * <p>同时返回答案与引用来源：{@code sources} 是 {@code QuestionAnswerAdvisor}
 * 实际注入 Prompt 的片段，与模型看到的上下文完全一致，因此可用于核对答案是否有据可依。
 */
@Data
@AllArgsConstructor
public class RagChatResponse {

    /** 模型基于召回资料生成的回答 */
    private String answer;

    /** 本次回答依据的知识库片段 */
    private List<RagSource> sources;
}
