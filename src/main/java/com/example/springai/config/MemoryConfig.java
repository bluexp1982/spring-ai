package com.example.springai.config;

import com.example.springai.ai.UserMemoryToolsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 长期记忆配置。
 *
 * <p>长期记忆与短期记忆的分工：
 * <ul>
 *   <li><b>短期记忆</b>：Spring AI JDBC Chat Memory（PostgreSQL {@code SPRING_AI_CHAT_MEMORY} 表），
 *       保存当前会话的上下文，按 conversationId 隔离，「清空对话」即删除；</li>
 *   <li><b>长期记忆</b>：spring-ai-agent-utils 的文件型记忆工具
 *       （{@link UserMemoryToolsProvider} 管理的 {@code AutoMemoryTools}），
 *       落盘为 markdown 文件，跨会话保留，按登录用户隔离，「清空对话」不受影响。</li>
 * </ul>
 *
 * <p>记忆根目录通过 {@code app.ai.memory.dir} 配置，默认 {@code data/memory}（相对进程工作目录）。
 * 刻意放在源码树之外：记忆是运行期用户数据，放进 {@code src/main/resources} 会被打包进 jar。
 * 与 {@link SkillsConfig} 一样使用文件系统路径，因为 {@code classpath:/.agents/...}
 * 在 Spring Boot Fat JAR 打包后无法识别带点号前缀的目录。
 *
 * @see UserMemoryToolsProvider
 */
@Configuration
public class MemoryConfig {

    /** 记忆根目录：相对进程工作目录；生产部署建议通过配置指向可写目录。 */
    @Value("${app.ai.memory.dir:data/memory}")
    private String memoriesDir;

    /**
     * 按登录用户隔离的长期记忆工具提供者。
     *
     * <p>注意：{@code AutoMemoryTools} 的记忆目录在构造期固定，无法作为单例 Bean 供所有用户共用，
     * 因此这里只暴露「按用户取实例」的提供者，具体的记忆工具由
     * {@code AiChatService} 在每轮对话时按用户请求级注册。
     *
     * @return 长期记忆工具提供者
     */
    @Bean
    public UserMemoryToolsProvider userMemoryToolsProvider() {
        return new UserMemoryToolsProvider(memoriesDir);
    }
}
