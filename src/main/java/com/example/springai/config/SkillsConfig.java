package com.example.springai.config;

import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI Agent Skills 配置。
 *
 * <p>SkillsTool 是 Spring AI Agent Utils 提供的技能系统核心组件：
 * 启动时扫描指定目录下的 SKILL.md 文件，将其技能名称与描述注册为
 * 可供大模型调用的工具。当用户请求与技能描述语义匹配时，大模型自动
 * 调用对应技能，获取 SKILL.md 中的完整指令作为上下文。
 *
 * <p>技能目录约定：
 * <ul>
 *   <li>每个技能一个子目录，目录内必须包含 {@code SKILL.md} 文件</li>
 *   <li>可选的辅助文件：{@code REFERENCE.md}、{@code EXAMPLES.md}</li>
 *   <li>可选的辅助脚本：{@code scripts/} 目录</li>
 * </ul>
 *
 * @see <a href="https://spring-ai-community.github.io/spring-ai-agent-utils/latest/">Spring AI Agent Utils Docs</a>
 */
@Configuration
public class SkillsConfig {

    /**
     * 创建 SkillsTool 对应的 {@link ToolCallback} Bean，扫描
     * {@code src/main/resources/.agents/skills/} 目录下的所有技能定义。
     *
     * <p>注意：此处使用文件系统路径而非 classpath 资源，因为
     * {@code classpath:/.agents/skills} 在 Spring Boot Fat JAR 打包后
     * 无法正确识别带点号前缀的目录；项目使用本地目录可确保兼容。
     *
     * <p>{@link SkillsTool#builder()}{@code .build()} 返回 {@link ToolCallback}，
     * 可直接通过 {@code ChatClient.Builder.defaultToolCallbacks(...)} 注册。
     *
     * @return 已注册所有技能的 ToolCallback 实例
     */
    @Bean
    public ToolCallback skillsToolCallback() {
        return (ToolCallback) SkillsTool.builder()
                .addSkillsDirectory("src/main/resources/.agents/skills")
                .build();
    }
}