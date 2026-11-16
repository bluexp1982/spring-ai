---
name: subscription-query
description: >
  查询 SaaS 订阅管理系统的业务数据，包括用户订阅详情、套餐计划、订单记录、发票和优惠券。
  当用户询问"我的订阅"、"订单详情"、"查订单"、"套餐信息"、"我的套餐"、"发票"、"优惠券"、
  "订阅状态"、"续费"、"计费"等与订阅业务相关的问题时使用此技能。
allowed-tools: Read
---

# 订阅业务查询技能

## Overview
本技能使 AI 助手能够查询 SaaS 订阅管理系统中与用户相关的业务数据，
帮助用户快速了解自己的订阅状态、订单历史、套餐详情等信息。
除了按 ID 查单条，还支持多条件分页查询与统计聚合（可直接产出图表）。

## Instructions

1. **识别用户意图**
   - 订阅相关
     - 已知订阅 ID → `getSubscriptionById` 查单条详情
     - 需要按条件筛选、列出多条 → `querySubscriptions`
     - 需要占比 / 分布统计 → `statsSubscriptions`
   - 订单相关
     - 已知订单 ID → `getOrderById` 查单条详情
     - 需要按条件筛选、列出多条 → `queryOrders`
     - 需要占比 / 趋势统计 → `statsOrders`
   - 套餐相关：通过套餐 CRUD 接口查询

2. **查询业务数据**

   **`querySubscriptions`** — 分页查询订阅列表
   - 参数：`userId`、`planId`、`status`、`page`、`size`，全部可选，省略即不限制
   - 返回 `total / page / size / pages / records`；`records` 已含 `userName`、`planName`，
     无需再查用户表与套餐表

   **`queryOrders`** — 分页查询订单列表
   - 参数：`orderNo`（模糊匹配）、`userId`、`planId`、`status`、`orderType`、
     `startDate`、`endDate`、`page`、`size`，全部可选
   - `startDate` / `endDate` 按**创建时间**过滤，支持 `yyyy-MM-dd` 或 `yyyy-MM-dd HH:mm:ss`；
     只给日期时，起点按当天 `00:00:00`、终点按当天 `23:59:59` 处理
   - 返回结构同上，`records` 已含 `userName`、`planName`

   **分页约定**
   - 每页默认 10 条、最多 20 条；响应中的 `pages` 是总页数
   - 若 `pages` 大于当前 `page`，说明还有数据：需要时继续翻页，或提示用户收窄筛选条件
   - **禁止臆造未返回的记录**，只能陈述工具真实返回的数据

   **可选值（英文码与中文标签均可传）**
   - 订阅状态：`active` / `in_trial` / `paused` / `expired` / `cancelled`
     （对应 生效中 / 试用中 / 已暂停 / 已过期 / 已取消）
   - 订单状态：`pending` / `paid` / `failed` / `refunded` / `expired`
     （对应 待支付 / 已支付 / 支付失败 / 已退款 / 已失效）
   - 订单类型：`new` / `renewal` / `upgrade` / `downgrade` / `refund` / `cancel`
     （对应 新购 / 续费 / 升级 / 降级 / 退款 / 取消）

   - 可组合多个查询以获得完整信息（例如先查订阅，再查关联的订单）
   - 如果用户只给了模糊描述（如"我的订单"却没给订单号或用户 ID），先追问必要信息再查询

3. **展示查询结果**
   - 用中文清晰呈现查询结果，关键字段（金额、日期）精确展示
   - 列表/明细数据使用 Markdown 表格展示
   - 金额以"元"为单位，日期按北京时间（UTC+8）展示
   - 状态字段使用中文说明（如 active → "生效中"）

4. **图表可视化**
   遇到「占比、分布、趋势、对比」类问题时，**先用统计工具取数，再输出 Mermaid 图表**，
   不要让用户自己去读一堆数字：

   **`statsSubscriptions(dimension, userId, planId, status)`** — 订阅统计
   - `dimension=status` → 各订阅状态的数量
   - `dimension=plan` → 各套餐的订阅数量

   **`statsOrders(dimension, metric, status, orderType, userId, planId, startDate, endDate)`** — 订单统计
   - `dimension`：`status`（订单状态）/ `orderType`（订单类型）/ `plan`（套餐）/ `month`（创建月份）
   - `metric`：`count`（笔数）/ `amount`（实付金额之和）
   - `month` 维度按时间正序返回，其余维度按指标值倒序

   两个统计工具都返回 `items: [{key, label, value}]` 与 `total`，其中 `label` 已是中文，
   直接把 `label` 作为分组名、`value` 作为数值填入 Mermaid 即可。

   | 场景 | 取数方式 | 图表类型 |
   |------|----------|----------|
   | 各套餐订阅人数占比 | `statsSubscriptions(dimension=plan)` | `pie` |
   | 订阅状态分布 | `statsSubscriptions(dimension=status)` | `pie` |
   | 订单状态分布 | `statsOrders(dimension=status)` | `pie` |
   | 各类型订单笔数 | `statsOrders(dimension=orderType)` | `pie` |
   | 各套餐收入对比 | `statsOrders(dimension=plan, metric=amount)` | `xychart-beta` 柱状图 |
   | 月度收入 / 订单趋势 | `statsOrders(dimension=month, metric=amount)` | `xychart-beta` 柱状图 |
   | 订阅到期时间分布 | `querySubscriptions` 取到期时间 | `gantt` |

   Mermaid 代码块语法示例（需严格遵循 Mermaid 官方语法）：
   - 饼图: ```mermaid\npie title 各套餐订阅分布\n    "专业版" : 45\n    "企业版" : 30\n    "基础版" : 25\n```
   - 柱状图: ```mermaid\nxychart-beta\n    title "月度收入对比"\n    x-axis ["1月", "2月", "3月"]\n    y-axis "收入(元)" 0 --> 10000\n    bar [5200, 6800, 9100]\n```
   - 注意：`pie` 的数值直接用数字，不要加引号或单位；`xychart-beta` 需要显式声明 y 轴范围。
   - 图表必须基于工具返回的真实数值，**禁止编造数据**；数值为金额时标明单位（元）。

5. **主动建议**
   - 查询完成后，根据订阅状态主动给出操作建议
   - 如订阅即将到期 → 提醒续费
   - 如订单异常 → 建议联系客服

## Examples

- 用户："查一下我的订单 ORD-2024-001" → 调用 `getOrderById` 获取订单详情
- 用户："我的订阅什么时候到期" → 调用 `getSubscriptionById` 后展示到期时间
- 用户："我当前用的是什么套餐" → 查询订阅关联的套餐信息
- 用户："帮我看看有没有优惠券" → 查询可用优惠券列表
- 用户："列出用户 2 的全部已支付订单" →
  `queryOrders(userId=2, status=paid)` 后用 Markdown 表格展示，并说明是否还有更多分页
- 用户："最近 3 个月的订单" →
  `queryOrders(startDate=..., endDate=...)`，日期按"当月 1 日 ~ 今天"计算
- 用户："各套餐的订阅人数分布是怎样的" →
  `statsSubscriptions(dimension=plan)` 后输出 Mermaid 饼图
- 用户："最近半年的收入趋势" →
  `statsOrders(dimension=month, metric=amount)` 后用 `xychart-beta` 展示柱状图
- 用户："有多少订单是支付失败的" →
  `statsOrders(dimension=status)` 取到 `failed` 项，或直接 `queryOrders(status=failed)` 看总数

## Notes
- 所有时间均以北京时间（UTC+8）展示，无需做时区换算
- 如果查询不到数据，明确告知用户而非编造结果
- 涉及金额和支付信息时，标注数据来源和时间以确保准确性
- 工具返回的 `total` 是满足条件的总条数，`records` 只是当前页，两者不要混为一谈
