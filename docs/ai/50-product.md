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
13 个手写 DAO：PmsProductDao、PmsSkuStockDao、PmsMemberPriceDao、PmsProductLadderDao、PmsProductFullReductionDao、PmsProductAttributeValueDao、PmsProductVertifyRecordDao、PmsProductCategoryDao、PmsProductAttributeDao、PmsProductAttributeCategoryDao、PmsProductCategoryAttributeRelationDao、CmsSubjectProductRelationDao、CmsPrefrenceAreaProductRelationDao

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