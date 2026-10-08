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

## ★★ D9 实测结论 4 条（踩过的坑，逐条有实测依据）

### 1. 分页回显必须是 **0 基**
老服务（8085）的 `CommonPage.pageNum` 回显的是 **PageHelper 的 `page.getNumber()`（0 基）**，不是人们直觉里的 1 基。
- 写法：`CommonPage.of(page.getNumber(), page.getSize(), page.getTotalElements(), page.getContent())`
- **禁止** `new PageInfo<>(page)` 再包 `CommonPage`：`PageInfo.getPageNum()` 是 **1 基**，会让第一页回显 `pageNum=1` 而老服务是 `0` → 逐字节不一致。
- 实测：`/cart/list`、`/member/coupon/listHistory` 等分页端点，8092 与 8085 的 `pageNum/pageSize/total/page` 四字段逐字节一致。

### 2. 门户**必须**有 `config/JacksonConfig`（`Include.NON_NULL`）
缺了它，值为 `null` 的字段会照常输出 `"field":null`，而老服务统一省略 → 响应体字节不一致（这是 D9 只读对比 9/9 的前提之一）。
- 位置：`config/JacksonConfig`，`Jackson2ObjectMapperBuilder.serializationInclusion(JsonInclude.Include.NON_NULL)`
- 只读端点做逐字节对比时，**任何非空字段差异都会被它放大**，所以这个配置不是"可选优化"，是契约的一部分。

### 3. `/sso/info` 返回 `password` 哈希 —— **已确认的安全问题**（照抄保留）
`/sso/info` 的返回体里带 `ums_member.password`（BCrypt 哈希，形如 `$2a$10$...`）。
- 老服务 8085 就是这个行为，**迁移期保持一致、不做"顺手修"**（改了就逐字节不一致）。
- 已登记为安全问题：将来要修必须**两边同时改成过滤 password**，并配套改前端与验收脚本；本批不做。

### 4. 并存版本**不能共享同一会员 session**（本轮实测，最重要）
`StpMemberUtil` 的 `TYPE="memberLogin"` + `sa-token.token-name=Authorization` 决定 Redis 键：
**`Authorization:memberLogin:session:{loginId}`**。8085 与 8092 共用 db0、同一 token-name、同一 loginType → **共用同一个 session 键**；而 session 的 `dataMap.memberInfo` 是 Jackson default-typing **按 FQCN 写死**的：

```
InvalidTypeIdException: Could not resolve type id 'com.mymall.common.dto.UserDto'
  → SaSession["dataMap"] → ConcurrentHashMap → sa-token SaJsonTemplateForJackson.jsonToObject
```

**实测结果（双向都崩）**：

| 操作 | 结果 |
|---|---|
| 清空 `Authorization:memberLogin:*` → 8085 登录（写 `com.macro.mall.common.dto.UserDto`） | ✅ 200；同 token 直连 8085 ✅、经网关 ✅ |
| 同一个 token 打 8092 | ❌ HTTP 500 |
| 8092 先登录（写 `com.mymall.common.dto.UserDto`）后再打 8085 | ❌ HTTP 500 |

**硬约束（AGENTS.md 第 12 条）**：Strangler Fig 并存期内，`UserDto` 的**包名必须与老服务逐字一致** = `com.macro.mall.common.dto.UserDto`，**不许"去 macro 化"**。一旦改动，两个服务会互相让对方 500。

**改名后的清理清单（两个前缀都要清）**：
```powershell
# Redis db0；只清 mall 相关前缀，别动同库的 sys_dict:*/sys_config:*
redis-cli -a root123 -n 0 --scan --pattern 'Authorization:memberLogin:*'   # Sa-Token 会员 session + token
redis-cli -a root123 -n 0 --scan --pattern 'mall-swarm:ums:member*'        # UmsMemberCacheServiceImpl 的会员缓存（同为 FQCN 载荷）
```
只清其中一个是**不够的**：会员缓存里同样内嵌类名（`com.macro.mall.model.UmsMember`），跨服务复用会触发同一个 `InvalidTypeIdException`。

## 验收（D9）

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