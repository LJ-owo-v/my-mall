# 63 搜索域（my-mall-search，8091）— 1 Controller / 8 端点

## 归属
- 无自有表：只**只读** `pms_product` / `pms_product_attribute_value` / `pms_sku_stock` 组装索引文档（跨域只读纪律，同 D7 的 A2）
- 索引：**`pms`**（`@Document(indexName="pms")` + `@Setting(shards=1,replicas=0)`）
- 依赖：common-web + nacos-discovery + `spring-boot-starter-data-elasticsearch` + mybatis/pagehelper/druid/mysql
- 类前缀：`/esProduct`；网关入口 **`/mall-search/**`**（StripPrefix=1）→ 灰度只需一条前缀

## ★★ 三条红线（实测踩过，别再犯）
1. **索引 mapping 必须由注解创建，绝不能让 ES 动态生成**
   - 症状：`attrValueList` 不是 `nested`、`name/subTitle/keywords` 没有 `ik_max_word`、`brandName` 是 `text`
   - 后果：中文分词失效（`search/simple` 命中 0 条）；**terms 聚合在 text 字段上直接 500**（`/search/relate` 两边都 500 就是这个原因）
   - 处理：`DELETE /pms` → **重启服务**（启动时按 `@Document/@Field/@Setting` 建索引）→ 重新 `importAll`
   - 核对：`curl :9200/pms/_mapping` 里应有 `"type":"nested"`、`ik_max_word` ×3、`brandName` 为 keyword
2. **禁止用 PageHelper 的 `PageInfo` 包装 Spring Data 的 `Page`**
   - 错误写法：`new PageInfo<>(page.getContent())` → `pageNum` 恒为 1、`total` = 当前页条数、`totalPage` 恒为 1
   - 正确写法：`CommonPage.of(page.getNumber(), page.getSize(), page.getTotalElements(), page.getContent())`
   - （D6 里"漏 `PageHelper.startPage` 时 `new PageInfo<>(list)` 把整表当一页"是同一个坑）
3. **`pageNum` 是 0 基**（控制器 `defaultValue = "0"`；`PageRequest.of(pageNum, pageSize)`，不要再 `-1`）

## 环境
- ES：**7.17.27** + **analysis-ik 7.17.27**（版本必须对齐）；实测 Spring Boot 3.5 + Spring Data ES 5.5 接 7.17 可正常工作
- 索引口径：**20 篇 = 上架/未删除商品数**（`pms_product` 全表 38 行，`importAll` 只导可售商品）

## 验收状态（2026-10-04）
- 只读端点：`search/simple`、`search`（含 sort/翻页/空关键词）、`search/relate`、`recommend/{id}` → **7/7 与 8081 逐字节一致**
- 写端点：`importAll`（幂等，data=20）、`create/{id}`（响应与老服务逐字节一致）、`delete/{id}`（20→19）、`delete/batch` → **4/4 通过**
- 灰度：`/mall-search/**` → `lb://my-mall-search`（route id `gray-search`, order=-1）；回滚演练通过