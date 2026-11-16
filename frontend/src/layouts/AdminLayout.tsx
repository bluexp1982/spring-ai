import { useState, useEffect } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { ProLayout } from '@ant-design/pro-components';
import {
  ShoppingCartOutlined,
  LogoutOutlined,
  UserOutlined,
  CreditCardOutlined,
  DatabaseOutlined,
} from '@ant-design/icons';
import { Dropdown, Avatar, Space } from 'antd';
import { useAuth } from '@/contexts/AuthContext';
import { AgentWorkingProvider } from '@/contexts/AgentWorkingContext';
import AiChatPanel from '@/components/AiChatPanel';
import AgentWorkingOverlay from '@/components/AgentWorkingOverlay';

const menuItems = [
  {
    key: '/admin/order',
    path: '/admin/order',
    name: '订单管理',
    icon: <ShoppingCartOutlined />,
  },
  {
    key: '/admin/subscription',
    path: '/admin/subscription',
    name: '订阅管理',
    icon: <CreditCardOutlined />,
  },
  {
    key: '/admin/knowledge',
    path: '/admin/knowledge',
    name: '知识库',
    icon: <DatabaseOutlined />,
  },
];

/** 左侧菜单栏收起状态写入 localStorage 时使用的键名 */
const MENU_COLLAPSED_STORAGE_KEY = 'admin_menu_collapsed';
/** AI 助手面板展开状态写入 localStorage 时使用的键名 */
const CHAT_OPEN_STORAGE_KEY = 'ai_panel_open';

/** 读取上次保存的布尔状态：无记录或读取异常时回退到默认值 */
const loadStoredBoolean = (key: string, fallback: boolean): boolean => {
  try {
    const saved = localStorage.getItem(key);
    return saved === null ? fallback : saved === 'true';
  } catch {
    // localStorage 不可用（隐私模式等），退化为默认值
    return fallback;
  }
};

/** 持久化布尔状态；写入失败不影响功能，静默忽略 */
const persistBoolean = (key: string, value: boolean) => {
  try {
    localStorage.setItem(key, String(value));
  } catch {
    // 忽略写入异常
  }
};

export default function AdminLayout() {
  const { displayName, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  /** 左侧菜单栏是否收起，初值取自上次选择 */
  const [collapsed, setCollapsed] = useState(() => loadStoredBoolean(MENU_COLLAPSED_STORAGE_KEY, false));
  /** AI 助手面板是否展开，初值取自上次选择 */
  const [chatOpen, setChatOpen] = useState(() => loadStoredBoolean(CHAT_OPEN_STORAGE_KEY, false));

  // 记住左侧菜单栏的缩进状态，刷新页面后仍保持上次的展开 / 收起形态
  useEffect(() => {
    persistBoolean(MENU_COLLAPSED_STORAGE_KEY, collapsed);
  }, [collapsed]);

  // 记住 AI 助手面板的展开状态，刷新页面后无需重新打开
  useEffect(() => {
    persistBoolean(CHAT_OPEN_STORAGE_KEY, chatOpen);
  }, [chatOpen]);

  return (
    // Provider 只需覆盖遮罩与 AI 面板的共同祖先：两者都在 AdminLayout 内部，
    // 因此不必上提到 App.tsx，改动半径更小。
    <AgentWorkingProvider>
      {/* admin-shell 是 flex 容器：AI 助手处于嵌入模式时作为第二列参与布局，
          主内容区随之收窄；悬浮模式下它 Portal 到 body，此处只剩主内容一列。 */}
      <div className="admin-shell">
        {/* admin-workspace 是工作区的定位包裹层：遮罩以它为基准铺满整个左侧工作区
            （含左侧菜单栏），且不随 .admin-layout-container 的内部滚动而移动。 */}
        <div className="admin-workspace">
          <div className="admin-layout-container">
            <ProLayout
              title="SaaS 管理系统"
              logo="https://gw.alipayobjects.com/zos/rmsportal/KDpgvguMpGfqaHPjicRK.svg"
              layout="side"
              navTheme="light"
              collapsed={collapsed}
              onCollapse={setCollapsed}
              // 禁用 antd Sider 的响应式断点（ProLayout 默认透传 breakpoint="lg"）。
              // 该断点在挂载时会立即执行一次 matchMedia：窗口宽度 >= 992px 时匹配结果为 false，
              // 而当前 collapsed 为 true，Sider 便回调 onCollapse(false) 强制展开，
              // 从而把恢复出来的"已收起"状态再次改回展开。禁用后 collapsed 完全由本地状态控制。
              breakpoint={false}
              location={location}
              menuDataRender={() => menuItems}
              // 整行（含图标与左右内边距）点击均触发路由，而非仅点击菜单文字
              menuProps={{
                onClick: ({ key }) => navigate(key),
              }}
              actionsRender={(props) => [
                <Dropdown
                  key="user"
                  menu={{
                    items: [
                      {
                        key: 'logout',
                        icon: <LogoutOutlined />,
                        label: '退出登录',
                        onClick: () => logout(),
                      },
                    ],
                  }}
                >
                  {/* 侧栏收起（collapsed）时仅保留头像，避免用户名在 64px 窄栏内被挤压变形 */}
                  <Space style={{ cursor: 'pointer' }}>
                    <Avatar icon={<UserOutlined />} size="small" />
                    {!props.collapsed && (
                      <span className="sider-user-name">{displayName || '管理员'}</span>
                    )}
                  </Space>
                </Dropdown>,
              ]}
            >
              <Outlet />
            </ProLayout>
          </div>
          {/* AI 生成期间覆盖整个左侧工作区的五彩霞光遮罩；未生成时组件返回 null */}
          <AgentWorkingOverlay />
        </div>
        <AiChatPanel open={chatOpen} onToggle={() => setChatOpen(!chatOpen)} />
      </div>
    </AgentWorkingProvider>
  );
}
