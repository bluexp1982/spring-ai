// 知识库片段（列表项）
export interface RagDocument {
  id: string;
  content: string;
  metadata?: Record<string, unknown>;
}

// 检索命中的片段：比列表项多一个相似度得分
export interface RagSource extends RagDocument {
  score?: number;
}

// 知识库分页查询参数
export interface RagDocumentQueryParams {
  page?: number;
  size?: number;
  keyword?: string;
}

// 待入库的片段
export interface RagIngestItem {
  content: string;
  metadata?: Record<string, unknown>;
}

// RAG 问答参数（召回参数省略则用服务端默认值）
export interface RagChatParams {
  message: string;
  topK?: number;
  similarityThreshold?: number;
}

// 非流式 RAG 问答结果
export interface RagChatResult {
  answer: string;
  sources: RagSource[];
}
