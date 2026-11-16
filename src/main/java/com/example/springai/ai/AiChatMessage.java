package com.example.springai.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * AI 对话历史消息（用于前端页面载入时回显气泡）。
 */
@Data
@AllArgsConstructor
public class AiChatMessage {

    /** 角色：user / assistant */
    private String role;

    /** 消息文本 */
    private String content;
}
