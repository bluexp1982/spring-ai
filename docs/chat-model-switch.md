# 对话模型切换手册（千问 ⇄ DeepSeek）

本项目支持在**不改代码、不重新打包**的前提下，通过环境变量在两个对话模型提供方之间切换：

| 取值 | 提供方 | 接入方式 | 配置前缀 |
| --- | --- | --- | --- |
| `openai`（默认） | 阿里云百炼千问 | 百炼「OpenAI 兼容模式」，复用项目的 OpenAI starter | `spring.ai.openai.chat.*` |
| `deepseek` | 原生 DeepSeek | Spring AI DeepSeek 官方 starter | `spring.ai.deepseek.*` |

**核心原理**：`spring.ai.model.chat` 是 Spring AI 的**顶层排他开关**，某个 starter 只有在开关值等于自己的名字时才会装配 `ChatModel` Bean。因此两个 starter 可以同时存在于依赖中而不冲突——同一时刻只有一个生效。项目把它参数化为 `${CHAT_PROVIDER:openai}`，从而可以用环境变量驱动。

> 排他性不是限制而是**保护**：若两个 provider 同时装配，容器中会出现两个 `ChatModel` Bean，`AiChatService` / `RagChatService` 的构造注入会直接抛 `NoUniqueBeanDefinitionException` 导致启动失败。

---

## 一、切换步骤

### 本地开发

```bash
# 前置：BAILIAN_API_KEY 必须导出——对话与向量化共用同一个百炼 Key，两种模式都依赖它
export BAILIAN_API_KEY=sk-xxx

# 默认：千问
./scripts/run.sh --backend

# 切到 DeepSeek
export CHAT_PROVIDER=deepseek
export DEEPSEEK_API_KEY=sk-xxx
./scripts/run.sh --backend

# 切回千问
unset CHAT_PROVIDER      # 或 export CHAT_PROVIDER=openai
./scripts/run.sh --backend
```

### 打包部署

```bash
# BAILIAN_API_KEY 在两种模式下都必须提供（embedding 链路依赖它）
export BAILIAN_API_KEY=sk-xxx

# 千问（默认）
java -jar target/spring-ai-0.0.1-SNAPSHOT.jar

# DeepSeek
CHAT_PROVIDER=deepseek DEEPSEEK_API_KEY=sk-xxx java -jar target/spring-ai-0.0.1-SNAPSHOT.jar
```

systemd 部署时把变量写进 `/etc/spring-ai.env` 即可：

```bash
BAILIAN_API_KEY=sk-xxx
DEEPSEEK_API_KEY=sk-xxx      # 仅 CHAT_PROVIDER=deepseek 时需要
CHAT_PROVIDER=openai         # 省略则默认 openai
```

> 切换后**必须重启进程**。本项目两个 Service 都在**构造期**用 `ChatModel` 构建 `ChatClient`，无法在不重启的情况下换模型。

---

## 二、可覆盖的环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `BAILIAN_API_KEY` | 空（**必填**） | 百炼 API Key，**对话与向量化共用**；未设置时对话会报 401 |
| `CHAT_PROVIDER` | `openai` | 对话提供方开关，取值 `openai` / `deepseek` |
| `CHAT_MODEL` | `qwen3.8-flash` | 千问模型名，须为百炼已上架的模型 |
| `BASE_URL` | 百炼北京业务空间兼容模式地址 | OpenAI 兼容接口地址，chat 与 embedding 共用 |
| `EMBEDDING_MODEL` | `qwen3.7-text-embedding` | 向量模型名 |
| `EMBEDDING_DIMENSIONS` | `1024` | 向量维度，**必须与 Milvus 集合维度一致** |
| `DEEPSEEK_API_KEY` | 空 | DeepSeek 密钥（仅 `CHAT_PROVIDER=deepseek` 时必需） |
| `DEEPSEEK_CHAT_MODEL` | `deepseek-flash` | DeepSeek 模型名 |

> 同一个业务空间下，**一个 Key 即可调用该空间内所有已开通的模型**（对话 + 向量化），因此不为 embedding 单独准备变量。
> 若将来对话与向量化落在不同业务空间或地域，可在 `application.yaml` 中补 `spring.ai.openai.chat.api-key` 与 `spring.ai.openai.chat.base-url` 分别覆盖。

---

## 三、千问思考模式

`application.yaml` 中默认已开启：

```yaml
spring:
  ai:
    openai:
      chat:
        options:
          extra-body:
            enable_thinking: true
```

`extra-body` 中的键值会被**展平进请求体顶层**，最终发给百炼的参数是 `enable_thinking: true`。

- 该配置挂在 `spring.ai.openai.chat` 分支下，只在 `CHAT_PROVIDER=openai` 时生效，切回 DeepSeek 不会带过去。
- 思考模式会让模型先输出推理过程，**增加延迟与 token 消耗**；不需要时把 `enable_thinking` 改为 `false` 或整段删除即可。
- **每次调整思考模式相关参数后，都要重跑下面的验证清单**，尤其是流式场景下的工具调用。

---

## 四、切换后验证清单

两种 provider 下都要跑一遍：

- [ ] 应用启动成功（**能启动即证明 `ChatModel` Bean 唯一**，否则构造注入会直接失败）
- [ ] `POST /api/ai/chat` 非流式返回正常
- [ ] `POST /api/ai/chat/stream` SSE 逐段输出正常
- [ ] **流式下**工具调用可用：问「查一下订单 1 的详情」应触发 `getOrderById`；问「北京天气」应触发 MCP 高德
- [ ] `POST /api/ai/rag/chat` 返回答案与引用来源（验证 embedding 链路未被 chat 配置带偏）
- [ ] `GET /api/ai/chat/history` / `POST /api/ai/chat/clear` 正常
- [ ] 长期记忆工具 `MemoryCreate` / `MemoryView` 可用

---

## 五、已知风险与限制

| 优先级 | 风险 | 说明 |
| --- | --- | --- |
| P1 | 百炼 Key 与地域绑定 | 北京地域的 Key 调用新加坡 endpoint 会直接返回 401（`Incorrect API key provided`），配置时不要混用。 |
| P1 | 思考模式 × 工具调用 | 千问开启 `enable_thinking` 后，部分模型的 function call 行为会有差异（流式下尤甚）。本项目主链路重度依赖工具调用，**调整思考模式后必须重跑验证清单**。 |
| P2 | 两家默认参数不同 | DeepSeek 默认 `temperature=1.0`，OpenAI 兼容层默认 `0.8`；上下文窗口与 `max-tokens` 上限也不同，不要在配置里写死过大的 `max-tokens`。 |
| P3 | 模型名必须存在 | 兼容模式下 `model` 必须是百炼控制台「模型列表」中已上架的名称，写错会直接报 400。 |
| P4 | 记忆不会被污染 | 本方案是「重启切换」，同一会话不存在两个模型交叉写入的情况。若将来改为运行时热切换，应把 provider 编入 `conversationId` 以隔离 `ChatMemory`。 |

> 历史记录：2026-09-17 曾在 `CHAT_PROVIDER=openai` + `POST /api/ai/chat/stream` 下实测通过「流式 + 业务工具调用」（触发 `getOrderById` 并返回真实订单数据），当时尚未开启思考模式。

---

## 六、本次未改动的文件

| 文件 | 原因 |
| --- | --- |
| `pom.xml` | `spring-ai-starter-model-deepseek` 与 `spring-ai-starter-model-openai` 均已存在，无需新增依赖 |
| `ai/AiChatService.java` | 只依赖 `ChatModel` 抽象，无任何 provider 专有类型 |
| `ai/RagChatService.java` | 同上 |
| `application.yaml` 中的 Milvus / MCP / 数据源段落 | 与对话模型切换无关，保持原样 |
