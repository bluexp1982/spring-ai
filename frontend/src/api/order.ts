import request from './request';
import type { ApiResult, PageResult } from '@/types/api';
import type { SaasSubscriptionOrder, OrderQueryParams } from '@/types/order';

// 分页查询订单
export function getOrderPage(params: OrderQueryParams) {
  return request.get<ApiResult<PageResult<SaasSubscriptionOrder>>>('/saas-subscription-orders', {
    params,
  });
}

// 查询订单详情
export function getOrderById(id: number) {
  return request.get<ApiResult<SaasSubscriptionOrder>>(`/saas-subscription-orders/${id}`);
}

// 创建订单
export function createOrder(data: Partial<SaasSubscriptionOrder>) {
  return request.post<ApiResult<void>>('/saas-subscription-orders', data);
}

// 更新订单
export function updateOrder(id: number, data: Partial<SaasSubscriptionOrder>) {
  return request.put<ApiResult<void>>(`/saas-subscription-orders/${id}`, data);
}

// 删除订单
export function deleteOrder(id: number) {
  return request.delete<ApiResult<void>>(`/saas-subscription-orders/${id}`);
}