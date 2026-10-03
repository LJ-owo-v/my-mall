# 60 营销域（my-mall-marketing，8088）— 10 Controller / 50 端点

## 归属与数据
- 表：sms_* 共 13 张（本模块自己的 MBG 产物，@MapperScan("com.mymall.marketing.dao")）
- 手写 DAO：SmsCouponDao、SmsCouponProductRelationDao、SmsCouponProductCategoryRelationDao、SmsFlashPromotionProductRelationDao
- DTO（domain/）：SmsCouponParam、SmsFlashPromotionProduct、SmsFlashPromotionSessionDetail、MarketingProductView

## ★ 类前缀（实测，别按直觉写）
/home/advertise、/home/brand、/home/newProduct、/home/recommendProduct、/home/recommendSubject、
/coupon、/couponHistory、**/flash**、**/flashSession**、**/flashProductRelation**
（不是 /flashPromotion*；灰度路由写错会静默不命中）

## ★★ 有意差异（已评审保留，勿当回归处理）
| 端点 | 差异 | 原因 |
|---|---|---|
| /flashProductRelation/list | 嵌套 product 只返回 5 个字段（id/name/productSn/price/stock）；老服务返回 PmsProduct 全字段（约 30 个键，多为 null） | 迁移期用本域只读视图 MarketingProductView（A2 方案）：不 import 商品域 model、payload 更小。**将来若要求逐字节一致**：把 PmsProduct 全部字段拷进 MarketingProductView 即可 |
| /flashSession/selectList | 计数由 N+1 改为一次 GROUP BY（countBySessionId）；flashPromotionId 为 null 时直接返回空数组 | 性能优化；响应已验证逐字节一致 |

## 跨域只读纪律（迁移期临时方案）
- 允许：本域 XML 只读 SELECT 别的域的表（当前只有 pms_product，仅 5 列）
- 禁止：跨域 INSERT/UPDATE/DELETE；禁止 import 其它域的 model/mapper；禁止把其它域 MBG 产物拷进本模块
- 每个跨域读都要带 TODO：将来改 Feign 调 my-mall-product + 缓存，或商品变更事件驱动的本地快照表

## ★ 踩坑固化（实测踩过）
1. 手写 XML 的列名必须对着 PO 核：sms_flash_promotion_product_relation 的列是 flash_promotion_session_id（不是 session_id）、flash_promotion_price、flash_promotion_count、flash_promotion_limit、sort（不存在 promotion_price / promotion_stock）
2. 批量 insert 的 #{} 属性名也要对：flashPromotionPrice / flashPromotionCount / flashPromotionLimit / sort
3. 读模型必须有 getter：MarketingProductView 缺 getter 时 MyBatis 能靠字段反射赋值，但 Jackson 抛 InvalidDefinitionException: No serializer found → 加 @Data
4. DAO 方法参数是对象或超过 1 个时必须加 @Param：否则 MyBatis 把对象当根对象，报 There is no getter for property named queryParam
5. 日期格式：库里 date 型列可能被序列化成 yyyy-MM-dd（与老服务的 …T16:00:00.000+00:00 不同）→ 用 @JsonFormat 固定，注解放 getter 上（放字段会连累入参反序列化）；SimpleDateFormat 不支持小写 x，零偏移要写成字面量 +00:00
6. MBG 重跑会覆盖 model/ 下的 PO → PO 上的 @JsonFormat 会丢，重跑后要补
7. 分页查询前必须 PageHelper.startPage(pageNum, pageSize)（漏调用不报错，但整表当一页）

## 验收状态（2026-10）
- 路由清单：与老服务 50/50 一致；只读端点逐字节 13 个中 12 个一致（唯一差异即上表 A2）
- 写端点：见 F:\develop\mall-work\scripts\verify-write-d7.ps1