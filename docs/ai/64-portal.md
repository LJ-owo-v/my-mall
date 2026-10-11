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
4. **★ 超卖修复（D10a 遗留，代码已留 TODO）**：`OmsPortalOrderServiceImpl.lockStock` 仍是
   `selectByPrimaryKey` → 应用层 `+quantity` → `updateByPrimaryKeySelective` 的**无守卫读-改-写**：
   并发丢失更新 + 旧快照覆盖 `stock`/`sale` 列；且 `hasStock()` 与 `lockStock` 是两条独立查询（TOCTOU）。
   目标改法：`UPDATE pms_sku_stock SET lock_stock = lock_stock + #{quantity} WHERE id = #{skuId} AND stock - lock_stock >= #{quantity}`，
   影响行数=0 → `Asserts.fail("库存不足，无法下单")`（文案与老服务一致）；同 sku 合并累加；失败靠事务回滚。
   详见 `docs/后期优化文档.md` #9 与 `D10a-任务书.md` §6 B3
5. 跨域直写 5 张外部域表（`oms_order`/`oms_order_item`/`pms_sku_stock`/`sms_coupon_history`/`ums_member`）→ 将来 Feign 或 Outbox + 对账

## D10a 实测（门户下单域）

- **`PortalOrderDao.xml` 的 `resultMap extends` / `<collection resultMap>` 必须指向门户自己的**
  `com.mymall.portal.dao.OmsOrderMapper.BaseResultMap` / `OmsOrderItemMapper.BaseResultMap`；
  曾误写成 `com.mymall.order.mapper.*`（8089 模块的包，门户 classpath 里没有）→ 启动崩
  `Result Maps collection does not contain value`。
- **空分页结果不能输出 `list`**：老服务 8085 空列表返回 `{pageNum,pageSize,totalPage,total}`（87B）；
  `CommonPage.of()` 的 `list == null ? Collections.emptyList()` 兜底会让 8092 多出 `"list":[]`（97B）→ 不一致。
  已在 `CommonPage.list` 上加 `@JsonInclude(NON_EMPTY)`（`common-core` 补 `jackson-annotations` 编译期依赖）。
- **MBG 两个坑**：① `ReadOnlyCrossDomain` context 缺 `UnmergeableXmlMappersPlugin` 时，MBG 会把 `BaseResultMap`
  **追加**到既有 XML 末尾（重复定义，启动崩）→ 已补插件；② MBG `overwrite=true` 会重写 `model/`，
  实测削掉了 `UmsMember.java` 上 D9 加的 `@JsonFormat` → **每次跑完 MBG 都要 `git status` 检查 `model/` 并还原**。
- **改了 `resources` 必须重新 `package`**：`target/classes` 会缓存旧资源（建议先删 `target/classes/dao`），
  否则会出现"修了还在报同样的错"。
- 下单 A/B 实测：响应均 `code=200 下单成功`；订单主表逐字段一致、明细一致、锁库存 `lock=2/1` 一致。
  老项目促销缺陷（`pay_amount` 为负、`realAmount` 为负、赠品积分偏大）两服务一致 → **照抄保留**（见 #3/#4）。

## D10b 实测（支付宝支付 / 退货申请，6 端点）

- **端点契约（`/alipay` 4 个都必须照抄"非 CommonResult"语义）**：
  - `GET /alipay/pay`、`GET /alipay/webPay`：**裸写 HTML**（`Content-Type: text/html;charset=UTF-8`，无 `@ResponseBody`）；
    `product_code` 分别为 `FAST_INSTANT_TRADE_PAY` / `QUICK_WAP_WAY`（类名是 `AlipayTrade**Wap**PayRequest`）。
  - `POST /alipay/notify`：**裸返回字符串** `success`/`failure`；验签用 `AlipaySignature.rsaCheckV1`（`com.alipay.api.internal.util`），
    `trade_status == TRADE_SUCCESS` 才调 `paySuccessByOrderSn(outTradeNo, 1)`（**payType 硬编码 1**）。
  - `GET /alipay/query`：`CommonResult<String>`，data = `tradeStatus`；★ 照抄保留原缺陷：异常分支 `response` 为 null 会 **NPE**、`query_options` 传 `String[]`。
- **`AlipayConfig` 的 3 个默认值不要挪进 yml**：`format="JSON"`、`charset="UTF-8"`、`signType="RSA2"`；
  且 `new DefaultAlipayClient(url, appId, privateKey, format, charset, publicKey, signType)` —— **公钥在第 6 位**，顺序抄错必崩。
- **沙箱凭证（当前缺口）**：`application.yml` 的 `alipay.appId/appPrivateKey/alipayPublicKey` 目前是占位值。
  **没有真实凭证 → `pay`/`webPay` 只能验证"端点已注册 + 与 8085 同构失败"，不算功能通过**；密钥不要提交到 git（优先 Nacos）。
- **退货申请**：`create()` 用 `insert`（非 `insertSelective`）、**无 `@Transactional`**；`OmsOrderReturnApplyParam` 是 `@Getter @Setter`（不是 `@Data`），16 字段。
- **`/order/paySuccess` 端点映射**：D10a 曾有方法体但缺 `@RequestMapping`，本次补齐（老服务 65 端点里本来就有它）。

## D10c 实测（订单超时取消：消费者 + 定时兜底）

- **消费者**：`component/CancelOrderReceiver` = `@RabbitListener(queues="mall.order.cancel")`（类上）+ `@RabbitHandler handle(Long orderId)`（方法上）→ `cancelOrder(orderId)`。
  队列名字面量必须与 `QueueEnum` 一致；实测启动后 `mall.order.cancel` 的 **consumers 1 → 2**（老服务 + 新服务各一个）。
- **定时兜底**：`component/OrderTimeOutCancelTask`（`@Scheduled(cron="0 0/10 * ? * ?")`）**已按决策启用** —— 与原项目**有意差异**：
  老项目该类 `@Component` 被注释、方法 `private` → **实际不生效**（兜底只靠 MQ）。启用时必须 `@Component` + **`public` 方法**
  （`private` 不会被代理，`@Scheduled` 静默失效）。
- **★ 两个运行期缺陷（都在本轮修复）**：
  1. **`oms_order` insert 不回填主键**：MBG 的 `ReadOnlyCrossDomain` context 没给 `oms_order` 配 `<generatedKey>`，
     导致 `order.getId()` 为 null → 明细 `order_id` 写成 NULL、`sendDelayMessageCancelOrder(null)` → NPE → **整个下单事务回滚（HTTP 500）**。
     修：`OmsOrderMapper.xml` 的 insert 加 `useGeneratedKeys="true" keyProperty="id"`，并在 `generatorConfig.xml` 给 `oms_order`
     补 `<generatedKey column="id" sqlStatement="MySql" identity="true"/>`（固化，防重跑 MBG 丢失）。
  2. **`getTimeOutOrders` 捞到无明细订单 → 定时任务崩**：空 `orderItemList` 让 `releaseSkuStockLock` 的 `<foreach>` 空转，
     生成非法 SQL `UPDATE pms_sku_stock SET lock_stock = CASE id END WHERE id IN`（`BadSqlGrammarException`）。
     修：`getTimeOutOrders` 加 **`AND EXISTS (SELECT 1 FROM oms_order_item t2 WHERE t2.order_id = o.id)`**（只捞有明细的订单）——
     既防崩，又避免"订单被置为已关闭但资源未释放"的**半取消**。老项目无此条件（因其任务为死代码从未暴露）。
- **验证证据**：下单 `code=200` 且主键回填（订单 113/114）；超时取消 `status 0→4`、`lock_stock 2/1 → 0/0`；
  **17:00:00 定时任务自然触发**：`getTimeOutOrders Total:0` → INFO `取消订单数量：0`、**无 `Unexpected error`**
  （修复前 16:20/16:30 均报 `BadSqlGrammarException`）。
- **逻辑溢出**：`getTimeOutOrders` 捞不到时方法在开头 `return 0`；"无明细订单"因此不会进入释放逻辑，有明细的订单仍正常处理。

## TODO（D10c 遗留，按决策留后期统一做）

- `updateOrderStatus` 批量改状态**缺 `AND status = 0`** → 与 MQ 消费者并发时可能重复处理同一订单（应校验影响行数）。
- `releaseSkuStockLock` **缺空集合保护与 `lock_stock` 下界保护**（可被扣成负数）。
- `cancelTimeOutOrder` 的 Java 侧未做空集合兜底（当前靠 SQL 的 `EXISTS` 拦住；建议再加 `if (!isEmpty) continue;` 作为纯兜底）。
- 以上三处代码已就地标注 `TODO(D10c-幂等)`，详见 `docs/后期优化文档.md` #15 与 `D10c-任务书.md` §5.2。

## 验收（D9）
- 只读端点：8085 vs 8092 逐字节对比（**各自用自己的 token**，因为跨服务借 token 会带旧类名 session）
- 写端点：A/B 造数 + 清理
- 灰度：`.\scripts\switch.ps1 -Path '/mall-portal/**' -ToService 'my-mall-portal' -Id 'gray-portal' -Order -1`