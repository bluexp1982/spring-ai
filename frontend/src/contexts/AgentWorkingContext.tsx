import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';

/**
 * AI 助手「工作中」状态的全局共享。
 *
 * AI 助手的生成状态原本是 AiChatPanel 的内部 useState，左侧工作区的遮罩无法感知，
 * 因此把状态提升到这里：
 * - AiChatPanel 在 loading 变化时调用 setWorking 同步；
 * - AiChatPanel 把自己的「停止」逻辑通过 registerStop 注册进来；
 * - 左侧遮罩读取 working 决定显隐，并调用 stop 触发停止。
 */
interface AgentWorkingContextValue {
  /** AI 助手是否正在生成，驱动左侧工作区遮罩的显隐 */
  working: boolean;
  /** 由 AiChatPanel 在 loading 变化时同步 */
  setWorking: (working: boolean) => void;
  /** 注册当前生成的停止回调；传 null 表示当前没有可停止的请求 */
  registerStop: (handler: (() => void) | null) => void;
  /** 触发停止当前生成；没有注册回调时为空操作 */
  stop: () => void;
}

const AgentWorkingContext = createContext<AgentWorkingContextValue | null>(null);

export function AgentWorkingProvider({ children }: { children: ReactNode }) {
  const [working, setWorking] = useState(false);
  /** 停止回调放在 ref 中，保证 stop / registerStop 的函数标识始终稳定 */
  const stopRef = useRef<(() => void) | null>(null);

  const registerStop = useCallback((handler: (() => void) | null) => {
    stopRef.current = handler;
  }, []);

  const stop = useCallback(() => {
    stopRef.current?.();
  }, []);

  // setWorking 来自 useState，标识天然稳定；value 仅在 working 变化时重建，
  // 避免遮罩之外的使用方被无关渲染波及
  const value = useMemo<AgentWorkingContextValue>(
    () => ({ working, setWorking, registerStop, stop }),
    [working, registerStop, stop]
  );

  return <AgentWorkingContext.Provider value={value}>{children}</AgentWorkingContext.Provider>;
}

export function useAgentWorking(): AgentWorkingContextValue {
  const context = useContext(AgentWorkingContext);
  if (!context) {
    throw new Error('useAgentWorking must be used within an AgentWorkingProvider');
  }
  return context;
}
