# 30 认证（my-mall-auth，8401）

## 职责
统一登录入口，**按 clientId 分流**，通过 Feign 转发到原服务（不自己查库、不自己签发 token）。

## 接口约定
- `POST /auth/login`，参数是 **`@RequestParam`（query/form）**：`clientId`、`username`、`password`
  - `clientId=admin-app` → Feign 调 `lb://mall-admin` 的 `/admin/login`
  - `clientId=portal-app` → Feign 调 `lb://mall-portal` 的 `/sso/login`
  - 其他 → `CommonResult.failed("clientId不正确")`（**code=500**，不是 404）
- 经网关访问路径是 `/mall-auth/auth/login`（网关 `StripPrefix=1`）

## 注意
- 用 `@Controller` + `@ResponseBody`，或 `@RestController`；路径前缀 `/auth`
- 不要给 `updatePassword` 这类"由 Service 返回状态码"的接口加 `@Validated`（会改变分支与文案）
- 该模块历史上因"公共模块被误加 `@Service`"启动失败过：改公共模块前先看 `docs/ai/10-common.md`
- 健康检查 503 的原因通常是 Redis 连接失败（缺 `spring.data.redis.password`）或 Nacos 不可达