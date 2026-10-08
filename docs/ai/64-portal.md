# 64 门户域（my-mall-portal，8092）— D9：7 Controller / 38 端点

> 完整门户域是 13 Controller / 65 端点，D9 完成会员与购物车（38），D10 交易（15）、D11 内容与浏览（12）待做。

## 归属与端口
- 网关入口 **`/mall-portal/**`**（StripPrefix=1）；网关侧用 `StpMemberUtil.checkLogin()` 校验会员登录态
- 依赖最全的一块：common-web + **common-redis** + sa-token×3 + nacos + openfeign + mybatis/pagehelper/druid/mysql + **mongodb** + **amqp**
- 自有表：`oms_cart_item`、`ums_member`、`ums_member_level`、`ums_member_receive_address`

## ★★ 跨域模型策略（重要决策）
跨域表（`pms_*`/`sms_*`/`oms_*`/`cms_*`/`ums_integration_*`）的 PO **由门户自己的 MBG 生成**到 `com.mymall.portal.model`：
- `generatorConfig.xml` 里的第二个 context `ReadOnlyCrossDomain`（`targetRuntime="MyBatis3"`，21 张表 = 17 跨域 + 4 自有）
- **只读**：不做跨域 INSERT/UPDATE/DELETE；**只在本模块内使用**，其它模块禁止 import 门户的 model
- 这样 `CartProduct/PromotionProduct/FlashPromotionProduct` 等 `extends PmsProduct` 的结构**原样保留**，只改 import
- 同步成本：商品/营销域改表结构后门户要**重跑 MBG**（将来改 Feign/事件快照的理由）
- ⚠️ `model/` 里的类是 MBG 产物（`overwrite=true`），**不要手改**；要扩展就写 `domain/` 衍生类

## ★ 会员登录态（与老服务一致）
- `util/StpMemberUtil`：`TYPE = "memberLogin"`，`stpLogic = new StpLogicJwtForSimple(TYPE)` → **会员 token 是 JWT**（约 191 位）
- **不需要** `SaTokenConfigure`：门户的 `StpMemberUtil` 自己 new 了 JWT 版 StpLogic（原项目也只有 `mall-admin` 注册了给后台 `StpUtil` 用的那个）
- Redis：Sa-Token session 在 **db0**（`spring.data.redis.database: 0`），与网关/认证一致

## ★ 必配的配置键（少一个就启动崩）
| 键 | 值 | 说明 |
|---|---|---|
| `spring.rabbitmq.virtual-host` | `/mall` | 本机 RabbitMQ 的 vhost 不是默认 `/`，少了会 `rabbit: DOWN`（health 503） |
| `redis.database` / `redis.key.member` / `redis.key.authCode` / `redis.expire.common` / `redis.expire.authCode` | `mall-swarm` / `ums:member` / `ums:authCode` / `86400` / `90` | `UmsMemberCacheServiceImpl` 的 5 个 `@Value`，缺一个即 `Could not resolve placeholder` |
| `sa-token.token-name` | `Authorization` | 决定 Redis key 前缀，必须与网关/认证逐项一致 |

## ★ 数据访问方式
| 技术 | 方式 |
|---|---|
| MySQL 自有表 | MBG `*Mapper` + 手写 `PortalProductDao`/`SmsCouponHistoryDao`（+XML） |
| MongoDB（浏览记录/收藏/关注） | **`MongoRepository`**（不是 MongoTemplate）：`extends MongoRepository<T, String>`，`_id` 是 String；`Page` 会多一次 count；集合名照抄老服务 |
| Redis | common-redis 的 `RedisService` |

## 行为说明（刻意与老服务保持一致）
- **未登录直连返回 HTTP 500**（`NotLoginException` 无人接管，Spring 兜底）—— 老服务 8085 完全相同；经网关访问时由网关拦成 `{"code":401,...}`。**不要**加 `@RestControllerAdvice` 改成 401，否则与老服务不一致。

## ⚠️ 迁移注意事项
**给类改包名/改名（如 `UserDto` 去 macro 化）会让 Redis 里已有的 Sa-Token session 反序列化失败**：
`InvalidTypeIdException: Could not resolve type id 'com.macro.mall.common.dto.UserDto'` → 所有需登录接口 500。
处理：清 `satoken:*` 或让用户重新登录；网关/认证等已改名的服务同样要清一次。

## 待办（TODO）
1. **领券 `POST /member/coupon/add/{couponId}` 是跨域写**（写营销域 `sms_coupon_history`）→ 将来改 Feign 调 `my-mall-marketing`
2. `PortalProductDao`/`SmsCouponHistoryDao` 读 `pms_*`/`sms_*` → 将来改 Feign 或本地快照
3. 首页聚合（D11）适合 Feign + 缓存 + 降级

## 验收（D9）
- 只读端点：8085 vs 8092 逐字节对比（**各自用自己的 token**，因为跨服务借 token 会带旧类名 session）
- 写端点：A/B 造数 + 清理
- 灰度：`.\scripts\switch.ps1 -Path '/mall-portal/**' -ToService 'my-mall-portal' -Id 'gray-portal' -Order -1`