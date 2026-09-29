# 40 用户权限域（my-mall-user，8086）— 41 个端点

## 范围（6 个 Controller，全部已实现并与原服务逐字段一致）
| Controller | 前缀 | 端点数 |
|---|---|---|
| AdminController | `/admin` | 12 |
| UmsRoleController | `/role` | 10 |
| UmsMenuController | `/menu` | 7 |
| UmsResourceController | `/resource` | 7 |
| UmsResourceCategoryController | `/resourceCategory` | 4 |
| UmsMemberLevelController | `/memberLevel` | 1 |

## ★ 本模块专属红线
1. **`initPathResourceMap()` 的 key 前缀必须写死 `/mall-admin`**（`@Value("${my-mall.gateway.path-prefix:/mall-admin}")`）
   - 用 `spring.application.name` 会生成 `/my-mall-user/...` → 网关匹配不上 → **静默越权**，且会覆盖老服务维护的正确映射
2. **session 里写 `com.macro.mall.common.dto.UserDto`**（`AuthConstant.STP_ADMIN_INFO` = `adminInfo`）
3. **token 必须是 JWT**：`sa-token-jwt` 只引依赖不生效，必须在 `config/SaTokenConfigure` 注册 `@Bean StpLogic getStpLogicJwt() { return new StpLogicJwtForSimple(); }`（否则是 36 位 UUID，原服务是 183 位 JWT）
4. **4 条失败文案 + code=500**：`用户名或密码不能为空！` / `找不到该用户！` / `密码不正确！` / `该账号已被禁用！`
5. `permissionList` 格式 `resourceId:resourceName`；`tokenHead` 带空格
6. 缓存 key 用 `my-ums:admin`（`redis.database: mall-swarm`、`redis.expire.common: 86400`），避免与老服务 `ums:admin` 的 `@class` 串味

## 分页（3 处：`/admin/list`、`/role/list`、`/menu/list/{parentId}`、`/resource/list`、`/resourceCategory/listAll` 无分页）
统一 `PageInfo` + `CommonPage.of(...)`；默认 `pageSize=5`、`pageNum=1`

## 已知待办（不要顺手改，需单独确认）
- `UmsAdminCacheServiceImpl` 目前是空壳（缓存未生效，功能透明）