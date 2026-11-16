import request from './request';
import type { ApiResult, PageResult } from '@/types/api';
import type {
  RagChatParams,
  RagChatResult,
  RagDocument,
  RagDocumentQueryParams,
  RagIngestItem,
  RagSource,
} from '@/types/rag';

/**
 * 知识库相关请求的独立超时时间。
 *
 * axios 实例默认 30s，但「批量入库」要逐条向量化、非流式问答要等模型整体生成，
 * 都容易超过默认值，因此统一放宽。
 */
const RAG_TIMEOUT = 120000;

/**
 * 分页查询知识库片段。
 *
 * 注意：Spring AI 的 VectorStore 接口不支持遍历，该接口由后端走 Milvus 原生查询实现。
 */
export function getRagDocumentPage(params: RagDocumentQueryParams) {
  return request.get<ApiResult<PageResult<RagDocument>>>('/ai/rag/documents', {
    params,
    timeout: RAG_TIMEOUT,
  });
}

/** 批量写入知识库片段，返回向量库生成的文档 ID */
export function ingestRagDocuments(documents: RagIngestItem[]) {
  return request.post<ApiResult<string[]>>('/ai/rag/documents', documents, {
    timeout: RAG_TIMEOUT,
  });
}

/**
 * 按 ID 批量删除片段。
 *
 * DELETE 带请求体时必须放在 config.data 里——放到第二个参数会被当成查询参数序列化。
 */
export function deleteRagDocuments(ids: string[]) {
  return request.delete<ApiResult<void>>('/ai/rag/documents', {
    data: ids,
    timeout: RAG_TIMEOUT,
  });
}

/** 纯向量检索：只查向量库，不调用大模型，用于调试召回效果 */
export function searchRagDocuments(query: string, topK?: number, threshold?: number) {
  return request.get<ApiResult<RagSource[]>>('/ai/rag/documents/search', {
    params: { query, topK, threshold },
    timeout: RAG_TIMEOUT,
  });
}

/** 上传 txt / md 文件入库，服务端按段落自动切分 */
export function uploadRagFile(file: File, source?: string, chunkSize?: number) {
  const form = new FormData();
  form.append('file', file);
  if (source) {
    form.append('source', source);
  }
  if (chunkSize) {
    form.append('chunkSize', String(chunkSize));
  }
  return request.post<ApiResult<string[]>>('/ai/rag/documents/upload', form, {
    timeout: RAG_TIMEOUT,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/** 非流式 RAG 问答：一次性拿到答案与全部引用来源 */
export function ragChat(params: RagChatParams) {
  return request.post<ApiResult<RagChatResult>>('/ai/rag/chat', params, {
    timeout: RAG_TIMEOUT,
  });
}

/** 流式 RAG 问答的回调集合 */
export interface RagStreamHandlers {
  /** 检索完成后回调一次（可能为空数组），用于在正文到达前先渲染引用来源 */
  onSources: (sources: RagSource[]) => void;
  /** 正文增量 */
  onChunk: (text: string) => void;
}

/**
 * 流式 RAG 问答（SSE）。
 *
 * 后端帧格式用 `event:` 区分类型，因此不能复用 /ai/chat/stream 的解析器
 * （那个解析器会把所有 data 都当正文，来源帧会被渲染成一坨 JSON）：
 *
 *   event:sources / data:[...]   → 引用来源，先于所有正文帧
 *   data:"正文分片"               → 正文增量，与普通对话格式一致
 *   event:done                    → 正常结束
 *   event:error                   → 失败
 */
export async function ragChatStream(
  params: RagChatParams,
  handlers: RagStreamHandlers,
  signal?: AbortSignal
): Promise<void> {
  const token = localStorage.getItem('admin_token');
  const response = await fetch('/api/ai/rag/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(params),
    signal,
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }

  const reader = response.body!.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });

    // SSE 以空行分隔消息帧
    const frames = buffer.split('\n\n');
    buffer = frames.pop() || '';

    for (const frame of frames) {
      dispatchFrame(frame, handlers);
    }
  }
}

/** 解析单个 SSE 帧并按事件类型分发 */
function dispatchFrame(frame: string, handlers: RagStreamHandlers) {
  let event = '';
  const dataLines: string[] = [];

  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim());
    }
  }

  if (event === 'error') {
    throw new Error(dataLines.join('\n') || 'AI 服务响应失败');
  }
  // done 帧不含业务数据，仅表示流正常结束
  if (event === 'done' || dataLines.length === 0) {
    return;
  }

  const raw = dataLines.join('\n');
  try {
    if (event === 'sources') {
      handlers.onSources(JSON.parse(raw) as RagSource[]);
    } else {
      handlers.onChunk(JSON.parse(raw) as string);
    }
  } catch {
    // 载荷不是 JSON（理论上不会出现）时按正文原文处理，避免整条流中断
    if (event !== 'sources') {
      handlers.onChunk(raw);
    }
  }
}
