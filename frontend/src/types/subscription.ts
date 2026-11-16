// 订阅状态
export type SubscriptionStatus = 'active' | 'paused' | 'expired' | 'cancelled' | 'in_trial';

// 订阅实体
export interface SaasSubscription {
  id?: number;
  userId?: number;
  userName?: string;
  planId?: number;
  planName?: string;
  status?: SubscriptionStatus;
  autoRenew?: boolean;
  startedAt?: string;
  expiredAt?: string;
  trialEndAt?: string;
  cancelledAt?: string;
  pausedAt?: string;
  pausedUntil?: string;
  currentPeriodStart?: string;
  currentPeriodEnd?: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
  updatedAt?: string;
}

// 订阅查询参数
export interface SubscriptionQueryParams {
  page?: number;
  size?: number;
  // 用户名模糊匹配（后端按名称查 ID 后过滤）
  userName?: string;
  userId?: number;
  planId?: number;
  status?: SubscriptionStatus;
}

// 订阅状态配置
export const SUBSCRIPTION_STATUS_MAP: Record<SubscriptionStatus, { label: string; color: string }> = {
  active: { label: '活跃', color: 'green' },
  paused: { label: '已暂停', color: 'orange' },
  expired: { label: '已过期', color: 'default' },
  cancelled: { label: '已取消', color: 'red' },
  in_trial: { label: '试用中', color: 'blue' },
};