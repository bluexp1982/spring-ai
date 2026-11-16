// 后端统一响应 Result<T>
export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

// 后端分页结果 PageResult<T>
export interface PageResult<T> {
  total: number;
  page: number;
  size: number;
  records: T[];
}

// 分页查询参数
export interface PageParams {
  page?: number;
  size?: number;
}

// 登录响应
export interface LoginResult {
  token: string;
  tokenType: string;
  expiresIn: number;
  username: string;
  displayName: string;
}