package com.example.springai.ai;

import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.agent.tools.AutoMemoryTools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 按登录用户隔离的长期记忆工具提供者。
 *
 * <p>长期记忆选用 spring-ai-agent-utils 的 {@link AutoMemoryTools}——一组文件型记忆工具
 * （MemoryView / MemoryCreate / MemoryStrReplace / MemoryInsert / MemoryDelete / MemoryRename），
 * 所有读写都被沙箱限制在构造时传入的 {@code memoriesDir} 内。
 *
 * <p>由于 {@code memoriesDir} 在 {@link AutoMemoryTools} 构造期即固定、实例无法复用于多个目录，
 * 本类承担「用户标识 → 安全目录名 → 记忆工具实例 / MEMORY.md 路径」的映射职责：
 * <ul>
 *   <li>目录布局：{@code {记忆根目录}/{安全用户标识}/}，每个用户拥有独立的
 *       {@code MEMORY.md} 索引与记忆正文文件，用户之间互不可见；</li>
 *   <li>实例缓存：每用户一个 {@link AutoMemoryTools} 实例（仅持有目录路径，占用极小），
 *       通过 {@link ConcurrentHashMap} 缓存，避免每轮对话重复创建目录；</li>
 *   <li>异常降级：工具构建失败时返回 {@code null} 并记录日志，由调用方跳过本轮记忆工具注册，
 *       不影响对话主流程。</li>
 * </ul>
 *
 * <p>路径安全共三道防线：① 用户标识白名单（不含点号与斜杠，从根上排除 {@code ../} 穿越）；
 * ② 解析后二次校验结果仍位于记忆根目录内；③ {@link AutoMemoryTools#builder()} 内部的
 * {@code resolveSafePath} 沙箱校验。
 */
@Slf4j
public class UserMemoryToolsProvider {

    /**
     * 安全目录名白名单：仅字母、数字、下划线、连字符，长度 1~32。
     * 刻意不包含点号与路径分隔符，使 {@code ..}/{@code /} 之类的穿越串无法通过校验。
     */
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    /** 记忆索引文件名（每个用户目录内各一份）。 */
    private static final String MEMORY_INDEX_FILE = "MEMORY.md";

    /** 索引骨架：首次访问某用户目录时写入，避免模型首次 MemoryView 时找不到索引文件。 */
    private static final String INDEX_SKELETON = """
            # Memory Index

            <!-- 长期记忆索引：每条记忆占一行，格式为 -->
            <!-- - [标题](文件名.md) — 一句话说明（不超过 150 字符） -->
            """;

    /** 记忆根目录（绝对路径，已归一化）。 */
    private final Path rootDir;

    /** 用户安全目录名 → 该用户的记忆工具实例。 */
    private final Map<String, AutoMemoryTools> toolsCache = new ConcurrentHashMap<>();

    /**
     * @param memoriesRootDir 记忆根目录，可为相对路径（基于进程工作目录解析）或绝对路径；
     *                        为空时回退到默认目录 {@code data/memory}
     */
    public UserMemoryToolsProvider(String memoriesRootDir) {
        String dir = (memoriesRootDir == null || memoriesRootDir.isBlank())
                ? "data/memory"
                : memoriesRootDir;
        // 归一化为绝对路径：记忆目录相对「进程工作目录」，绝对化可避免运行期路径漂移
        this.rootDir = Paths.get(dir).toAbsolutePath().normalize();
        log.info("长期记忆根目录: {}", this.rootDir);
    }

    /**
     * 获取指定用户的记忆工具实例。
     *
     * <p>首次调用会创建该用户的目录与 {@code MEMORY.md} 索引骨架；失败时返回 {@code null}，
     * 调用方应降级为「本轮不注册记忆工具」，而不是让对话失败。
     *
     * @param conversationId 会话标识（当前即登录用户名）
     * @return 该用户专属的记忆工具；初始化失败时返回 {@code null}
     */
    public AutoMemoryTools get(String conversationId) {
        String dirName = safeDirName(conversationId);

        AutoMemoryTools cached = toolsCache.get(dirName);
        if (cached != null) {
            return cached;
        }

        try {
            Path userDir = userDir(dirName);
            AutoMemoryTools tools = AutoMemoryTools.builder().memoriesDir(userDir).build();
            ensureIndexSkeleton(userDir);
            toolsCache.put(dirName, tools);
            log.info("初始化用户长期记忆目录: {}（conversationId={}）", userDir, conversationId);
            return tools;
        } catch (Exception e) {
            log.error("初始化用户长期记忆失败, conversationId={}, dirName={}", conversationId, dirName, e);
            return null;
        }
    }

    /**
     * 获取该用户 {@code MEMORY.md} 索引文件的绝对路径，供每轮对话主动注入索引内容使用。
     *
     * @param conversationId 会话标识（当前即登录用户名）
     * @return 索引文件路径（文件可能尚未创建）
     */
    public Path memoryIndexPath(String conversationId) {
        return userDir(safeDirName(conversationId)).resolve(MEMORY_INDEX_FILE);
    }

    /** 用户目录 = 根目录 / 安全目录名，并二次校验未越出根目录。 */
    private Path userDir(String dirName) {
        Path dir = rootDir.resolve(dirName).normalize();
        if (!dir.startsWith(rootDir)) {
            throw new IllegalArgumentException("非法的记忆目录名: " + dirName);
        }
        return dir;
    }

    /**
     * 用户标识 → 安全目录名：符合白名单则原样使用，否则回退为
     * {@code u-} 加用户标识 SHA-256 摘要的前 16 位十六进制（不可逆，且必定安全）。
     */
    private String safeDirName(String conversationId) {
        if (conversationId != null && SAFE_NAME.matcher(conversationId).matches()) {
            return conversationId;
        }
        return "u-" + sha256Hex(conversationId);
    }

    /** 计算用户标识的 SHA-256 摘要（取前 8 字节共 16 位十六进制字符）。 */
    private static String sha256Hex(String raw) {
        String source = (raw == null || raw.isBlank()) ? "anonymous" : raw;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 保证 SHA-256 一定可用，此分支实际不可达
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    /** 索引骨架懒创建：已存在则跳过；失败仅告警，不影响记忆工具本身可用。 */
    private void ensureIndexSkeleton(Path userDir) {
        Path indexPath = userDir.resolve(MEMORY_INDEX_FILE);
        try {
            if (Files.exists(indexPath)) {
                return;
            }
            Files.createDirectories(userDir);
            Files.writeString(indexPath, INDEX_SKELETON, StandardCharsets.UTF_8);
            log.info("创建长期记忆索引骨架: {}", indexPath);
        } catch (Exception e) {
            log.warn("创建长期记忆索引骨架失败: {}", indexPath, e);
        }
    }
}
