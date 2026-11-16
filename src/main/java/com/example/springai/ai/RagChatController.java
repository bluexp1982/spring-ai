package com.example.springai.ai;

import com.example.springai.common.PageResult;
import com.example.springai.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.Disposable;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * RAG（检索增强生成）知识库接口。
 *
 * <p>提供以下能力，覆盖「建库 → 管理 → 看召回 → 问答 → 清理」的完整闭环：
 * <ol>
 *   <li>{@code POST /documents} 写入知识库片段；</li>
 *   <li>{@code POST /documents/upload} 上传 txt/md 文件并按段落切分入库；</li>
 *   <li>{@code GET  /documents} 分页列出知识库片段，支持正文关键词过滤；</li>
 *   <li>{@code GET  /documents/search} 纯向量检索，不调用大模型，用于调试召回效果；</li>
 *   <li>{@code POST /chat} 基于 {@link org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor}
 *       的检索增强问答，返回答案与实际引用的片段；</li>
 *   <li>{@code POST /chat/stream} 同上，但以 SSE 流式返回，并先推送引用来源；</li>
 *   <li>{@code DELETE /documents} 按 ID 删除片段。</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/ai/rag")
@RequiredArgsConstructor
@Slf4j
public class RagChatController {

    /** SSE 流式响应的最长等待时间（秒），超时后主动释放订阅，避免请求线程被长时间占用。 */
    private static final long STREAM_TIMEOUT_SECONDS = 180L;

    /** 复用的 JSON 序列化器，用于把事件编码为 SSE 数据帧（避免原文换行破坏帧格式）。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** RAG 业务服务，负责向量库读写与检索增强问答。 */
    private final RagChatService ragChatService;

    /** 知识库片段查询仓储（列表能力不在 VectorStore 接口内，故单独封装）。 */
    private final RagDocumentRepository ragDocumentRepository;

    /**
     * 写入知识库：批量入库文本片段，自动向量化。
     *
     * @param documents 片段列表（正文 + 可选元数据）
     * @return 向量库生成的文档 ID 列表，删除时使用
     */
    @PostMapping("/documents")
    public Result<List<String>> ingest(@Valid @RequestBody List<@Valid RagDocumentRequest> documents) {
        return Result.success(ragChatService.ingest(documents));
    }

    /**
     * 上传 txt / md 文件入库：服务端按段落自动切分后批量写入。
     *
     * <p>只接受纯文本类扩展名，避免把二进制文件按 UTF-8 读成乱码写进知识库、
     * 污染检索结果（这类脏数据很难排查）。
     *
     * @param file      上传的文件
     * @param source    来源标识，省略则取文件名
     * @param chunkSize 单片最大字符数，省略取服务端默认值
     * @return 向量库生成的文档 ID 列表
     * @throws IOException 读取上传文件失败时抛出
     */
    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<String>> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam(required = false) String source,
                                       @RequestParam(required = false) Integer chunkSize) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.failed(400, "上传文件不能为空");
        }

        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!isPlainTextFile(filename)) {
            return Result.failed(400, "仅支持上传 .txt / .md 文件");
        }

        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        String resolvedSource = (source != null && !source.isBlank()) ? source : filename;
        List<String> ids = ragChatService.ingestText(text, resolvedSource, chunkSize);
        if (ids.isEmpty()) {
            return Result.failed(400, "文件内容为空，未入库任何片段");
        }

        log.info("知识库文件入库完成, 文件={}, 片段数={}", filename, ids.size());
        return Result.success(ids);
    }

    /** 仅放行纯文本类扩展名。 */
    private boolean isPlainTextFile(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    /**
     * 分页列出知识库片段，支持按正文关键词过滤。
     *
     * <p>供前端「知识库」页面展示与删除使用；{@code size} 由服务端夹逼，
     * 无需前端做上限校验。
     *
     * @param page    页码，从 1 开始
     * @param size    每页条数
     * @param keyword 正文关键词，省略则返回全部
     * @return 统一响应包装的分页结果
     */
    @GetMapping("/documents")
    public Result<PageResult<RagDocument>> page(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) String keyword) {
        return Result.success(ragDocumentRepository.page(keyword, page, size));
    }

    /**
     * 纯向量检索：只走向量库，不调用大模型。
     *
     * <p>用于单独观察召回结果与相似度得分，是排查 RAG 效果的第一站。
     *
     * @param query       查询文本
     * @param topK        召回数量，省略则用服务端默认值
     * @param threshold   相似度阈值（0~1），省略则用服务端默认值
     * @return 命中的知识库片段（按相似度降序）
     */
    @GetMapping("/documents/search")
    public Result<List<RagSource>> search(@RequestParam String query,
                                          @RequestParam(required = false) Integer topK,
                                          @RequestParam(required = false) Double threshold) {
        return Result.success(ragChatService.search(query, topK, threshold));
    }

    /**
     * RAG 问答：检索知识库后由大模型基于资料作答。
     *
     * @param request 问题与可选的召回参数
     * @return 模型回答及其实际依据的知识库片段
     */
    @PostMapping("/chat")
    public Result<RagChatResponse> chat(@Valid @RequestBody RagChatRequest request) {
        return Result.success(ragChatService.ask(request));
    }

    /**
     * RAG 流式问答：以 SSE 推送，先给引用来源，再逐段推正文。
     *
     * <p>帧格式（用 {@code event:} 区分类型）：
     * <pre>
     * event:sources
     * data:[{"id":"...","content":"...","score":0.8,"metadata":{...}}]
     *
     * data:"正文分片"
     * data:"正文分片"
     *
     * event:done
     * data:
     * </pre>
     * 正文帧不带 {@code event:}，格式与现有 {@code /api/ai/chat/stream} 完全一致；
     * 失败时发送 {@code event:error}。
     *
     * <p>与 {@link AiChatController#chatStream} 同理，因 Spring Boot 4 下直接返回
     * Flux / SseEmitter 会被消息转换器拒绝，这里直接向 {@link HttpServletResponse} 写帧。
     *
     * @param request  问题与可选的召回参数
     * @param response 用于直接写入 text/event-stream 的响应对象
     * @throws IOException 获取响应输出流失败时抛出
     */
    @PostMapping("/chat/stream")
    public void chatStream(@Valid @RequestBody RagChatRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
        // 关闭 Nginx 等反向代理的缓冲，保证流式实时输出
        response.setHeader("X-Accel-Buffering", "no");

        PrintWriter writer = response.getWriter();
        CountDownLatch latch = new CountDownLatch(1);

        Disposable subscription = ragChatService.chatStream(request)
                .subscribe(
                        event -> {
                            if (!writeEvent(writer, event)) {
                                // 客户端断开：提前释放请求线程，不必等满超时
                                latch.countDown();
                            }
                        },
                        error -> {
                            log.error("RAG 流式响应出错", error);
                            writeError(writer, "AI 服务响应失败");
                            latch.countDown();
                        },
                        () -> {
                            // 正常结束：补一个 done 帧，前端据此收起等待态
                            writeRaw(writer, "event:done\ndata:\n\n");
                            latch.countDown();
                        });

        try {
            latch.await(STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            subscription.dispose();
        }
    }

    /**
     * 把服务层事件编码为 SSE 帧。
     *
     * @return 写入成功返回 {@code true}；客户端已断开返回 {@code false}
     */
    private boolean writeEvent(PrintWriter writer, RagStreamEvent event) {
        try {
            if (event.type() == RagStreamEvent.Type.SOURCES) {
                writer.write("event:sources\ndata:" + MAPPER.writeValueAsString(event.sources()) + "\n\n");
            } else {
                writer.write("data:" + MAPPER.writeValueAsString(event.content()) + "\n\n");
            }
            writer.flush();
            return true;
        } catch (Exception e) {
            // 客户端断开连接等情况
            log.debug("SSE 写入中断: {}", e.getMessage());
            return false;
        }
    }

    /** 写入一段原始 SSE 文本，失败时静默忽略（连接已断开）。 */
    private void writeRaw(PrintWriter writer, String frame) {
        try {
            writer.write(frame);
            writer.flush();
        } catch (Exception ignored) {
            // 连接已断开，忽略
        }
    }

    /** 向 SSE 连接写入一条 error 事件帧，通知前端本次响应失败。 */
    private void writeError(PrintWriter writer, String message) {
        writeRaw(writer, "event:error\ndata:" + message + "\n\n");
    }

    /**
     * 按文档 ID 批量删除知识库片段。
     *
     * <p>请求体为 ID 数组（DELETE 带 body），ID 来自入库接口的返回值或检索结果的 {@code id} 字段。
     *
     * @param ids 待删除的文档 ID
     * @return 无业务数据的统一成功响应
     */
    @DeleteMapping("/documents")
    public Result<Void> delete(@RequestBody List<String> ids) {
        ragChatService.delete(ids);
        return Result.success();
    }
}
