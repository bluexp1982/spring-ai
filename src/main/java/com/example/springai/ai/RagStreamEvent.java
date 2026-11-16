package com.example.springai.ai;

import java.util.List;

/**
 * RAG 流式问答的事件单元。
 *
 * <p>之所以在服务层就区分事件类型、而不是直接向下游抛纯文本，是因为
 * 「引用来源」与「正文增量」需要编码成不同的 SSE 帧，控制器据此分流。
 */
public record RagStreamEvent(Type type, List<RagSource> sources, String content) {

    /** 事件类型 */
    public enum Type {
        /** 检索结果帧：整个回答只出现一次，且一定先于所有正文帧 */
        SOURCES,
        /** 正文增量帧 */
        CONTENT
    }

    /**
     * 构造检索结果事件。
     *
     * <p>命中 0 条时也会发一个空列表事件，让前端明确知道「检索已完成且无结果」，
     * 而不是停留在等待状态。
     */
    public static RagStreamEvent sources(List<RagSource> sources) {
        return new RagStreamEvent(Type.SOURCES, sources, null);
    }

    /** 构造正文增量事件 */
    public static RagStreamEvent content(String content) {
        return new RagStreamEvent(Type.CONTENT, null, content);
    }
}
