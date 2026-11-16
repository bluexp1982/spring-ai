# 接口文档

## 认证

除 `/api/auth/**` 与 Swagger 路径外，所有 `/api/**` 接口均需在请求头携带 `Authorization: Bearer <token>`。详见 [SecurityConfig.java](../src/main/java/com/example/springai/config/SecurityConfig.java) 中的 `authorizeHttpRequests` 配置。

登录获取 token：

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

## Swagger / OpenAPI

项目使用 **SpringDoc OpenAPI 2.8.5**（`springdoc-openapi-starter-webmvc-ui`）自动生成接口文档，无需手动编写：

| 资源 | URL |
| --- | --- |
| Swagger UI（可视化浏览与调试） | [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) |
| OpenAPI JSON（机器可读） | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) |

基础信息在 `OpenApiConfig.java` 中注册：

```java
@Bean
public OpenAPI customOpenAPI() {
    return new OpenAPI()
            .info(new Info()
                    .title("SaaS 订阅订单管理系统 API")
                    .version("1.0.0")
                    .description("基于 Spring AI + MyBatis-Plus 的 SaaS 订阅订单管理接口"));
}
```

## 接口一览

| 前缀 | 所属模块 | 说明 |
| --- | --- | --- |
| `POST /api/auth/login` | 认证管理 | 管理员登录，返回 JWT token |
| `POST /api/ai/chat` | AI 对话 | 调用当前对话模型回答（非流式，需认证） |
| `POST /api/ai/chat/stream` | AI 对话 | 调用当前对话模型回答（SSE 流式，需认证，对应前端 AI 助手侧栏） |
| `GET /api/ai/chat/history` | AI 对话 | 查询当前会话的对话历史（需认证） |
| `POST /api/ai/chat/clear` | AI 对话 | 清空当前会话的对话记忆（需认证） |
| `POST /api/ai/rag/documents`、`POST /api/ai/rag/documents/upload` | RAG 知识库 | 文本 / 文件入库（需认证） |
| `GET /api/ai/rag/documents`、`GET /api/ai/rag/documents/search` | RAG 知识库 | 片段列表 / 纯检索（需认证） |
| `DELETE /api/ai/rag/documents` | RAG 知识库 | 删除文档（需认证） |
| `POST /api/ai/rag/chat`、`POST /api/ai/rag/chat/stream` | RAG 知识库 | 知识库问答 / 流式问答（需认证） |
| `GET/POST/PUT/DELETE /api/saas-plans/**` | 套餐管理 | 套餐 CRUD + 分页查询（需认证） |
| `GET/POST/PUT/DELETE /api/saas-subscriptions/**` | 订阅管理 | 订阅 CRUD + 分页查询（支持 `userName`/`planId`/`status`，需认证） |
| `GET/POST/PUT/DELETE /api/saas-subscription-orders/**` | 订单管理 | 订单 CRUD + 分页查询（支持 `userName`/`planId`/`status`/`orderType`/创建时间范围，需认证） |
| `GET/POST/PUT/DELETE /api/saas-invoices/**` | 发票管理 | 发票 CRUD + 分页查询（需认证） |
| `GET/POST/PUT/DELETE /api/saas-coupons/**` | 优惠券管理 | 优惠券 CRUD + 分页查询（需认证） |
| `GET/POST/PUT/DELETE /api/saas-coupon-usages/**` | 优惠券使用记录 | 使用记录 CRUD + 分页查询（需认证） |

## 列表分页查询条件

`GET /api/saas-subscription-orders` 与 `GET /api/saas-subscriptions` 除 `page` / `size` 外还支持以下过滤条件（均为可选，不传即不限制）：

| 参数 | 订单 | 订阅 | 说明 |
| --- | --- | --- | --- |
| `userName` | ✅ | ✅ | 按用户显示名（`display_name`）或登录名（`username`）模糊匹配；后端先查出用户 ID 集合再过滤业务表 |
| `userId` | ✅ | ✅ | 按用户 ID 精确匹配 |
| `planId` | ✅ | ✅ | 按套餐 ID 精确匹配（前端「套餐」搜索框为下拉选择，直接提交套餐 ID） |
| `status` | ✅ | ✅ | 订单状态 / 订阅状态 |
| `orderType` | ✅ | — | 订单类型 |
| `orderNo` | ✅ | — | 订单编号，模糊匹配 |
| `subscriptionId` | ✅ | — | 订阅 ID |
| `startDate` / `endDate` | ✅ | — | 创建时间范围，格式 `yyyy-MM-dd HH:mm:ss` |

## 如何为接口添加描述

如需为接口添加中文描述、请求示例、响应说明，可在对应 Controller 和实体类上添加注解（以 `SaasPlanController` 为例）：

```java
@Tag(name = "套餐管理", description = "SaaS 套餐的增删改查")
@RestController
@RequestMapping("/api/saas-plans")
public class SaasPlanController {

    @Operation(summary = "分页查询套餐", description = "支持按名称、价格等条件过滤")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @GetMapping
    public Result<PageResult<SaasPlan>> page(...) { ... }
}
```

实体类中可使用 `@Schema` 描述字段：

```java
@Data
public class SaasPlan {
    @Schema(description = "套餐名称", example = "专业版")
    private String name;

    @Schema(description = "单价（元）", example = "99.00")
    private BigDecimal price;
}
```

## 常用配置项

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `springdoc.api-docs.path` | OpenAPI JSON 路径 | `/v3/api-docs` |
| `springdoc.swagger-ui.path` | Swagger UI 路径 | `/swagger-ui.html` |
| `springdoc.swagger-ui.enabled` | 是否启用 Swagger UI | `true` |
| `springdoc.show-actuator` | 是否显示 Actuator 端点 | `false` |
