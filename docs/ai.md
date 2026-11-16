# AI 能力说明

本项目的 AI 能力全部基于 Spring AI 2.0 实现，包括对话、RAG 知识库、工具调用与对话记忆。

## 模型说明

项目同时接入两个对话模型提供方，由 `spring.ai.model.chat` 这个**排他开关**决定当前生效的是哪一个（同一时刻只装配一个 `ChatModel` Bean，否则注入歧义会导致启动失败）：

| `CHAT_PROVIDER` | 提供方 | 模型 | 配置前缀 |
| --- | --- | --- | --- |
| `openai`（默认） | 阿里云百炼千问 | `qwen3.8-flash` | `spring.ai.openai.chat.*` |
| `deepseek` | DeepSeek 原生接口 | `deepseek-flash` | `spring.ai.deepseek.*` |

切换只需一个环境变量，无需改代码或重新打包：

```bash
export CHAT_PROVIDER=deepseek   # 切到 DeepSeek；省略或设为 openai 则走千问
```

模型名同样可用环境变量覆盖（默认值即上表）：

```bash
export CHAT_MODEL=qwen3.8-flash           # 千问模型名
export DEEPSEEK_CHAT_MODEL=deepseek-flash # DeepSeek 模型名
```

> DeepSeek 原 `deepseek-v4-flash` 已下线，现用新名 `deepseek-flash`。
>
> 千问默认开启了思考模式（`enable_thinking: true`，经 `extra-body` 透传到请求体），会增加延迟与 token 消耗。
>
> 向量化固定走百炼 OpenAI 兼容接口（`qwen3.7-text-embedding`，1024 维），**不受对话模型切换影响**。

完整的切换步骤、可覆盖变量清单与验证清单见 [chat-model-switch.md](chat-model-switch.md)。

## 对话接口

| 接口 | 说明 |
| --- | --- |
| `POST /api/ai/chat` | 非流式对话，返回完整回答 |
| `POST /api/ai/chat/stream` | SSE 流式对话，对应前端 AI 助手侧栏 |
| `GET /api/ai/chat/history` | 查询当前登录用户的对话历史 |
| `POST /api/ai/chat/clear` | 清空当前登录用户的**短期**记忆 |

## RAG 知识库

基于 Milvus 向量库 + 百炼向量模型实现，接口统一挂在 `/api/ai/rag` 下：

| 接口 | 说明 |
| --- | --- |
| `POST /api/ai/rag/documents` | 写入一段文本（自动切分、向量化后入库） |
| `POST /api/ai/rag/documents/upload` | 上传纯文本文件入库（单文件上限 5MB） |
| `GET /api/ai/rag/documents` | 查看已入库的片段列表 |
| `GET /api/ai/rag/documents/search` | 纯检索，返回命中片段与相似度得分，便于调阈值 |
| `DELETE /api/ai/rag/documents` | 删除知识库文档 |
| `POST /api/ai/rag/chat` | 问答，返回答案 + 引用来源 |
| `POST /api/ai/rag/chat/stream` | 流式问答，先推 `SOURCES` 再推 `CONTENT` |

检索参数在 `application.yaml` 的 `app.ai.rag` 下，可按知识库调优：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `top-k` | `4` | 每次召回片段数，太大引入噪声，太小可能漏召回 |
| `similarity-threshold` | `0.5` | COSINE 相似度阈值；过高会出现「库里有资料却检索不到」 |

> 调参前先用 `GET /api/ai/rag/documents/search` 观察实际得分。

## AI 工具集成

模型可调用四类工具，全部由 `AiChatService` 显式注册到 `ChatClient`（只加 `@Tool` 注解而不注册，模型是感知不到的）：

| 类别 | 来源 | 说明 |
| --- | --- | --- |
| MCP 外部工具 | `application.yaml` 的 `spring.ai.mcp.client.stdio.connections` | 通过 MCP 协议接入的外部能力（如高德地图） |
| 业务查询工具 | `biz/tools/` 下的 `@Tool` 方法 | 订阅 / 订单的只读查询与统计，共 6 个 |
| Agent Skills | `.agents/skills/*/SKILL.md` | 技能描述被语义命中时，加载该技能的完整指令 |
| 长期记忆工具 | `spring-ai-agent-utils` | 按登录用户隔离的文件型记忆，**请求级**注册 |

### MCP 外部工具

项目通过 Spring AI MCP Client 以 **stdio** 方式连接外部 MCP Server（Model Context Protocol）。当前已接入 **高德地图 MCP Server**（`@amap/amap-maps-mcp-server`），清单维护在 `application.yaml` 中：

```yaml
spring:
  ai:
    mcp:
      client:
        stdio:
          connections:
            amap-maps:
              command: npx
              args: ["-y", "@amap/amap-maps-mcp-server"]
              env:
                AMAP_MAPS_API_KEY: ${AMAP_MAPS_API_KEY:}   # 从环境变量注入，密钥不入库
```

> MCP Server 以本地子进程方式拉起，需本机已安装 Node.js 22+，首次运行 `npx -y` 会下载该 npm 包。
>
> 早期版本使用 `servers-configuration: classpath:mcp-server-config.json` 的 JSON 形式，但 Spring AI 解析该 JSON 时**不做占位符替换**，密钥只能明文写入，因此已改为 `connections` 写法。

### 业务查询工具（只读）

写操作（`create` / `updateById` / `deleteById`）刻意未暴露给模型，避免误触发数据变更：

| 域 | 工具方法 | 说明 |
| --- | --- | --- |
| 订阅 | `getSubscriptionById` | 按 ID 查询订阅详情 |
| 订阅 | `querySubscriptions` | 多条件分页查询（`userId` / `planId` / `status`） |
| 订阅 | `statsSubscriptions` | 统计聚合，维度 `status` / `plan`，可直接作为 Mermaid 饼图数据源 |
| 订单 | `getOrderById` | 按 ID 查询订单详情 |
| 订单 | `queryOrders` | 多条件分页查询（订单号模糊匹配、状态、类型、创建时间范围） |
| 订单 | `statsOrders` | 统计聚合，维度 `status` / `orderType` / `plan` / `month`，指标 `count` / `amount` |

> 分页每页最大 20 条、默认 10 条（见 `ToolParams`），结果统一带 `total` / `pages` 供模型判断是否翻页。
>
> 入参会做容错归一（日期只给到天时自动补 `00:00:00` / `23:59:59`，状态与类型接受中文），校验失败以 `{"error": "..."}` 返回，模型可据此自我纠正。

### Agent Skills

技能目录为 `src/main/resources/.agents/skills/`，每个技能一个子目录、内含 `SKILL.md`（frontmatter 声明 `name` / `description`）。启动时由 `SkillsConfig` 扫描并注册为 `ToolCallback`。

当前内置技能 `subscription-query` —— 覆盖「我的订阅 / 订单详情 / 套餐信息 / 发票 / 优惠券」等订阅业务问法。

### 长期记忆工具

由 `spring-ai-agent-utils` 的 `AutoMemoryTools` 提供（`MemoryView` / `MemoryCreate` / `MemoryInsert` / `MemoryStrReplace` 等），落盘为 markdown 文件，按**登录用户**隔离于 `{app.ai.memory.dir}/{用户}/`。

## 对话记忆持久化

短期记忆使用 Spring AI JDBC Chat Memory 持久化到 PostgreSQL 的 `SPRING_AI_CHAT_MEMORY` 表，该表由 Spring AI 启动时自动创建（`spring.ai.chat.memory.repository.jdbc.initialize-schema: always`），无需手动执行建表脚本。

会话标识（`conversationId`）取当前**登录用户名**（见 `AiChatController#currentConversationId`），因此不同用户的对话上下文相互隔离；同一用户在不同页面发起对话共用同一条记忆链。

短期记忆与长期记忆互不影响：`POST /api/ai/chat/clear` 只清 PostgreSQL 中的短期记忆，长期记忆 markdown 文件保留。

> `app.ai.memory.dir` 默认指向 `data/memory`（相对进程工作目录，可用 `MEMORY_DIR` 覆盖），刻意放在 `src/main/resources` 之外：记忆属于运行期用户数据，放在 resources 下会被打进 jar。该目录已在 `.gitignore` 中忽略；生产部署建议改为可写的绝对路径。
