import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { App as AntdApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { AuthProvider, useAuth } from '@/contexts/AuthContext';
import LoginPage from '@/pages/login';
import AdminLayout from '@/layouts/AdminLayout';
import OrderPage from '@/pages/order';
import SubscriptionPage from '@/pages/subscription';
import KnowledgePage from '@/pages/knowledge';

function PrivateRoute({ children }: { children: React.ReactNode }) {
  // 统一走 AuthContext 判断：token 必须存在且未过期
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) {
    return <Navigate to="/admin/login" replace />;
  }
  return <>{children}</>;
}

function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#1677ff',
        },
      }}
    >
      {/* antd App 组件：为 message / modal 等静态方法提供 ConfigProvider 上下文（主题、国际化）
          component={false} 不额外渲染 DOM 节点，避免影响现有布局 */}
      <AntdApp component={false}>
        <BrowserRouter>
          <AuthProvider>
            <Routes>
              <Route path="/admin/login" element={<LoginPage />} />
              <Route
                path="/admin"
                element={
                  <PrivateRoute>
                    <AdminLayout />
                  </PrivateRoute>
                }
              >
                <Route index element={<Navigate to="/admin/order" replace />} />
                <Route path="order" element={<OrderPage />} />
                <Route path="subscription" element={<SubscriptionPage />} />
                <Route path="knowledge" element={<KnowledgePage />} />
              </Route>
              <Route path="*" element={<Navigate to="/admin" replace />} />
            </Routes>
          </AuthProvider>
        </BrowserRouter>
      </AntdApp>
    </ConfigProvider>
  );
}

export default App;