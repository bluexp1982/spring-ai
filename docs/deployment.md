# 部署与配置

## 后端打包

`./mvnw spring-boot:run` 仅用于本地开发，正式部署请先打成可执行 jar：

```bash
./mvnw clean package -DskipTests
```

产物为 `target/spring-ai-0.0.1-SNAPSHOT.jar`，可先用 `java -jar` 本地验证：

```bash
DB_PASSWORD=你的数据库口令 BAILIAN_API_KEY=sk-你的百炼密钥 \
  java -jar target/spring-ai-0.0.1-SNAPSHOT.jar

# 若要把对话切到 DeepSeek
CHAT_PROVIDER=deepseek DEEPSEEK_API_KEY=sk-你的DeepSeek密钥 \
  DB_PASSWORD=你的数据库口令 BAILIAN_API_KEY=sk-你的百炼密钥 \
  java -jar target/spring-ai-0.0.1-SNAPSHOT.jar
```

## 前端构建

```bash
cd frontend
pnpm build
```

产物为 `frontend/dist/`，可直接部署到 Nginx 或 CDN。生产环境需将 `/api` 请求反向代理到后端：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    root /opt/spring-ai-admin/dist;
    index index.html;

    # 前端路由 SPA 支持
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 后端部署（systemd）

Linux 服务器建议使用 systemd 守护进程，示例 `/etc/systemd/system/spring-ai.service`：

```ini
[Unit]
Description=spring-ai
After=network.target

[Service]
User=deploy
EnvironmentFile=/etc/spring-ai.env
ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar /opt/spring-ai/spring-ai.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

密钥放入 `/etc/spring-ai.env`（建议权限 600）：

```bash
BAILIAN_API_KEY=sk-你的百炼密钥
DB_PASSWORD=你的数据库口令
JWT_SECRET=用 openssl rand -base64 32 生成的随机串
AMAP_MAPS_API_KEY=你的高德地图Key        # 可选
DEEPSEEK_API_KEY=sk-你的DeepSeek密钥     # 仅 CHAT_PROVIDER=deepseek 时需要
# CHAT_PROVIDER=deepseek                # 省略则默认 openai（千问）
# BASE_URL=https://...                  # 使用专属业务空间地址时才需要
```

然后启用并启动：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now spring-ai
sudo systemctl status spring-ai
```

## 启动方式对比

| 方式 | 适用场景 |
| --- | --- |
| `./mvnw spring-boot:run` | 本地开发、调试 |
| `java -jar xxx.jar` | 单机正式部署（配合 systemd） |
| 容器镜像（Docker / K8s） | 云环境、多实例部署 |
| `pnpm dev`（前端） | 前端本地开发 |
| `pnpm build`（前端） | 前端生产构建，产物部署到 Nginx |

## 后端配置项

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `spring.ai.model.chat` | 对话 provider 排他开关，来自 `${CHAT_PROVIDER}` | `openai` |
| `spring.ai.openai.api-key` | 百炼 API Key（对话 + 向量化共用），来自 `${BAILIAN_API_KEY}` | 空（必填） |
| `spring.ai.openai.base-url` | OpenAI 兼容接口地址，来自 `${BASE_URL}` | 百炼公开兼容地址 `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| `spring.ai.openai.chat.options.model` | 千问模型名，来自 `${CHAT_MODEL}` | `qwen3.8-flash` |
| `spring.ai.openai.chat.options.extra-body.enable_thinking` | 千问思考模式开关 | `true` |
| `spring.ai.openai.embedding.options.model` | 向量模型名，来自 `${EMBEDDING_MODEL}` | `qwen3.7-text-embedding` |
| `spring.ai.openai.embedding.options.dimensions` | 向量维度，来自 `${EMBEDDING_DIMENSIONS}` | `1024` |
| `spring.ai.vectorstore.milvus.embedding-dimension` | Milvus 集合维度，须与上面 dimensions 一致 | `${EMBEDDING_DIMENSIONS:1024}` |
| `spring.ai.deepseek.api-key` | DeepSeek API 密钥，来自 `${DEEPSEEK_API_KEY}` | 空（`CHAT_PROVIDER=deepseek` 时必填） |
| `spring.ai.deepseek.base-url` | DeepSeek API 地址 | `https://api.deepseek.com` |
| `spring.ai.deepseek.chat.model` | DeepSeek 模型名，来自 `${DEEPSEEK_CHAT_MODEL}` | `deepseek-flash` |
| `jwt.secret` | JWT 签名密钥（≥32 字节），来自 `${JWT_SECRET}` | 开发默认值（**生产必须覆盖**） |
| `jwt.expiration-ms` | JWT 过期时间（毫秒），来自 `${JWT_EXPIRATION_MS}` | `86400000`（24 小时） |
| `spring.datasource.password` | PostgreSQL 口令，来自 `${DB_PASSWORD}` | `postgres` |
| `spring.ai.chat.memory.repository.jdbc.initialize-schema` | 启动时自动建表 `SPRING_AI_CHAT_MEMORY` | `always` |
| `app.ai.memory.dir` | 长期记忆根目录，来自 `${MEMORY_DIR}` | `data/memory`（位于源码树外，不入库） |
| `spring.ai.mcp.client.enabled` | 是否启用 MCP 客户端 | `true` |
| `spring.ai.mcp.client.type` | MCP 客户端类型 | `SYNC` |
| `spring.ai.mcp.client.stdio.connections.*` | MCP Server 清单（command / args / env） | 内置高德地图示例，Key 取 `${AMAP_MAPS_API_KEY}` |

## 前端配置

前端配置位于 `frontend/vite.config.ts`，主要是代理设置：

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `server.port` | 开发服务器端口 | `5173` |
| `server.proxy./api.target` | API 代理目标地址 | `http://localhost:8080` |

如需修改后端接口地址，只需调整 `vite.config.ts` 中的 `target`，无需修改前端代码。
