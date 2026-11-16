package com.example.springai.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.tools.AutoMemoryTools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UserMemoryToolsProvider} 的隔离性与安全性测试。
 *
 * <p>不加载 Spring 上下文、不调用大模型，只验证「按用户隔离目录」「实例缓存」
 * 「非法用户标识回退且不越界」这三项确定性行为，作为长期记忆改造的基础保障。
 */
class UserMemoryToolsProviderTest {

    /** 每个测试独立的临时根目录，避免污染项目内的 data/memory。 */
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("不同用户拥有各自独立的记忆目录与 MEMORY.md 索引")
    void isolatedByUser() {
        UserMemoryToolsProvider provider = new UserMemoryToolsProvider(tempDir.toString());

        AutoMemoryTools alice = provider.get("alice");
        AutoMemoryTools bob = provider.get("bob");

        assertNotNull(alice);
        assertNotNull(bob);
        assertEquals(tempDir.resolve("alice"), alice.getMemoriesDir());
        assertEquals(tempDir.resolve("bob"), bob.getMemoriesDir());
        assertEquals(tempDir.resolve("alice").resolve("MEMORY.md"), provider.memoryIndexPath("alice"));
        assertTrue(Files.exists(provider.memoryIndexPath("alice")));
        assertTrue(Files.exists(provider.memoryIndexPath("bob")));
    }

    @Test
    @DisplayName("同一用户复用同一个记忆工具实例")
    void cachesInstancePerUser() {
        UserMemoryToolsProvider provider = new UserMemoryToolsProvider(tempDir.toString());

        assertSame(provider.get("alice"), provider.get("alice"));
    }

    @Test
    @DisplayName("非法用户标识回退为哈希目录名，且不会创建到根目录之外")
    void unsafeUserNameFallsBackInsideRoot() throws IOException {
        UserMemoryToolsProvider provider = new UserMemoryToolsProvider(tempDir.toString());

        // 路径穿越与中文用户名都不满足白名单，应回退为 u-{sha256 前 16 位}
        assertNotNull(provider.get("../../etc"));
        assertNotNull(provider.get("张三"));

        assertTrue(provider.memoryIndexPath("../../etc").getParent().startsWith(tempDir));
        assertTrue(provider.memoryIndexPath("张三").getParent().startsWith(tempDir));

        try (Stream<Path> children = Files.list(tempDir)) {
            List<Path> created = children.toList();
            assertEquals(2, created.size());
            assertTrue(created.stream().allMatch(path -> path.getFileName().toString().startsWith("u-")));
        }
    }

    @Test
    @DisplayName("用户标识为空时回退为 anonymous 的哈希目录")
    void blankUserNameFallsBack() {
        UserMemoryToolsProvider provider = new UserMemoryToolsProvider(tempDir.toString());

        assertNotNull(provider.get(null));
        assertNotNull(provider.get("   "));
        assertTrue(provider.memoryIndexPath(null).getParent().startsWith(tempDir));
    }
}
