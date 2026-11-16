# 开发指南

## 测试

测试类使用 `@SpringBootTest` 加载**完整应用上下文**，会读取 `application.yaml` 连接本机 PostgreSQL（`DataInitializer` 启动时还会写入默认管理员），因此运行前需保证：

- 已按 README 的「初始化数据库」建好库并导入表结构；
- 已导出 `BAILIAN_API_KEY` 与 `DB_PASSWORD`。

```bash
./mvnw test
```

当前只有上下文加载与长期记忆隔离两个测试，尚未接入 Testcontainers / CI，外部贡献者需自备数据库环境。

## 前端项目说明

### 项目结构

```text
frontend/
├── package.json                  # 依赖管理
├── vite.config.ts                # Vite 构建配置（含 /api 代理）
├── index.html                    # HTML 入口
└── src/
    ├── main.tsx                  # 应用入口
    ├── App.tsx                   # 路由配置（BrowserRouter，/admin 前缀）
    ├── index.css                 # 全局样式（含 AI 侧栏样式）
    ├── api/                      # 请求封装
    │   ├── request.ts            # Axios 实例（JWT 注入 + 401 拦截跳转）
    │   ├── order.ts              # 订单 CRUD API
    │   ├── subscription.ts       # 订阅 CRUD API
    │   ├── plan.ts               # 套餐查询 API（列表页搜索下拉选项）
    │   ├── ai.ts                 # AI 对话 API（fetch 解析 SSE 流）
    │   └── rag.ts                # 知识库 API
    ├── components/               # AiChatPanel（AI 助手侧栏）、MarkdownContent
    ├── contexts/                 # AuthContext（login/logout + localStorage 持久化）
    ├── layouts/                  # AdminLayout（ProLayout 侧边栏 + AI 助手入口）
    ├── pages/                    # login / order / subscription
    ├── types/                    # TypeScript 类型定义
    └── utils/                    # 工具函数
```

### 前端路由

| 路径 | 页面 | 说明 |
| --- | --- | --- |
| `/admin/login` | 登录页 | 公开访问，表单提交后获取 JWT token |
| `/admin` | 管理后台 | 路由守卫，无 token 时重定向到 `/admin/login`；根路径重定向到 `/admin/order` |
| `/admin/order` | 订单管理 | ProTable 分页列表（搜索：订单编号、用户、套餐、订单类型、订单状态、创建时间范围）+ 详情弹窗 |
| `/admin/subscription` | 订阅管理 | ProTable 分页列表（搜索：用户、套餐、订阅状态）+ 详情弹窗 |

> 两个列表页的搜索行为一致：「用户」为输入框，后端按显示名/登录名模糊匹配；「套餐」为下拉选择，选中后按 `planId` 精确过滤（选项来自 `GET /api/saas-plans`）。

### JWT 认证流程

1. 用户访问 `/admin` → 路由守卫检查 `localStorage` 中是否存在 `admin_token`
2. 无 token → 重定向到 `/admin/login`
3. 用户填写用户名密码 → 调用 `POST /api/auth/login`
4. 登录成功 → 将 token、用户名、显示名称存入 `localStorage`（键：`admin_token`、`admin_username`、`admin_display_name`），跳转到 `/admin`
5. 后续请求 → axios 请求拦截器自动添加 `Authorization: Bearer <token>`；AI 流式请求由 `api/ai.ts` 手动添加
6. token 过期 → 响应拦截器捕获 401，自动清除登录信息并跳回 `/admin/login`

### AI 助手侧栏

`AiChatPanel` 默认收起，仅显示右下角浮动按钮，点击后从右侧展开：

- 流式对话：消息以 SSE 形式实时展示，支持 Enter 发送 / Shift+Enter 换行
- 停止生成：生成过程中可中断当前回复
- 清空对话：清空当前会话消息列表
- 收起/展开：收起态为浮动按钮（`MessageOutlined`），展开态点击侧栏顶部关闭按钮收回

侧栏通过 `createPortal` 渲染到 `document.body`，不依赖 ProLayout 布局；对话内容为前端内存态，刷新页面后清空（历史消息可通过 `GET /api/ai/chat/history` 拉取）。
