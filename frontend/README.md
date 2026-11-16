# spring-ai-admin

SaaS 订阅订单管理系统前端，基于 React 19 + Ant Design 6 + Vite 8。

## 环境要求

- Node.js 22+
- **pnpm 10+**（推荐，比 npm 快得多）

## 安装与启动

```bash
# 安装依赖
pnpm install

# 启动开发服务器（默认 http://localhost:5173）
pnpm dev

# 生产构建
pnpm build

# 预览构建产物
pnpm preview
```

## 技术栈

| 技术 | 版本 |
|------|------|
| React | 19 |
| TypeScript | 5.9 |
| Vite | 8 |
| Ant Design | 6 |
| Pro Components | 3.1 |
| Axios | 1.8 |
| React Router | 7 |

## 代理配置

开发服务器通过 `vite.config.ts` 中的 `server.proxy` 将 `/api` 请求代理到后端 `http://localhost:8080`。