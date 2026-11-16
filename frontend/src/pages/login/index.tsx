import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { App, Form, Input, Button, Card } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useAuth } from '@/contexts/AuthContext';
import request from '@/api/request';
import type { ApiResult, LoginResult } from '@/types/api';

export default function LoginPage() {
  // 使用 App.useApp() 获取实例，静态 message 无法消费 ConfigProvider 的主题上下文
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const res = await request.post<ApiResult<LoginResult>>('/auth/login', values);
      const data = res.data.data;
      login(data.token, data.username, data.displayName);
      message.success('登录成功');
      navigate('/admin', { replace: true });
    } catch {
      message.error('登录失败，请检查用户名和密码');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      }}
    >
      <Card
        style={{
          width: 400,
          boxShadow: '0 8px 24px rgba(0,0,0,0.15)',
          borderRadius: 8,
        }}
      >
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <h2 style={{ margin: 0, fontSize: 24, fontWeight: 600, color: '#1677ff' }}>
            SaaS 订阅管理系统
          </h2>
          <p style={{ margin: '8px 0 0', color: '#999' }}>请登录以继续</p>
        </div>

        <Form
          name="login"
          onFinish={handleSubmit}
          size="large"
          autoComplete="off"
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="用户名"
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="密码"
            />
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              block
              style={{ height: 44 }}
            >
              登 录
            </Button>
          </Form.Item>
        </Form>

        <div style={{ textAlign: 'center', color: '#999', fontSize: 12 }}>
          <p>默认账号: admin / admin123</p>
        </div>
      </Card>
    </div>
  );
}