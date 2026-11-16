package com.example.springai.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 纯文本切分器。
 *
 * <p>刻意做成无状态工具类：切分逻辑与 Spring 无关，便于脱离上下文做单元测试，
 * 以后若换成 token 级切分也不影响调用方。
 */
public final class RagTextSplitter {

    /** 默认单片最大字符数 */
    public static final int DEFAULT_CHUNK_SIZE = 800;

    /** 单片长度的允许区间，防止调用方传入极端值（过小会产生海量碎片，过大则失去检索精度） */
    private static final int MIN_CHUNK_SIZE = 200;
    private static final int MAX_CHUNK_SIZE = 2000;

    private RagTextSplitter() {
    }

    /**
     * 把整篇文本切成入库片段。
     *
     * <p>策略：先按空行拆段，再把相邻段落贪心合并到不超过 {@code chunkSize}；
     * 单段自身超长时按字符窗口硬切。这样既能保住段落的语义边界，
     * 又不会让某个超长段落变成一条塞不进模型上下文的巨型片段。
     *
     * @param text      原始文本
     * @param chunkSize 单片最大字符数，为空或非法时回落默认值
     * @return 切分后的非空片段（保留顺序）；输入为空时返回空列表
     */
    public static List<String> split(String text, Integer chunkSize) {
        int limit = normalizeChunkSize(chunkSize);
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        StringBuilder current = new StringBuilder();
        // 连续换行（允许中间夹空白）视为段落分隔
        for (String paragraph : text.split("\\R\\s*\\R")) {
            String trimmed = paragraph.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            for (String part : hardSplit(trimmed, limit)) {
                // 放不下就先把当前片段收尾，再从新片段开始累积
                if (current.length() > 0 && current.length() + part.length() + 1 > limit) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                if (current.length() > 0) {
                    current.append('\n');
                }
                current.append(part);
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    /** 把超长段落按固定窗口硬切，保证单块长度不超过 {@code limit}。 */
    private static List<String> hardSplit(String paragraph, int limit) {
        if (paragraph.length() <= limit) {
            return List.of(paragraph);
        }
        List<String> parts = new ArrayList<>();
        for (int start = 0; start < paragraph.length(); start += limit) {
            parts.add(paragraph.substring(start, Math.min(start + limit, paragraph.length())));
        }
        return parts;
    }

    /** 把请求传入的切片长度夹逼到允许区间。 */
    private static int normalizeChunkSize(Integer chunkSize) {
        if (chunkSize == null) {
            return DEFAULT_CHUNK_SIZE;
        }
        return Math.min(Math.max(chunkSize, MIN_CHUNK_SIZE), MAX_CHUNK_SIZE);
    }
}
