import { useState, useRef, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { Input, Button, Space, Typography, Segmented, Collapse, Tag } from 'antd';
import {
  MessageOutlined,
  SendOutlined,
  CloseOutlined,
  DeleteOutlined,
  PicRightOutlined,
  PushpinOutlined,
  RobotOutlined,
  UserOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { chatStream, clearChat, getChatHistory } from '@/api/ai';
import { ragChatStream } from '@/api/rag';
import type { RagSource } from '@/types/rag';
import { useAgentWorking } from '@/contexts/AgentWorkingContext';
import MarkdownContent from './MarkdownContent';

const { Text, Paragraph } = Typography;

/**
 * 取片段的来源标识。
 *
 * metadata 是后端自定义的 JSON，不保证存在 source 键，因此这里集中做一次类型收窄。
 */
function sourceLabel(source: RagSource): string {
  const value = source.metadata?.source;
  return typeof value === 'string' ? value : '';
}

/** 侧栏默认宽度（px），与 index.css 中 --ai-chat-panel-width 的回退值保持一致 */
const DEFAULT_PANEL_WIDTH = 360;
/** 侧栏可拖拽的最小宽度（px） */
const MIN_PANEL_WIDTH = 300;
/** 侧栏宽度写入 localStorage 时使用的键名 */
const PANEL_WIDTH_STORAGE_KEY = 'ai_panel_width';

/**
 * 侧栏最大宽度：不超过视口宽度的一半。
 * 窗口过窄时（例如宽度小于 600px）「最小 300px」优先，避免出现 min > max。
 */
const maxPanelWidth = () => Math.max(MIN_PANEL_WIDTH, window.innerWidth / 2);

/** 把侧栏宽度约束在 [MIN_PANEL_WIDTH, 视口宽度的一半] 之间 */
const clampPanelWidth = (width: number) =>
  Math.min(Math.max(width, MIN_PANEL_WIDTH), maxPanelWidth());

/** 读取上次保存的宽度：无记录或数据异常时回退默认值，并做一次越界约束 */
const loadStoredPanelWidth = () => {
  try {
    const saved = Number(localStorage.getItem(PANEL_WIDTH_STORAGE_KEY));
    return Number.isFinite(saved) && saved > 0 ? clampPanelWidth(saved) : DEFAULT_PANEL_WIDTH;
  } catch {
    // localStorage 不可用（隐私模式等），退化为默认宽度
    return DEFAULT_PANEL_WIDTH;
  }
};

/** 持久化侧栏宽度；写入失败不影响功能，静默忽略 */
const persistPanelWidth = (width: number) => {
  try {
    localStorage.setItem(PANEL_WIDTH_STORAGE_KEY, String(width));
  } catch {
    // 忽略写入异常
  }
};

/**
 * 面板显示模式。
 *
 * - float：悬浮模式，Portal 到 body 后浮在页面右侧，不挤占主内容；
 * - dock：嵌入模式，作为 AdminLayout 中 flex 容器的一员，与左侧菜单栏一样融入页面布局。
 */
type PanelDisplayMode = 'float' | 'dock';

/** 显示模式写入 localStorage 时使用的键名 */
const PANEL_MODE_STORAGE_KEY = 'ai_panel_mode';

/** 读取上次保存的显示模式：无记录或数据异常时回退悬浮模式 */
const loadStoredPanelMode = (): PanelDisplayMode => {
  try {
    return localStorage.getItem(PANEL_MODE_STORAGE_KEY) === 'dock' ? 'dock' : 'float';
  } catch {
    // localStorage 不可用（隐私模式等），退化为悬浮模式
    return 'float';
  }
};

/** 持久化显示模式；写入失败不影响功能，静默忽略 */
const persistPanelMode = (mode: PanelDisplayMode) => {
  try {
    localStorage.setItem(PANEL_MODE_STORAGE_KEY, mode);
  } catch {
    // 忽略写入异常
  }
};

interface Message {
  role: 'user' | 'assistant';
  content: string;
  /**
   * 知识库问答模式下本条回复实际依据的片段。
   *
   * 为 undefined 表示普通对话（不展示来源区块）；为空数组表示已检索但无命中。
   */
  sources?: RagSource[];
}

/**
 * 对话模式。
 *
 * - chat：普通对话，走 /api/ai/chat/stream，由后端 ChatMemory 保存多轮上下文；
 * - rag：知识库问答，走 /api/ai/rag/chat/stream，后端无状态、不写记忆。
 */
type ChatMode = 'chat' | 'rag';

interface AiChatPanelProps {
  open: boolean;
  onToggle: () => void;
}

export default function AiChatPanel({ open, onToggle }: AiChatPanelProps) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  /** 把生成状态与停止能力共享给左侧工作区的霞光遮罩 */
  const { setWorking, registerStop } = useAgentWorking();
  const [mode, setMode] = useState<ChatMode>('chat');
  /** 侧栏宽度（px），由左边缘手柄拖拽调节；初值取自上次保存的宽度 */
  const [panelWidth, setPanelWidth] = useState(loadStoredPanelWidth);
  /** 悬浮 / 嵌入显示模式，初值取自上次选择 */
  const [displayMode, setDisplayMode] = useState<PanelDisplayMode>(loadStoredPanelMode);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const abortControllerRef = useRef<AbortController | null>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  /** 是否正在拖拽调宽：用 ref 记录，避免 mousemove 期间触发额外重渲染 */
  const resizingRef = useRef(false);

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, scrollToBottom]);

  useEffect(() => {
    if (open) {
      setTimeout(() => inputRef.current?.focus(), 300);
    }
  }, [open]);

  // 生成结束后把焦点还给输入框（点击发送按钮或输入框被禁用都会导致焦点丢失）
  useEffect(() => {
    if (open && !loading) {
      inputRef.current?.focus();
    }
  }, [open, loading]);

  // 拖拽左侧边框调节侧栏宽度：面板始终贴住视口右边缘，
  // 所以宽度 = 视口宽度 - 鼠标 X 坐标。事件挂在 window 上，
  // 这样鼠标快速划出面板甚至划出窗口时拖拽也不会中断。
  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (!resizingRef.current) return;
      setPanelWidth(clampPanelWidth(window.innerWidth - e.clientX));
    };
    const stopResize = () => {
      if (!resizingRef.current) return;
      resizingRef.current = false;
      document.body.style.removeProperty('cursor');
      document.body.style.removeProperty('user-select');
    };
    // 窗口变窄时把侧栏一起收回来，避免它盖住整个主内容区
    const handleWindowResize = () => {
      setPanelWidth((prev) => clampPanelWidth(prev));
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', stopResize);
    window.addEventListener('resize', handleWindowResize);
    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', stopResize);
      window.removeEventListener('resize', handleWindowResize);
      stopResize();
    };
  }, []);

  // 记住拖动后的宽度，刷新页面依然生效。
  // 加 300ms 防抖：拖拽时 mousemove 高频触发，没必要每次同步写 localStorage；
  // 防抖同时保证了「松手后最后一次宽度」一定会被写入。
  useEffect(() => {
    const timer = window.setTimeout(() => persistPanelWidth(panelWidth), 300);
    return () => window.clearTimeout(timer);
  }, [panelWidth]);

  // 记住显示模式，刷新页面后仍保持上次选择的悬浮 / 嵌入形态
  useEffect(() => {
    persistPanelMode(displayMode);
  }, [displayMode]);

  /** 在悬浮模式与嵌入模式之间切换 */
  const toggleDisplayMode = () => {
    setDisplayMode((prev) => (prev === 'float' ? 'dock' : 'float'));
  };

  /** 按下左侧手柄，开始拖拽；同时禁用文本选中，防止拖拽时选中面板内文字 */
  const handleResizeStart = (e: React.MouseEvent<HTMLDivElement>) => {
    e.preventDefault();
    resizingRef.current = true;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
  };

  // 页面载入时从后端拉取历史对话并回显气泡（刷新后不再丢失）
  useEffect(() => {
    let cancelled = false;
    getChatHistory()
      .then((history) => {
        // 仅当本地尚无消息时填充，避免覆盖用户刚发出的消息
        if (!cancelled) {
          setMessages((prev) => (prev.length === 0 ? history : prev));
        }
      })
      .catch((err) => {
        console.error('加载历史对话失败:', err);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  /** 向最后一条助手气泡追加正文增量 */
  const appendToLastAssistant = (chunk: string) => {
    setMessages((prev) => {
      const updated = [...prev];
      const last = updated[updated.length - 1];
      if (last.role === 'assistant') {
        updated[updated.length - 1] = {
          ...last,
          content: last.content + chunk,
        };
      }
      return updated;
    });
  };

  /**
   * 把检索到的片段挂到最后一条助手气泡上。
   *
   * 知识库问答会先推来源帧再推正文，因此这里通常先于任何正文增量执行，
   * 用户能立刻看到「引用了哪些资料」，而不是干等模型输出。
   */
  const attachSourcesToLastAssistant = (sources: RagSource[]) => {
    setMessages((prev) => {
      const updated = [...prev];
      const last = updated[updated.length - 1];
      if (last.role === 'assistant') {
        updated[updated.length - 1] = { ...last, sources };
      }
      return updated;
    });
  };

  const handleSend = async () => {
    const trimmed = input.trim();
    if (!trimmed || loading) return;

    const userMessage: Message = { role: 'user', content: trimmed };
    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setLoading(true);

    const assistantMessage: Message = { role: 'assistant', content: '' };
    setMessages((prev) => [...prev, assistantMessage]);

    const abortController = new AbortController();
    abortControllerRef.current = abortController;

    try {
      if (mode === 'rag') {
        await ragChatStream(
          { message: trimmed },
          { onSources: attachSourcesToLastAssistant, onChunk: appendToLastAssistant },
          abortController.signal
        );
      } else {
        await chatStream(trimmed, appendToLastAssistant, abortController.signal);
      }
    } catch (err: unknown) {
      if (err instanceof Error && err.name === 'AbortError') {
        return;
      }
      setMessages((prev) => {
        const updated = [...prev];
        const last = updated[updated.length - 1];
        if (last.role === 'assistant') {
          updated[updated.length - 1] = {
            ...last,
            content: last.content + '\n\n[响应出错，请重试]',
          };
        }
        return updated;
      });
    } finally {
      setLoading(false);
      abortControllerRef.current = null;
    }
  };

  // 停止本次生成。内部只依赖 ref 与 setState，用 useCallback 固定函数标识，
  // 使下面的注册 effect 保持稳定、不会每次渲染都重复注册。
  const handleStop = useCallback(() => {
    abortControllerRef.current?.abort();
    setLoading(false);
  }, []);

  // 把生成状态同步到全局：生成期间左侧工作区铺上五彩霞光遮罩并拦截操作，
  // 生成结束 / 出错 / 被停止后遮罩自动消失
  useEffect(() => {
    setWorking(loading);
  }, [loading, setWorking]);

  // 把「停止」注册给遮罩上的停止按钮，卸载时注销
  useEffect(() => {
    registerStop(handleStop);
    return () => registerStop(null);
  }, [registerStop, handleStop]);

  // 兜底复位：请求进行中组件被卸载时，不能把遮罩留在界面上
  useEffect(() => {
    return () => setWorking(false);
  }, [setWorking]);

  const handleClear = async () => {
    try {
      // 同步清空后端记忆，避免残留历史影响后续对话
      await clearChat();
    } catch (err) {
      console.error('清空后端对话记忆失败:', err);
    } finally {
      // 无论后端是否成功都清空界面，保持界面可用
      setMessages([]);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  // 收起状态：显示浮动按钮，使用 Portal 渲染到 body
  if (!open) {
    return createPortal(
      <div className="ai-chat-trigger" onClick={onToggle}>
        <MessageOutlined style={{ fontSize: 20, color: '#fff' }} />
      </div>,
      document.body
    );
  }

  // 展开状态：右侧侧栏，悬浮 / 嵌入两种形态见文件末尾的 return
  const panel = (
    <div
      className={`ai-chat-panel${displayMode === 'dock' ? ' is-docked' : ''}`}
      style={{ '--ai-chat-panel-width': `${panelWidth}px` } as React.CSSProperties}
    >
      {/* 左边缘拖拽手柄：按住左右拖动调节宽度，双击恢复默认宽度 */}
      <div
        className="ai-chat-resizer"
        onMouseDown={handleResizeStart}
        onDoubleClick={() => setPanelWidth(DEFAULT_PANEL_WIDTH)}
        title="拖动调整宽度，双击恢复默认"
      />

      {/* 顶部栏 */}
      <div className="ai-chat-header">
        <Space>
          <RobotOutlined style={{ fontSize: 16 }} />
          <Text strong style={{ color: '#fff', fontSize: 14 }}>
            AI 助手
          </Text>
        </Space>
        <Space size={4}>
          <Button
            type="text"
            size="small"
            icon={<DeleteOutlined />}
            onClick={handleClear}
            style={{ color: 'rgba(255,255,255,0.8)' }}
            title="清空对话"
          />
          <Button
            type="text"
            size="small"
            icon={displayMode === 'float' ? <PushpinOutlined /> : <PicRightOutlined />}
            onClick={toggleDisplayMode}
            style={{ color: 'rgba(255,255,255,0.8)' }}
            title={
              displayMode === 'float'
                ? '切换到嵌入模式（融入页面布局）'
                : '切换到悬浮模式（浮动于页面右侧）'
            }
          />
          <Button
            type="text"
            size="small"
            icon={<CloseOutlined />}
            onClick={onToggle}
            style={{ color: 'rgba(255,255,255,0.8)' }}
            title="收起面板"
          />
        </Space>
      </div>

      {/* 模式切换条：单独一行而非塞进 header，避免窄栏下与标题、按钮相互挤压 */}
      <div className="ai-chat-mode-bar">
        <Segmented
          size="small"
          block
          value={mode}
          onChange={(value) => setMode(value as ChatMode)}
          options={[
            { label: '普通对话', value: 'chat' },
            { label: '知识库问答', value: 'rag' },
          ]}
        />
        {mode === 'rag' && (
          <Text type="secondary" className="ai-chat-mode-hint">
            检索知识库后作答，不写入对话记忆（刷新后不保留）
          </Text>
        )}
      </div>

      {/* 消息列表 */}
      <div className="ai-chat-messages">
        {messages.length === 0 && (
          <div className="ai-chat-empty">
            <RobotOutlined style={{ fontSize: 36, color: '#bbb' }} />
            <Text type="secondary" style={{ marginTop: 8 }}>
              开始与AI助手对话吧
            </Text>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {mode === 'rag' ? '我会先检索知识库，再基于资料回答' : '您可以向我咨询任何管理问题'}
            </Text>
          </div>
        )}

        {messages.map((msg, i) => (
          <div
            key={i}
            className={`ai-chat-message ${msg.role === 'user' ? 'message-user' : 'message-assistant'}`}
          >
            {msg.role === 'assistant' && (
              <div className="ai-avatar">
                <RobotOutlined style={{ fontSize: 14, color: '#1677ff' }} />
              </div>
            )}
            <div className="message-bubble">
              {msg.role === 'user' && (
                <div className="user-icon">
                  <UserOutlined style={{ fontSize: 12, color: '#fff' }} />
                </div>
              )}
              <div className="message-text">
                {msg.content ? (
                  // 只对 AI 回复做 Markdown 渲染；用户输入保持纯文本，
                  // 以免 Shift+Enter 的换行语义被 Markdown 折叠掉
                  msg.role === 'assistant' ? (
                    <MarkdownContent content={msg.content} />
                  ) : (
                    msg.content
                  )
                ) : loading && i === messages.length - 1 ? (
                  <>
                    <span className="typing-dot">.</span>
                    <span className="typing-dot" style={{ animationDelay: '0.2s' }}>.</span>
                    <span className="typing-dot" style={{ animationDelay: '0.4s' }}>.</span>
                  </>
                ) : null}
              </div>
              {/* 知识库问答的引用来源：sources 为 undefined 表示普通对话，不渲染本区块 */}
              {msg.sources && (
                <div className="ai-chat-sources">
                  {msg.sources.length === 0 ? (
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      未检索到相关知识库资料
                    </Text>
                  ) : (
                    <Collapse
                      ghost
                      size="small"
                      items={[
                        {
                          key: 'sources',
                          label: (
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              引用来源 ({msg.sources.length})
                            </Text>
                          ),
                          children: (
                            <Space direction="vertical" size={8} style={{ width: '100%' }}>
                              {msg.sources.map((source) => (
                                <div key={source.id}>
                                  <Space size={6} wrap>
                                    <Tag color="blue" style={{ marginInlineEnd: 0 }}>
                                      {source.score != null ? source.score.toFixed(4) : '-'}
                                    </Tag>
                                    {sourceLabel(source) && (
                                      <Text type="secondary" style={{ fontSize: 12 }}>
                                        {sourceLabel(source)}
                                      </Text>
                                    )}
                                  </Space>
                                  <Paragraph
                                    type="secondary"
                                    style={{ fontSize: 12, marginTop: 4, marginBottom: 0 }}
                                    ellipsis={{ rows: 3, expandable: true, symbol: '展开' }}
                                  >
                                    {source.content}
                                  </Paragraph>
                                </div>
                              ))}
                            </Space>
                          ),
                        },
                      ]}
                    />
                  )}
                </div>
              )}
            </div>
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

      {/* 输入区域 */}
      <div className="ai-chat-input-area">
        <Input.TextArea
          ref={inputRef as any}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="输入消息... (Enter 发送, Shift+Enter 换行)"
          autoSize={{ minRows: 1, maxRows: 4 }}
          readOnly={loading}
          className="ai-chat-input"
        />
        {loading ? (
          <Button
            type="primary"
            danger
            icon={<StopOutlined />}
            onClick={handleStop}
            className="ai-send-btn"
          />
        ) : (
          <Button
            type="primary"
            icon={<SendOutlined />}
            onClick={handleSend}
            disabled={!input.trim()}
            className="ai-send-btn"
          />
        )}
      </div>
    </div>
  );

  // 嵌入模式：直接渲染进 AdminLayout 的 flex 容器，成为布局的一列（与左侧菜单栏同级），
  // 主内容区随之收窄而不会被遮挡；
  // 悬浮模式：Portal 到 body，脱离 ProLayout 的堆叠上下文，浮在页面右侧不挤占主内容。
  return displayMode === 'dock' ? panel : createPortal(panel, document.body);
}
