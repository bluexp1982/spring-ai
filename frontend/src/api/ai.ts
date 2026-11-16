import request from './request';
import type { ApiResult } from '@/types/api';

export interface ChatHistoryMessage {
  role: 'user' | 'assistant';
  content: string;
}

/**
 * 拉取当前登录用户的历史对话（用于页面载入时回显）。
 */
export async function getChatHistory(): Promise<ChatHistoryMessage[]> {
  const response = await request.get<ApiResult<ChatHistoryMessage[]>>('/ai/chat/history');
  return response.data.data ?? [];
}

/**
 * 清空当前登录用户的后端对话记忆。
 * 与后端 POST /api/ai/chat/clear 对应（axios baseURL 已为 /api）。
 */
export async function clearChat(): Promise<void> {
  await request.post('/ai/chat/clear');
}

export async function chatStream(
  message: string,
  onChunk: (text: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const token = localStorage.getItem('admin_token');
  const response = await fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ message }),
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
      if (frame.includes('event:error')) {
        throw new Error('AI 服务响应失败');
      }
      const dataLines: string[] = [];
      for (const line of frame.split('\n')) {
        if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trim());
        }
      }
      if (dataLines.length === 0) continue;

      const raw = dataLines.join('\n');
      try {
        // 后端用 JSON 编码传输（避免换行破坏 SSE 帧），这里解码
        onChunk(JSON.parse(raw));
      } catch {
        onChunk(raw);
      }
    }
  }
}