import axios from 'axios';
import type { ApiResult } from '@/types/api';

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截器：注入 JWT token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截器：统一错误处理
request.interceptors.response.use(
  (response) => {
    const data = response.data as ApiResult<unknown>;
    if (data.code !== 200) {
      console.error('API error:', data.message);
      return Promise.reject(new Error(data.message));
    }
    return response;
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('admin_token');
      localStorage.removeItem('admin_username');
      localStorage.removeItem('admin_display_name');
      window.location.href = '/admin/login';
    }
    console.error('Request failed:', error.message);
    return Promise.reject(error);
  },
);

export default request;