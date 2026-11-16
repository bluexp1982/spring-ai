import { memo, useState, useEffect, useMemo, type ReactNode } from 'react';
import ReactMarkdown, { type Components } from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkBreaks from 'remark-breaks';
import mermaid from 'mermaid';

/**
 * 初始化 Mermaid，仅执行一次。
 * startOnLoad: false 防止自动扫描 DOM 跟 React 的虚拟 DOM 产生竞态。
 */
mermaid.initialize({
  startOnLoad: false,
  theme: 'default',
  securityLevel: 'strict',
});

/**
 * Markdown 解析插件。必须放在模块级常量：
 * 每次渲染新建数组会让 react-markdown 认为配置变化而重新初始化解析器。
 *
 * - remarkGfm：表格、删除线、任务列表、自动链接等 GFM 扩展语法
 * - remarkBreaks：把单个换行渲染成 <br>。模型的回答大量依赖「一行一条」的写法，
 *   不加它，单换行会被 Markdown 规范折叠成空格，属于可见的观感回退
 */
const REMARK_PLUGINS = [remarkGfm, remarkBreaks];

// ──────────────────────────── Mermaid 渲染 ────────────────────────────

/**
 * 将 Mermaid 文本异步渲染为 SVG 的内置组件。
 *
 * 设计要点：
 * - useMemo 生成稳定唯一 ID，避免 React 18/19 的 useId() 包含冒号，
 *   而 mermaid.render() 要求 ID 可用作 CSS selector。
 * - useEffect 中调用 mermaid.render()，用 cancelled 标记防止组件卸载后
 *   setState 导致 React 告警。
 * - 渲染失败（如流式输出中 Mermaid 语法不完整）时，回退显示原始代码块。
 */
function MermaidDiagram({ chart }: { chart: string }) {
  const id = useMemo(
    () => `mermaid-${Math.random().toString(36).slice(2, 11)}`,
    [],
  );
  const [svg, setSvg] = useState<string | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setError(false);

    mermaid
      .render(id, chart)
      .then(({ svg: rendered }) => {
        if (!cancelled) setSvg(rendered);
      })
      .catch(() => {
        if (!cancelled) setError(true);
      });

    return () => {
      cancelled = true;
    };
  }, [id, chart]);

  // 渲染中：占位提示（流式场景下通常一闪而过）
  if (!svg && !error) {
    return <div className="mermaid-loading">图表渲染中...</div>;
  }

  // 语法错误 / 不完整：回退显示原始代码
  if (error) {
    return (
      <pre>
        <code className="language-mermaid">{chart}</code>
      </pre>
    );
  }

  return (
    <div
      className="mermaid-diagram"
      dangerouslySetInnerHTML={{ __html: svg! }}
    />
  );
}

/**
 * react-markdown 的 code 元素自定义渲染组件。
 *
 * 检测 className 中的 language-mermaid，命中则委托给 MermaidDiagram；
 * 其余语言保持 react-markdown 的默认行为（纯 <code> 元素，不引入
 * 语法高亮库以控制打包体积）。
 */
function CodeBlock({ className, children, ...rest }: Record<string, unknown>) {
  const match = /language-(\w+)/.exec(String(className ?? ''));
  const language = match ? match[1] : '';

  if (language === 'mermaid' && children != null) {
    return <MermaidDiagram chart={String(children)} />;
  }

  return (
    <code className={className as string | undefined} {...rest}>
      {children as ReactNode}
    </code>
  );
}

// ──────────────────────────── 其余自定义渲染 ────────────────────────────

/**
 * 自定义元素渲染。同样是模块级常量，理由同上。
 *
 * 覆盖：
 * - table：GFM 表格在 300~360px 的窄侧栏里必然溢出，包一层横向滚动容器。
 * - code：Mermaid 代码块渲染为 SVG 图表。
 */
const COMPONENTS: Components = {
  // node 是 react-markdown 注入的 AST 节点，必须解构出来剔除，
  // 否则会被透传到真实 DOM 上触发 React 告警
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  table: ({ node: _node, ...props }) => (
    <div className="markdown-table-wrap">
      <table {...props} />
    </div>
  ),
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  code: CodeBlock as any,
};

// ──────────────────────────── 组件主体 ────────────────────────────

interface MarkdownContentProps {
  content: string;
}

/**
 * AI 回复的 Markdown 渲染组件。
 *
 * 用 memo 包裹：流式输出时每来一个 SSE chunk 都会重建整个 messages 数组，
 * 所有气泡都会重新渲染，memo 可以让内容未变的历史消息跳过重复解析。
 *
 * 安全说明：不注册 rehype-raw，react-markdown 默认不解析原始 HTML、
 * 也不使用 dangerouslySetInnerHTML，因此不存在 XSS 风险。
 * Mermaid 图表的 dangerouslySetInnerHTML 仅注入 mermaid 官方库生成的
 * 受限 SVG（securityLevel: 'strict'），同样不存在 XSS 风险。
 */
function MarkdownContent({ content }: MarkdownContentProps) {
  return (
    <ReactMarkdown remarkPlugins={REMARK_PLUGINS} components={COMPONENTS}>
      {content}
    </ReactMarkdown>
  );
}

export default memo(MarkdownContent);