import { LoadingOutlined, StopOutlined } from '@ant-design/icons';
import { useAgentWorking } from '@/contexts/AgentWorkingContext';

/**
 * AI 助手工作期间覆盖在左侧工作区上的五彩霞光遮罩。
 *
 * 结构自下而上：
 * 1. 两层超大尺寸 conic-gradient 霞光，经大半径模糊后呈极光质感，持续顺时针旋转；
 * 2. 半透明白色毛玻璃「洗白层」，弱化工作区内容、统一色调；
 * 3. 底部居中的悬浮胶囊：loading 图标 + 文案 + 动态省略号 + 停止按钮。
 *
 * 遮罩铺满工作区并接收指针事件，因此工作区在生成期间不可点击、不可选中、不可滚动。
 */
export default function AgentWorkingOverlay() {
  const { working, stop } = useAgentWorking();

  // 未在工作时完全不渲染，避免常驻一层透明节点影响工作区交互与性能
  if (!working) {
    return null;
  }

  return (
    <div className="agent-working-overlay" role="status" aria-live="polite" aria-busy="true">
      {/* 霞光与洗白层被「中间挖空、向外虚化」的遮罩裁切，
          工作区中部保持清晰，只有四周被霞光环绕 */}
      <div className="agent-working-aurora-wrap" aria-hidden="true">
        {/* 霞光层：顺时针围绕工作区飘动，两层不同速度叠加出流动层次 */}
        <div className="agent-working-aurora agent-working-aurora-1" />
        <div className="agent-working-aurora agent-working-aurora-2" />
        {/* 洗白层 */}
        <div className="agent-working-veil" />
      </div>

      <div className="agent-working-pill">
        <LoadingOutlined className="agent-working-spinner" />
        <span className="agent-working-text">AI 正在工作中，请勿操作</span>
        <span className="agent-working-dots" aria-hidden="true">
          <i />
          <i />
          <i />
        </span>
        <span className="agent-working-divider" aria-hidden="true" />
        <button type="button" className="agent-working-stop" onClick={stop}>
          <StopOutlined />
          <span>停止</span>
        </button>
      </div>
    </div>
  );
}
