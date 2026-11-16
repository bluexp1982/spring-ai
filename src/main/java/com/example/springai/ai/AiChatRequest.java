package com.example.springai.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 对话请求体。
 *
 * 非流式 {@code /api/ai/chat} 与流式 {@code /api/ai/chat/stream} 共用，
 * 仅承载本轮用户提问；历史上下文由后端根据会话记忆自动拼装。
 */
@Data
public class AiChatRequest {

    /** 本轮用户提问内容，不能为空白字符串。 */
    @NotBlank(message = "消息内容不能为空")
    private String message;
}