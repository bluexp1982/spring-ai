package com.example.springai.ai;

import com.example.springai.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.Disposable;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * AI 对话接口。
 *
 * 注意：本项目使用 Spring MVC（spring-boot-starter-webmvc），在当前
 * Spring Boot 4 / Spring Framework 7 下，直接返回 Flux 或 SseEmitter
 * 都无法被正确处理（会抛 HttpMessageNotWritableException）。
 * 因此流式接口直接向 HttpServletResponse 写入 SSE 帧，绕开消息转换器。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiChatController {

    /** SSE 流式响应的最长等待时间（秒），超时后主动释放订阅，避免请求线程被长时间占用。 */
    private static final long STREAM_TIMEOUT_SECONDS = 180L;

    /** 复用的 JSON 序列化器，用于把流式分片编码为 SSE 数据帧（避免原文换行破坏帧格式）。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** AI 对话业务服务，负责 Prompt 组装、模型调用与对话记忆读写。 */
    private final AiChatService aiChatService;

    /**
     * 非流式对话：一次性返回 AI 完整回复。
     *
     * 注意：前端聊天页面走的是流式接口 {@link #chatStream}，本接口目前主要
     * 用于接口调试或第三方集成。
     *
     * @param request 对话请求体，仅包含本轮用户消息
     * @return 统一响应包装的 AI 回复文本
     */
    @PostMapping("/chat")
    public Result<String> chat(@Valid @RequestBody AiChatRequest request) {
        return Result.success(aiChatService.chat(currentConversationId(), request.getMessage()));
    }

    /**
     * 清空当前登录用户的对话记忆。
     *
     * @return 无业务数据的统一成功响应
     */
    @PostMapping("/chat/clear")
    public Result<Void> clearChat() {
        aiChatService.clearMemory(currentConversationId());
        return Result.success();
    }

    /**
     * 查询当前登录用户的历史对话记录，用于前端页面载入时回显对话气泡。
     *
     * @return 统一响应包装的历史消息列表（仅含用户与助手消息）
     */
    @GetMapping("/chat/history")
    public Result<List<AiChatMessage>> history() {
        return Result.success(aiChatService.getHistory(currentConversationId()));
    }

    /**
     * 流式对话：以 SSE 逐帧推送 AI 回复增量。
     *
     * 因 Spring Boot 4 / Spring Framework 7 的消息转换器限制，这里直接向
     * {@link HttpServletResponse} 写 SSE 帧；客户端断开或等待超时后释放订阅。
     *
     * @param request  对话请求体，仅包含本轮用户消息
     * @param response 用于直接写入 text/event-stream 的响应对象
     * @throws IOException 获取响应输出流失败时抛出
     */
    @PostMapping("/chat/stream")
    public void chatStream(@Valid @RequestBody AiChatRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
        // 关闭 Nginx 等反向代理的缓冲，保证流式实时输出
        response.setHeader("X-Accel-Buffering", "no");

        PrintWriter writer = response.getWriter();
        CountDownLatch latch = new CountDownLatch(1);

        Disposable subscription = aiChatService.chatStream(currentConversationId(), request.getMessage())
                .subscribe(
                        chunk -> {
                            try {
                                // JSON 编码：避免回复内容中的换行破坏 SSE 帧格式
                                writer.write("data:" + MAPPER.writeValueAsString(chunk) + "\n\n");
                                writer.flush();
                            } catch (Exception e) {
                                // 客户端断开连接等情况
                                log.debug("SSE 写入中断: {}", e.getMessage());
                                latch.countDown();
                            }
                        },
                        error -> {
                            log.error("AI 流式响应出错", error);
                            writeError(writer, "AI 服务响应失败");
                            latch.countDown();
                        },
                        latch::countDown
                );

        try {
            latch.await(STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            subscription.dispose();
        }
    }

    /**
     * 向 SSE 连接写入一条 error 事件帧，通知前端本次响应失败。
     *
     * @param writer  响应输出流
     * @param message 错误提示文本
     */
    private void writeError(PrintWriter writer, String message) {
        try {
            writer.write("event:error\ndata:" + message + "\n\n");
            writer.flush();
        } catch (Exception ignored) {
            // 连接已断开，忽略
        }
    }

    /**
     * 以登录用户名作为会话标识，用于隔离不同用户的对话记忆。
     * 必须在请求线程（此处）取好，Reactor 线程无法访问 SecurityContext。
     */
    private String currentConversationId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "anonymous";
    }
}