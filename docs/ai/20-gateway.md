# 20 网关（my-mall-gateway，8201，WebFlux）

## 职责
统一入口、灰度路由（绞杀者替换）、统一鉴权（Sa-Token reactor 版）、traceId 透传。

## 路由与灰度规则（★ 最容易出事故的地方）
- 现有 5 条基础路由：`/mall-auth/**` → `lb://my-mall-auth`（已灰度），`/mall-admin/**` → `lb://mall-admin`，`/mall-portal/**`、`/mall-search/**`、`/mall-demo/**`
- 灰度接管**只能加"最具体的路径前缀"**：
  ```
  /mall-admin/admin/**             → my-mall-user
  /mall-admin/role/**              → my-mall-user
  /mall-admin/menu/**              → my-mall-user
  /mall-admin/resource/**          → my-mall-user
  /mall-admin/resourceCategory/**  → my-mall-user
  /mall-admin/memberLevel/**       → my-mall-user
  /mall-admin/product/** /brand/** /productCategory/** /productAttribute/** /sku/** /subject/** /prefrenceArea/** → my-mall-product
  ```
- **绝对禁止切 `/mall-admin/**`**：原 mall-admin 有 31 个 Controller 共用该前缀，切了会把未替换的营销/订单/上传也切走 → 大面积 404
- 动态加/删路由用 `F:\develop\mall-work\scripts\switch.ps1`（`-Path` + `-ToService`，`-Remove` 回滚）

## 已知短板（不要"顺手优化"）
- 动态路由重启会丢（未持久化）
- `management.endpoint.gateway.access` 与 `enabled` 互斥，只能留 `access: unrestricted`

## 与下游的约定
- 转发时带 `X-Trace-Id`（WebFlux filter 生成/透传）
- 鉴权：按 `requestPath` 查 Redis `auth:pathResourceMap` → 与 session 里 `permissionList` 比对
- 未带 token 打受保护路径应由网关返回 401（不是 500）
## ★ switch.ps1 的路由 id 必须唯一（实测踩坑）
`switch.ps1` 默认路由 id 是 **`gray-<服务名>`**（如 `gray-my-mall-marketing`）。给同一个服务切多条前缀时，
**后一次会覆盖前一次**（因为 id 相同，走的是同一路由的 PUT/刷新），最后只剩最后一条前缀生效 —— 而且不报错。
✅ 正确用法：**每条前缀给唯一 id**
```powershell
.\scripts\switch.ps1 -Path '/mall-admin/coupon/**' -ToService 'my-mall-marketing' -Id 'gray-mkt-coupon'
```
切换后用 `-List` 核对**前缀条数**（不是只看"已生效"）。

## ✅ 未登录访问必须看响应体，不是看 HTTP 状态码（2026-10-03 实测澄清）
不带 token 访问 `/mall-admin/admin/info`：
- HTTP 状态码 = 200
- 响应体 = {"code":401,"message":"暂未登录或token已经过期","data":null}

→ 网关鉴权**是生效的**（SaTokenConfig 命中 auth:pathResourceMap 后 StpUtil.checkPermissionOr 抛未登录，GatewayResponseWriter.unauthorized 写出 code=401 的 CommonResult）。
→ 本项目（与原项目一致）统一「HTTP 200 + 业务 code」表达状态，**验收脚本断言必须取 body 里的 code，只比 HTTP 状态码会得到假阴性**。
→ 白名单（secure.ignore.urls）只含 login/register/sso/portal 公开接口/doc.html/actuator，无需放宽。

### 权限表核对（Redis db0）
auth:pathResourceMap 有 31 个字段，key 形态为 /mall-admin/** 、/mall-admin/admin/info 这类（由 my-mall-user 的 initPathResourceMap 写入，前缀写死 /mall-admin）。
网关 spring.data.redis.database = 0，与写入方一致。