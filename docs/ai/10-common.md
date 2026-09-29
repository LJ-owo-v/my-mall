# 10 公共模块（my-mall-common）

三个子模块，职责严格分离，**改动前想清楚放在哪一层**：

| 子模块 | 放什么 | 依赖约束 |
|---|---|---|
| `my-mall-common-core` | 契约层：`CommonResult`/`CommonPage`/`IErrorCode`/`ResultCode`/`BizException`/`Asserts`/`AuthConstant`/兼容类 `com.macro.mall.common.dto.UserDto` | **零外部依赖**（只有 slf4j）。禁止加入 web/持久层/Redis 依赖 |
| `my-mall-common-web` | Servlet 栈公共件：`GlobalExceptionHandler`、`TraceIdFilter`、springdoc/validation/aop/actuator/loadbalancer | 禁止被 WebFlux 的网关依赖 |
| `my-mall-common-redis` | `MyMallBaseRedisConfig`（序列化与 Sa-Token 对齐）、`RedisService`/`RedisServiceImpl` | 禁止把 `@Configuration` 放公共包（服务扫不到） |

## 三条历史教训（不要再犯）
1. **`RedisServiceImpl` 不能加 `@Service`**：common-redis 会被并不使用 Redis 的服务（如 auth）通过 common-web 传递引入，组件扫描会强建 Bean → 因缺 `RedisTemplate<String,Object>` 启动崩。正确做法：Bean 在 `MyMallBaseRedisConfig` 里用 `@Bean redisService()` 显式声明（与 mall-swarm 一致）。
2. **`GlobalExceptionHandler` 不要加 `@ExceptionHandler(Exception.class)` 兜底**：原项目没有；且会吞掉 `NoResourceFoundException`，把 404 变成 HTTP 200 + code 500。
3. **`TraceIdFilter`（servlet 版）** 头名 `X-Trace-Id`、MDC key `traceId`，必须与网关的 WebFlux 版逐字一致；日志要看到 traceId 还需 `logging.pattern.console` 带 `%X{traceId}`。

## 契约类改动红线
- `UserDto` 的 **FQCN 必须是 `com.macro.mall.common.dto.UserDto`**（Redis session 里带 `@class`，网关按此反序列化）
- `AuthConstant` 的常量值与原项目逐字一致（`admin-app`/`portal-app`/`adminInfo`/`auth:pathResourceMap`…）
- `ResultCode`：成功 `200`、`FAILED 500`、`VALIDATE_FAILED 404`、`UNAUTHORIZED 401`、`FORBIDDEN 403`