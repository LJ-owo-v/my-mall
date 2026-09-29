# 50 商品与内容域（my-mall-product，8087，D6）— 44 个端点

## 范围（8 个 Controller）
| Controller | 前缀 | 端点数 | 难度 |
|---|---|---|---|
| PmsProductController | `/product` | 10 | ★★★ 多表事务写入 |
| PmsBrandController | `/brand` | 9 | ★ |
| PmsProductCategoryController | `/productCategory` | 8 | ★★ 树 + 关系表 |
| PmsProductAttributeController | `/productAttribute` | 6 | ★★ 手写聚合 SQL |
| PmsProductAttributeCategoryController | `/productAttribute/category` | 6 | ★★ 嵌套聚合 |
| PmsSkuStockController | `/sku` | 2 | ★★ 批量更新（**并发**） |
| CmsSubjectController | `/subject` | 2 | ★ |
| CmsPrefrenceAreaController | `/prefrenceArea` | 1 | ★ |

## 数据规模
17 张表（pms_product 及 7 张关联、pms_brand/product_category/product_attribute/product_attribute_category、cms_subject/cms_prefrence_area + 2 张关系表）
12 个手写 DAO（原计划的 PmsProductDao 未被任何代码引用，已删除）：PmsSkuStockDao、PmsMemberPriceDao、PmsProductLadderDao、PmsProductFullReductionDao、PmsProductAttributeValueDao、PmsProductVertifyRecordDao、PmsProductCategoryDao、PmsProductAttributeDao、PmsProductAttributeCategoryDao、PmsProductCategoryAttributeRelationDao、CmsSubjectProductRelationDao、CmsPrefrenceAreaProductRelationDao

## ★★ 必须人工经手的点（AI 只准给建议）
1. **事务边界**：`PmsProductServiceImpl.create/update` 一次写 9 张表（sku_stock、member_price、ladder、full_reduction、attribute_value、subject_product_relation、prefrence_area_product_relation + 主体 + 审核记录）。
   - 原实现**没有 `@Transactional`** → 为对齐行为先不加；**要不要加、加了会改变什么，由人决定**
2. **"先删后插"的顺序与完整性**：关系表必须先删干净再插，漏一张就是脏数据
3. **并发**：`/sku/update/{pid}` 批量改库存 → 乐观锁/行锁/超卖，AI 不会考虑
4. **聚合查询**：`listWithChildren`（分类树）、`list/withAttr`（属性分类+属性）、`attrInfo` → 排序字段、字段别名、递归组装需人工核对
5. **契约细节**：`/brand/delete/{id}` 成功返回 `CommonResult.success(null)`（**不是 count**）
6. **校验**：`PmsBrandParam` 用自定义 `@FlagValidator`（值 `{"0","1"}`，文案"厂家状态不正确"/"显示状态不正确"），必须把 `validator/FlagValidator` + `FlagValidatorClass` **一起拷过来**，否则编译不过
7. **继承式 DTO 不能改组合**：`PmsProductResult extends PmsProductParam`(+`cateParentId`)、`PmsProductAttributeCategoryItem extends PmsProductAttributeCategory`(+`productAttributeList`)、`PmsProductCategoryWithChildrenItem extends PmsProductCategory`(+`children`)
8. **路径优先级**（实测确认）：`/brand/delete/{id}`(GET) vs `/brand/delete/batch`(POST)；`/productCategory/list/{parentId}` vs `/productCategory/list/withChildren`；`/productAttribute/**` vs `/productAttribute/category/**`（两个 Controller 嵌套前缀）
9. **GET 参数绑定**：`PmsProductQueryParam` 直接作方法参数（**不加 `@RequestBody`**）；`/product/simpleList` 用裸 `String keyword` → 依赖父 pom 的 `-parameters` 编译参数，**不要去掉**

## 分页（6 处）
`/product/list`、`/brand/list`、`/productCategory/list/{parentId}`、`/productAttribute/list/{cid}`、`/productAttribute/category/list`、`/subject/list` → 统一 `PageInfo` + `CommonPage.of(...)`

## 模块依赖（最小集，别多引）
common-web + nacos-discovery + mybatis/pagehelper/druid/mysql + （**不需要** common-redis、**不需要** sa-token）
## D6 验收记录（2026-09-29）
- 规模：8 Controller / **44 端点** / 17 张表（MBG 34 model + 17 Mapper + 17 XML）/ **12 个手写 DAO + XML** / 9 DTO + 2 校验器
- 编译：`mvn -o clean install -DskipTests` → **10/10 SUCCESS**
- 只读端点直连对比（8080 原服务 vs 8087）：**19 个中 17 个逐字节一致**
- 网关：`gray-my-mall-product` 按**精确前缀**生效；`/mall-admin/coupon|order/**` 仍落在原服务（未误切整个 `/mall-admin/**`）

### ★ 2 处有意差异（与 mall-admin 不一致，已确认保留，**勿当回归处理**）
| 端点 | 差异 | 原因与结论 |
|---|---|---|
| `/product/list` | 列表**顺序**不同（新实现显式排序） | 原实现无 `order by`、依赖 MySQL 自然顺序；显式排序更可靠。分页 5 字段与 total 完全一致 |
| `/product/updateInfo/{id}` | `productAttributeValueList` 子列表顺序相反（原倒序 / 新升序） | 新实现改用 Example 分表组装，替代原 `PmsProductDao.getUpdateInfo` 大 join，性能更优；响应长度与字段完全一致 |

### 本次验收修掉的 4 个缺陷（防复发，务必遵守）
1. **XML 文件名必须与其 namespace/Dao 对应**：`resources/dao/` 里出现"文件名叫 A、namespace 是 B"的副本时，同 namespace + 同 statement id 会让 MyBatis 启动崩（`Mapped Statements collection already contains value`）。
2. **`generatorConfig.xml` 的 `trimStrings` 必须为 `false`**：MBG 会生成 `name = name.trim()` 的 setter，静默吃掉库里字段的尾空格（与老服务不一致）。
3. **树形查询必须查对表**：`listWithChildren` 要对 `pms_product_category` **自连接**（`c1.id = c2.parent_id`），且子字段别名必须带 resultMap 的 `columnPrefix`（`child_`），否则 `children` 全为 null。
4. **分页查询前必须 `PageHelper.startPage(pageNum, pageSize)`**：漏调用时 `new PageInfo<>(list)` 会把整表当成一页（`pageSize=总数`、`totalPage=1`），且不报错。