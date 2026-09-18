# my-mall（自研微服务电商）

对标 `mall-swarm`，用**绞杀者模式**逐服务替换验证：新服务注册新服务名 + 新端口，
由自研网关按路径灰度接管，`diff.ps1` 逐字段对比通过后再扩大范围，出问题秒级回滚。

## 模块规划

| 模块 | 端口 | 状态 | 说明 |
|---|---|---|---|
| my-mall-common-core | — | ✅ 已建 | 零依赖契约层：CommonResult / CommonPage / ResultCode / BizException |
| my-mall-common-web | — | ✅ 已建 | Servlet 栈公共件（业务服务用；网关不能依赖） |
| my-mall-api | — | ✅ 已建 | 服务间契约（DTO + Feign），D4 起填充 |
| my-mall-gateway | 8201 | 🚧 D2 | 路由 + 统一鉴权 + traceId + 灰度 |
| my-mall-user | 8086 | ⏳ D4 | 接管 /mall-admin/admin/** |
| my-mall-product | 8087 | ⏳ D5 | 接管 /mall-admin/product/**、/mall-portal/product/** |
| my-mall-trade | 8088 | ⏳ D7 | 购物车 / 订单 / 支付 |
| my-mall-search | 8089 | ⏳ D11 | ES 搜索 |
| my-mall-marketing | 8090 | ⏳ D12 | 优惠券 / 秒杀 |
| my-mall-monitor | 8101 | ⏳ D13 | Spring Boot Admin |

## 约定

* 返回结构与原项目逐字段对齐：`CommonResult{code,message,data}`（**成功码 200**）、`CommonPage{pageNum,pageSize,total,list}`
* 替换验证期与原项目共用 `mall` 库；稳定后再按域拆库
* 原项目 `mall-swarm` 一行都不改，作为参考实现与验收基座

## 常用命令

```powershell
mvn -q clean install -DskipTests     # 编译
mvn -pl my-mall-gateway spring-boot:run
```