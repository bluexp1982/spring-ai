import request from './request';
import type { ApiResult, PageResult, PageParams } from '@/types/api';
import type { SaasPlan } from '@/types/plan';

// 套餐下拉选项一次性拉取的条数（套餐数量有限，无需分页加载）
const PLAN_OPTION_SIZE = 200;

// 分页查询套餐
export function getPlanPage(params: PageParams) {
  return request.get<ApiResult<PageResult<SaasPlan>>>('/saas-plans', {
    params,
  });
}

// 套餐下拉选项（label 为套餐名，value 为套餐ID），供列表页搜索条件复用
export async function getPlanOptions() {
  const res = await getPlanPage({ page: 1, size: PLAN_OPTION_SIZE });
  return (res.data.data?.records ?? []).map((plan) => ({
    label: plan.name || `#${plan.id}`,
    value: plan.id as number,
  }));
}
