// 套餐实体（仅列出管理后台用到的字段）
export interface SaasPlan {
  id?: number;
  name?: string;
  code?: string;
  price?: number;
  currency?: string;
  billingCycle?: string;
  isActive?: boolean;
}
