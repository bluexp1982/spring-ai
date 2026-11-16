import request from './request';
import type { ApiResult, PageResult } from '@/types/api';
import type { SaasSubscription, SubscriptionQueryParams } from '@/types/subscription';

// 分页查询订阅
export function getSubscriptionPage(params: SubscriptionQueryParams) {
  return request.get<ApiResult<PageResult<SaasSubscription>>>('/saas-subscriptions', {
    params,
  });
}

// 查询订阅详情
export function getSubscriptionById(id: number) {
  return request.get<ApiResult<SaasSubscription>>(`/saas-subscriptions/${id}`);
}

// 创建订阅
export function createSubscription(data: Partial<SaasSubscription>) {
  return request.post<ApiResult<void>>('/saas-subscriptions', data);
}

// 更新订阅
export function updateSubscription(id: number, data: Partial<SaasSubscription>) {
  return request.put<ApiResult<void>>(`/saas-subscriptions/${id}`, data);
}

// 删除订阅
export function deleteSubscription(id: number) {
  return request.delete<ApiResult<void>>(`/saas-subscriptions/${id}`);
}