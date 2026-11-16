package com.example.springai.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * RAG 问答请求。
 *
 * <p>召回参数留空时使用 {@code app.ai.rag.*} 中的默认值，便于前端在「调试模式」
 * 下按需调大召回数量或放宽阈值。
 */
@Data
public class RagChatRequest {

    /** 用户问题 */
    @NotBlank(message = "问题内容不能为空")
    private String message;

    /** 召回片段数，留空使用默认值 */
    private Integer topK;

    /** 相似度阈值（0~1），留空使用默认值；越小召回越多但噪声也越多 */
    private Double similarityThreshold;
}
