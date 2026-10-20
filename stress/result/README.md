# 压测与验证证据

本目录保存 `docs/后期优化文档.md` 中 20 项缺陷治理的**原始实测输出**，
每一项的「复现 → 修复 → 复验」过程都能在这里找到对应文件。

- `.txt`：人工整理的实测输出（请求 / 响应 / 库内数据前后对照）
- `.jtl`：JMeter 原始结果文件（CSV），可用 JMeter 或 Excel 直接打开

> 说明：所有请求均指向本地 `127.0.0.1`，无外网依赖；日志中的 token 为测试账号，
> 库内已脱敏，不含任何真实密钥或生产数据。

## 文件对照

| 文件 | 对应缺陷 | 看什么 |
|---|---|---|
| `evidence-before.txt` / `evidence-after.txt` | #9 锁库存丢失更新（超卖） | 9 并发抢 5 件：修复前成功 9 单 → 修复后成功 5 单 |
| `evidence-idem-before.txt` / `evidence-idem-after.txt` | #14 下单幂等 | 2 并发同一请求：修复前 2 张订单 → 修复后 1 张 |
| `idem-before.jtl` / `idem-after.jtl` | #14 同上 | 同上，JMeter 原始结果 |
| `evidence-cancel-after.txt` | #15-A 取消幂等 | `lock_stock` 连调 3 次由 -1/-2 → 完全不变 |
| `evidence-cancel-endpoint.txt` | #17 取消接口「假成功」 | 返回成功但无实际动作 → 该接口已删除 |
| `evidence-coupon.txt` | #7 优惠券超发 / #8 每人限领绕过 | 8 并发抢 2 张：发出 3 张 → 2 张；同会员领到 6 张 → 1 张 |
| `coupon-oversell-before.jtl` / `coupon-oversell-after.jtl` | #7 同上 | JMeter 原始结果 |
| `coupon-limit-before.jtl` / `coupon-limit-after.jtl` | #8 同上 | JMeter 原始结果 |
| `evidence-integration-coupon.txt` | #13 积分丢失更新 + `use_integration` 不落库 | 并发两笔积分 700（丢 300）→ 400；取消订单由「永不退积分」→ 正确退还 |
| `evidence-pay-after.txt` | #16 支付回调幂等 | 回调连调 3 次 `lock_stock` 由 -1/-2 → 无变化 |
| `evidence-pay-amount.txt` | #16 回调金额校验 | 金额不一致的回调被拒绝 |
| `evidence-fullreduction.txt` | #4 满减分摊精度 | 承诺减 120 实减 118.80 → 精确 120.00 |
| `evidence-promo-expiry.txt` | #3 促销过期仍生效 | 过期最久 6.4 年仍给优惠 → 降级为无优惠 |
| `evidence-timeout-cancel.txt` | #10 无明细订单永不取消 | 订单停 1253 天 → 65 秒内自动关闭 |
| `evidence-reconcile.txt` | #12 数据对账 | 5 条不变量扫描，30 秒内检出不一致 |
| `evidence-rollbackfor.txt` | #15-B `rollbackFor` | A/B 对照：带注解写入回滚（0→0），裸注解写入提交（0→1） |
| `evidence-cart-add.txt` | #20 加购不补全价格 | 传 0.01 元 → 被服务端定价覆盖为真实价格 |

## 未纳入本目录

- `jmeter-load.log`：JMX 脚本加载失败日志（`CannotResolveClassException: SynchronizingTimer`），
  属工具链问题排查记录，不构成缺陷证据。
- 压测计划 `.jmx` 与原始响应报文：体量较大，按需单独提供。

## 复现方式

各项缺陷的完整复现步骤、根因分析与方案取舍，见
[`docs/后期优化文档.md`](../docs/后期优化文档.md)。
