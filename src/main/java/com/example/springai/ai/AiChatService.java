package com.example.springai.ai;

import com.example.springai.biz.tools.SaasSubscriptionOrderTools;
import com.example.springai.biz.tools.SaasSubscriptionTools;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.agent.tools.AutoMemoryTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AI 对话核心服务。
 *
 * 负责组装 Prompt（系统提示词 + 历史记忆 + 本轮提问）、通过
 * {@link ChatClient} 调用底层大模型完成对话，并将问答结果写入按会话隔离的
 * {@link ChatMemory}，使 AI 具备多轮上下文记忆能力；
 * 同时提供流式（SSE）与非流式两种对话方式。
 *
 * <p>本服务持有已注册工具对象的 {@link ChatClient}：模型在回答过程中可以主动
 * 发起工具调用，由 Spring AI 反射执行对应 {@code @Tool} 方法并把结果回灌给模型。
 * 已注册的工具包括：业务查询工具（{@code @Tool} 注解）、MCP 外部工具、
 * 以及 Agent Skills 技能工具（{@link org.springframework.ai.tool.ToolCallback}）。
 *
 * <p>记忆分为两层，互不干扰：
 * <ul>
 *   <li><b>短期记忆</b>：{@link ChatMemory}（PostgreSQL），保存当前会话上下文，
 *       「清空对话」即删除；</li>
 *   <li><b>长期记忆</b>：{@link AutoMemoryTools}（文件型，按登录用户隔离），
 *       每轮对话以 <em>请求级</em> 工具注册，并把该用户的 {@code MEMORY.md} 索引
 *       主动注入系统提示词，保证「清空对话」后依然记得用户背景。</li>
 * </ul>
 */
@Service
@Slf4j
public class AiChatService {

    private static final String SYSTEM_PROMPT = """
            你是一位私人 AI 助理，专门为使用者提供贴心、专业且可靠的协助。
            你的职责包括：准确理解使用者的需求，用清晰易懂的中文回答问题，
            在不确定时坦诚说明，并主动给出可操作的后续建议。
            回答时可使用 Markdown 组织内容：用加粗突出关键信息，用列表分点罗列，
            用表格对比结构化数据，让结论更清晰易读；不要输出 HTML。
            工具返回的时间均已按北京时间（UTC+8）格式化，请直接展示，不要再做任何时区换算。

            你拥有一份跨会话的长期记忆，它只属于当前使用者，与其他使用者完全隔离：
            - 回答前先参考系统提示词末尾注入的长期记忆索引；索引中存在相关条目时，
              先用 MemoryView 读取对应记忆文件，再结合内容回答。
            - 当了解到值得长期记住的信息（使用者的身份、角色与偏好，明确的纠正或确认，
              项目背景与决策，外部系统指引）时，按两步保存：① 用 MemoryCreate 写入带
              YAML frontmatter 的正文文件（name / description / type: user|feedback|project|reference）；
              ② 用 MemoryInsert 或 MemoryStrReplace 在 MEMORY.md 中新增或更新索引行，
              格式为「- [标题](文件名.md) — 一句话说明」。
            - 不要保存代码结构、git 历史等能从代码库本身推导出来的内容。
            - 长期记忆与本次会话无关：使用者清空对话时，不要删除或修改任何长期记忆文件。
            """;

    /** Spring AI 自动装配的对话记忆（内存存储，按 conversationId 隔离）。 */
    private final ChatMemory chatMemory;

    /**
     * Spring AI 对话客户端：在 {@link ChatModel} 之上封装了一层请求 API。
     *
     * 关键点是 {@code defaultTools(...)}——它把工具对象的 {@code @Tool} 方法
     * 声明给模型；只加 {@code @Tool} 注解而不注册，模型是感知不到该工具的。
     */
    private final ChatClient chatClient;

    /**
     * 按登录用户隔离的长期记忆工具提供者。
     *
     * 长期记忆工具无法像业务工具那样全局注册——{@link AutoMemoryTools} 的记忆目录在
     * 构造期固定，每个用户需要各自一份实例，因此改为在每轮对话时按 conversationId
     * 以请求级工具追加（见 {@link #registerMemoryTools}）。
     */
    private final UserMemoryToolsProvider userMemoryToolsProvider;

    public AiChatService(ChatModel chatModel,
                         ChatMemory chatMemory,
                         SaasSubscriptionOrderTools orderTools,
                         SaasSubscriptionTools subscriptionTools,
                         UserMemoryToolsProvider userMemoryToolsProvider,
                         ObjectProvider<ToolCallbackProvider> toolCallbackProviders,
                         ToolCallback skillsToolCallback) {
        this.chatMemory = chatMemory;
        this.userMemoryToolsProvider = userMemoryToolsProvider;

        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultTools(orderTools, subscriptionTools)
                .defaultToolCallbacks(skillsToolCallback);

        // MCP 工具（如高德地图）由 spring-ai-starter-mcp-client 以 ToolCallbackProvider
        // Bean 的形式暴露，但 Spring AI 会主动把 MCP 提供者排除在全局 ToolCallbackResolver
        // 之外，因此这里必须显式注册到本服务自建的 ChatClient 上，模型才能感知并调用。
        toolCallbackProviders.forEach(builder::defaultToolCallbacks);

        this.chatClient = builder.build();
    }

    /**
     * 非流式对话：一次性获取 AI 完整回复，并在返回成功后写入对话记忆。
     *
     * @param conversationId 会话标识（当前以登录用户名隔离）
     * @param message        本轮用户提问
     * @return AI 回复的完整文本
     */
    public String chat(String conversationId, String message) {
        UserMessage userMessage = new UserMessage(message);
        Prompt prompt = buildPrompt(conversationId, userMessage);
        String answer = registerMemoryTools(chatClient.prompt(prompt), conversationId).call().content();
        saveMemory(conversationId, userMessage, answer);
        return answer;
    }

    /**
     * 流式对话：以 {@link Flux} 逐段推送 AI 回复增量。
     *
     * 回复内容边推送边累积，仅在流正常结束时写入记忆，
     * 避免客户端中途取消或出错时写入残缺回复。
     *
     * @param conversationId 会话标识（当前以登录用户名隔离）
     * @param message        本轮用户提问
     * @return 逐段输出的 AI 回复文本流
     */
    public Flux<String> chatStream(String conversationId, String message) {
        UserMessage userMessage = new UserMessage(message);
        Prompt prompt = buildPrompt(conversationId, userMessage);
        // 累积完整回复，流正常结束（doOnComplete）时再写入记忆；
        // 客户端中途取消或出错不会写入残缺回复。
        StringBuilder reply = new StringBuilder();
        // 流式响应中部分 chunk（如仅含 usage 统计的收尾块）文本为 null，
        // Reactor 的 map 不允许返回 null，故用 mapNotNull 跳过。
        return registerMemoryTools(chatClient.prompt(prompt), conversationId).stream().content()
                .mapNotNull(text -> (text == null || text.isEmpty()) ? null : text)
                .doOnNext(reply::append)
                .doOnComplete(() -> saveMemory(conversationId, userMessage, reply.toString()));
    }

    /**
     * 为当前用户注册「请求级」长期记忆工具。
     *
     * <p>与 {@code defaultTools(...)} 走的是同一条工具聚合通路（Spring AI 会把
     * {@code @Tool} POJO 转换为 {@link ToolCallback} 并交给工具调用 Advisor 执行），
     * 区别仅在于作用范围是本轮请求——这样才能让每个用户拿到各自记忆目录下的工具实例。
     *
     * <p>工具初始化失败（返回 {@code null}）时静默跳过，仅记录日志，
     * 保证本轮对话不因长期记忆异常而失败。
     *
     * @param requestSpec    待发起的请求
     * @param conversationId 会话标识（当前即登录用户名）
     * @return 已按需注册记忆工具的请求
     */
    private ChatClient.ChatClientRequestSpec registerMemoryTools(ChatClient.ChatClientRequestSpec requestSpec,
                                                                String conversationId) {
        AutoMemoryTools memoryTools = userMemoryToolsProvider.get(conversationId);
        return memoryTools != null ? requestSpec.tools(memoryTools) : requestSpec;
    }

    /**
     * 读取指定会话的历史消息（仅用户/助手），用于前端页面载入时回显对话气泡。
     */
    public List<AiChatMessage> getHistory(String conversationId) {
        return loadHistory(conversationId).stream()
                .map(message -> switch (message.getMessageType()) {
                    case USER -> new AiChatMessage("user", message.getText());
                    case ASSISTANT -> new AiChatMessage("assistant", message.getText());
                    default -> null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 清空指定会话（登录用户）的「短期」对话记忆。
     *
     * <p><b>边界约定</b>：本方法只删除 PostgreSQL 中的会话上下文
     * （{@link ChatMemory#clear(String)}），对应前端「清空对话」按钮。
     * 长期记忆是磁盘上的 markdown 文件，跨会话保留、按用户隔离，
     * <b>严禁</b>在本方法（或 {@code POST /api/ai/chat/clear} 链路）中加入任何
     * 删除记忆文件、调用 {@code MemoryDelete} 的逻辑，否则会误删用户长期偏好。
     */
    public void clearMemory(String conversationId) {
        try {
            chatMemory.clear(conversationId);
        } catch (Exception e) {
            log.error("清空 AI 对话记忆失败, conversationId={}", conversationId, e);
        }
    }

    /**
     * 组装 Prompt：SystemPrompt 常量（含长期记忆索引）+ 历史消息 + 本轮用户消息。
     * SystemPrompt 不写入记忆，每次调用单独前置，避免污染窗口与重复。
     */
    private Prompt buildPrompt(String conversationId, UserMessage userMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT + loadMemoryIndex(conversationId)));
        messages.addAll(loadHistory(conversationId));
        messages.add(userMessage);
        return new Prompt(messages);
    }

    /**
     * 读取该用户长期记忆索引（{@code MEMORY.md}）并包装成系统提示词片段。
     *
     * <p>模型虽然可以通过 MemoryView 自行查看索引，但并不总有这个自觉（尤其是能力较弱的模型）。
     * 由服务端每轮主动注入索引，可以稳定保证「清空对话后依然记得用户背景」，而记忆正文
     * 仍由模型按需用 MemoryView 读取，避免每轮都塞入全部记忆内容。
     *
     * @param conversationId 会话标识（当前即登录用户名）
     * @return 可直接拼接的提示词片段；索引不存在、为空或读取失败时返回空串
     */
    private String loadMemoryIndex(String conversationId) {
        try {
            Path indexPath = userMemoryToolsProvider.memoryIndexPath(conversationId);
            if (!Files.exists(indexPath)) {
                return "";
            }
            String index = Files.readString(indexPath, StandardCharsets.UTF_8).trim();
            if (index.isEmpty()) {
                return "";
            }
            // 注意：仅记录路径等元信息，不要打印记忆正文（可能含用户隐私）
            log.debug("注入长期记忆索引, conversationId={}, path={}", conversationId, indexPath);
            return "\n\n以下是当前使用者已保存的长期记忆索引（需要详情时用 MemoryView 读取对应文件）：\n" + index;
        } catch (Exception e) {
            log.error("读取长期记忆索引失败, conversationId={}", conversationId, e);
            return "";
        }
    }

    /** 读取历史消息，异常时降级为无历史，保证本轮对话不因记忆问题失败。 */
    private List<Message> loadHistory(String conversationId) {
        try {
            return chatMemory.get(conversationId);
        } catch (Exception e) {
            log.error("读取 AI 对话记忆失败, conversationId={}", conversationId, e);
            return List.of();
        }
    }

    /** 写入本轮「用户消息 + 助手回复」，异常仅记录日志，不阻断对话返回。 */
    private void saveMemory(String conversationId, UserMessage userMessage, String answer) {
        if (answer == null || answer.isEmpty()) {
            return;
        }
        try {
            chatMemory.add(conversationId, List.of(userMessage, new AssistantMessage(answer)));
        } catch (Exception e) {
            log.error("写入 AI 对话记忆失败, conversationId={}", conversationId, e);
        }
    }
}