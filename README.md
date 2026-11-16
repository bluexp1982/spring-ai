# spring-ai

[English](README.en.md) | 中文

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F.svg)
![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0-6DB33F.svg)

基于 Spring Boot 4 + Spring AI 2 的 SaaS 订阅订单管理系统示例，包含**后端 API 服务**与**前端管理后台**。

## 项目概况

本项目是 SaaS 订阅订单管理系统的完整示例，覆盖套餐、订阅、订单、发票、优惠券业务，后端基于 Spring Boot 4.1 + Spring AI 2.0 + PostgreSQL 18，前端基于 React 19 + TypeScript + Ant Design。AI 是主线能力：双对话模型可切换、SSE 流式对话、PostgreSQL 短期记忆、Milvus 向量检索问答，以及 MCP 与业务工具调用。

## 特性

- **AI 助手侧栏**：SSE 流式输出，支持停止生成、清空会话，随页面右下角气泡唤起。
- **双模型热切换**：千问 / DeepSeek 用一个环境变量切换，无需改代码或重新打包。
- **RAG 知识库**：文本或文件入库 → 自动切分 → 向量化 → 召回问答，回答附带引用来源。
- **双层对话记忆**：短期记忆持久化到 PostgreSQL，长期记忆按登录用户隔离为 markdown 文件。
- **工具调用**：MCP 外部工具、业务只读查询、Agent Skills、长期记忆四类工具注册给模型。
- **完整业务闭环**：套餐 / 订阅 / 订单 / 发票 / 优惠券 CRUD 与分页检索。
- **工程化配套**：JWT 认证、统一响应体、OpenAPI 文档、一键启动脚本。

## 运行

### 前置依赖

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| JDK | 21 | 后端运行 |
| Node.js | 22+ | 前端构建；同时用于拉起 MCP Server 子进程 |
| pnpm | 10+ | 前端包管理（`npm i -g pnpm`） |
| PostgreSQL | 18+ | 业务库 + 对话短期记忆 |
| Milvus | 2.6+ | RAG 知识库向量库，默认连 `localhost:19530`（不用 RAG 可暂不安装） |

### 配置环境变量

所有密钥与连接信息都通过环境变量注入，仓库不含任何凭据。样例见 [.env.example](.env.example)（本项目不自动加载 `.env`，读取的是进程环境变量）：

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `BAILIAN_API_KEY` | 是 | 阿里云百炼 API Key，**对话与向量化共用**，缺失会 401 |
| `DB_PASSWORD` | 是 | PostgreSQL 口令（`DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` 可选） |
| `BASE_URL` | 否 | 百炼 OpenAI 兼容地址，默认公开地址；使用专属业务空间时覆盖 |
| `AMAP_MAPS_API_KEY` | 否 | 高德地图 MCP Server 的 Key，缺失时地图类工具不可用 |
| `DEEPSEEK_API_KEY` | 否 | 仅 `CHAT_PROVIDER=deepseek` 时需要 |
| `JWT_SECRET` | 建议 | JWT 签名密钥（≥32 字节），未设置时使用开发默认值，**生产必须覆盖** |
| `MILVUS_HOST` / `MILVUS_PORT` | 否 | 默认 `localhost:19530` |

```bash
export BAILIAN_API_KEY=sk-你的百炼密钥
export DB_PASSWORD=你的数据库口令
export JWT_SECRET="$(openssl rand -base64 32)"
```

### 初始化数据库

```bash
createdb spring-ai

# 先创建 update_updated_at 函数（被多张表依赖）
psql -d spring-ai -c "
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS \$\$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
\$\$ LANGUAGE plpgsql;
"

psql -d spring-ai -f db/sys_user.sql
psql -d spring-ai -f db/saas_subscription_order.sql

# 可选：导入开发测试样本数据
psql -d spring-ai -f db/sample_data/sys_user_sample_data.sql
psql -d spring-ai -f db/sample_data/saas_subscription_order_sample_data.sql
```

> 若 PostgreSQL 需要口令，改用 `PGPASSWORD=$DB_PASSWORD psql -U postgres -d spring-ai -f ...`。
> `SPRING_AI_CHAT_MEMORY` 表由 Spring AI 启动时自动创建，无需手动建表。

### 启动

```bash
# 首次运行前安装前端依赖
cd frontend && pnpm install && cd ..

# 释放 8080 / 5173 端口后同时启动前后端
./scripts/run.sh
```

后端监听 `http://localhost:8080`，前端 `http://localhost:5173`（Vite 自动代理 `/api/*`）。访问前端使用默认管理员 `admin / admin123`（**仅本地演示，部署前务必修改**）。

`./scripts/run.sh` 是 bash 脚本，适用于 macOS / Linux；Windows 请分别执行：

```bash
./mvnw spring-boot:run
cd frontend && pnpm dev
```

## 文档

| 文档 | 内容 |
| --- | --- |
| [docs/ai.md](docs/ai.md) | 模型切换、对话记忆、RAG 知识库、MCP / Agent Skills / 长期记忆 |
| [docs/api.md](docs/api.md) | 接口一览、分页查询条件、Swagger、接口注解示例 |
| [docs/deployment.md](docs/deployment.md) | 打包、Nginx、systemd、配置项速查 |
| [docs/development.md](docs/development.md) | 测试、前端结构、路由与认证流程、AI 侧栏实现 |
| [docs/chat-model-switch.md](docs/chat-model-switch.md) | 千问 ⇄ DeepSeek 完整切换步骤与验证清单 |

## 目录结构

```text
.
├── src/main/java/com/example/springai/
│   ├── ai/       # 对话、RAG 知识库、长期记忆
│   ├── auth/     # JWT 认证
│   ├── biz/      # SaaS 业务（controller / service / entity / mapper / tools）
│   ├── common/   # 统一响应体、枚举、工具
│   └── config/   # 安全、OpenAPI、Skill 与记忆装配
├── src/main/resources/
│   ├── application.yaml   # 主配置（含 MCP Server 清单）
│   └── .agents/skills/    # Agent Skills（每技能一个 SKILL.md）
├── data/memory/           # 长期记忆（运行时生成，按用户隔离，不入库）
├── frontend/              # 管理后台（React + Vite + Ant Design）
├── db/                    # 建表脚本与样本数据
├── docs/                  # 详细文档
└── scripts/run.sh         # 一键启动前后端
```

## 技术栈

- **后端**：Java 21 / Spring Boot 4.1 / Spring AI 2.0（JDBC Chat Memory、MCP Client、Milvus Vector Store、Agent Utils）/ Spring Security 7 / MyBatis-Plus 3.5 / PostgreSQL 18 / JJWT
- **前端**：React 19 / TypeScript 5.9 / Vite 8 / Ant Design 6 / Pro Components 3.1 / React Router 7

## 参与贡献

欢迎提交 Issue 与 Pull Request。本地跑通项目后，请确保改动只影响单一模块，并在 PR 描述中写清验证方式。

> 提交时**不要**带上 `.env`、数据库口令、API Key 或 `data/memory/` 下的真实记忆文件，这些路径已在 `.gitignore` 中忽略。

## 许可证

本项目基于 [MIT License](LICENSE) 开源。
