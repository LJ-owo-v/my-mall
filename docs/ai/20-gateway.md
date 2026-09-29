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