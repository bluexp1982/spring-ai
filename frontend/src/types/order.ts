// 订单类型
export type OrderType = 'new' | 'renewal' | 'upgrade' | 'downgrade' | 'refund' | 'cancel';

// 订单状态
export type OrderStatus = 'pending' | 'paid' | 'failed' | 'refunded' | 'expired';

// 订单实体
export interface SaasSubscriptionOrder {
  id?: number;
  orderNo?: string;
  subscriptionId?: number;
  userId?: number;
  userName?: string;
  planId?: number;
  planName?: string;
  prevPlanId?: number;
  orderType?: OrderType;
  status?: OrderStatus;
  amount?: number;
  currency?: string;
  discountAmount?: number;
  finalAmount?: number;
  couponId?: number;
  paymentMethod?: string;
  paymentChannel?: string;
  transactionId?: string;
  paidAt?: string;
  periodStart?: string;
  periodEnd?: string;
  invoiceId?: number;
  failureReason?: string;
  refundReason?: string;
  refundedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

// 订单查询参数
export interface OrderQueryParams {
  page?: number;
  size?: number;
  orderNo?: string;
  status?: OrderStatus;
  orderType?: OrderType;
  // 用户名模糊匹配（后端按名称查 ID 后过滤）
  userName?: string;
  userId?: number;
  planId?: number;
  startDate?: string;
  endDate?: string;
}

// 订单状态配置
export const ORDER_STATUS_MAP: Record<OrderStatus, { label: string; color: string }> = {
  pending: { label: '待支付', color: 'blue' },
  paid: { label: '已支付', color: 'green' },
  failed: { label: '失败', color: 'red' },
  refunded: { label: '已退款', color: 'orange' },
  expired: { label: '已过期', color: 'default' },
};

// 订单类型配置
export const ORDER_TYPE_MAP: Record<OrderType, string> = {
  new: '新购',
  renewal: '续费',
  upgrade: '升级',
  downgrade: '降级',
  refund: '退款',
  cancel: '取消',
};