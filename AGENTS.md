# AGENTS.md — 本仓库 AI 使用规范（唯一真源）

> **所有 AI 工具在开始任何任务前先读本文件。**
> 模块细节**按需**再读 `docs/ai/*.md`（见下方"模块索引"）。
> **子模块内不放任何规则文件**（避免分散漂移）——本文件与 `docs/ai/` 是唯一维护点。
> 入口文件 `CLAUDE.md` / `.cursor/rules/mall-ai.mdc` / `.github/copilot-instructions.md` 只做指向，**永不写内容**。

---

## 一、三条铁律（违反任何一条即视为不合格交付）

1. **只做可机械验证的活**：涉及 `契约 / 事务 / 并发 / 鉴权 / 配置密钥` 的代码，必须由人写或人逐行审，AI 只能给建议。
2. **任何改动必须过"验证四件套"**：`编译 ✅ + 基线/契约对比 ✅ + 边界手测 ✅ + 回滚点 ✅`，未过不许提交。
3. **每行都要能解释"为什么对"**：解释不了就删掉重写，不许"先提交再说"。

## 二、任务分派

**✅ 交给 AI**：样板/重复代码生成（PO/DTO/Example/Mapper/XML）、有 ≥3 个同构样本时的同构扩写、机械重构（包名/import/签名）、测试骨架、读码与报错解释、文档草稿。

**⛔ 不许 AI 直接产出并提交**：事务边界与多表写入顺序、幂等/补偿、鉴权与会话（Sa-Token key 前缀、session 对象 FQCN）、DDL/索引/关键 SQL、配置与密钥与依赖版本、公共契约（响应结构/字段/状态码/默认值）、并发与缓存参数、**任何看不懂的代码**。

## 三、标准流程（6 步）

0. **任务卡**（必须固定格式，见 `docs/ai/00-global.md` 的模板）
1. 小步执行：一次只改一个关注点，禁止"顺便重构"
2. 先编译 `mvn -o -pl <module> compile -DskipTests`，再 `git diff` **逐行读**
3. 验证四件套
4. 提交时标注 **AI 参与范围 + 人工经手 + 验证方式 + 回滚点**
5. 记台账（日期/域/端点数/AI 生成文件数/人工改动/拦截缺陷/验证方式/返工/耗时）

出问题 **先回滚，再分析**；禁止"边猜边改"。

## 四、提交信息模板

```
feat(<模块>): <做了什么>

AI: <AI 生成/改动的范围，如 Controller/Service/DAO/XML 骨架（照抄 <参照>，改动清单 N 处）>
人: <人工经手的部分，如 契约核对(分页5字段/失败文案/token前缀)、事务与鉴权路径>
验证: mvn compile ✅ ／ baseline+diff 0 差异 ✅ ／ 手测 <用例> ✅ ／ 回滚点 <commit>
```

## 五、交付前自检清单（14 项，逐条确认）

1. 共享模块实现类是否被误加 `@Service`（会让没有对应 Bean 的服务启动崩）
2. `resources/**` 是否有 0 字节文件（MyBatis 启动即崩）
3. 插件式依赖是否漏了显式注册（如 `sa-token-jwt` → 必须注册 `StpLogicJwtForSimple`）
4. 每个 `@Value("${...}")` 是否都有对应配置项
5. 是否用 `spring.application.name` 拼 key（权限表前缀必须写死 `/mall-admin`）
6. 注解是否改变分支可达性（多写 `@Validated` 会把 500 分支变成 404）
7. 默认值是否与参照服务一致（如分页默认 `pageSize=5`）
8. 成功分支返回值是否与参照一致（如 `/brand/delete/{id}` 返回 `null` 而非 count）
9. 接口方法是否齐全（重载/签名与参照一致，否则编译不过）
10. `@MapperScan` / `scanBasePackages` 是否覆盖实际包路径
11. 包名是否规范（禁止大写包名、禁止用 `Impl`/`Redis` 之类目录当包名）
12. 是否重复造契约类（`UserDto` 必须是 `com.macro.mall.common.dto.UserDto`）
13. 是否抄了别人机器的配置（IP/账号/密码硬编码）
14. 是否用了公共件里不存在的 API（如 `CommonPage.restPage`，本项目只有 `of`）

## 六、模块索引（改哪个模块，就读哪个文件）

| 模块 | 端口 | 负责的路由 | 细节文件 |
|---|---|---|---|
| my-mall-common | — | 契约与公共件 | `docs/ai/10-common.md` |
| my-mall-gateway | 8201 | 全部入口 `/mall-*` | `docs/ai/20-gateway.md` |
| my-mall-auth | 8401 | `/mall-auth/**` | `docs/ai/30-auth.md` |
| my-mall-user | 8086 | `/mall-admin/{admin,role,menu,resource,resourceCategory,memberLevel}/**` | `docs/ai/40-user.md` |
| my-mall-product（D6） | 8087 | `/mall-admin/{product,brand,productCategory,productAttribute,sku,subject,prefrenceArea}/**` | `docs/ai/50-product.md` |
| my-mall-marketing | 8088 | `/mall-admin/{home/*,coupon,couponHistory,flash,flashSession,flashProductRelation}/**` | `docs/ai/60-marketing.md` |
| my-mall-order | 8089 | `/mall-admin/{order,returnApply,returnReason,orderSetting,companyAddress}/**` | `docs/ai/61-order.md` |
| my-mall-file | 8090 | `/mall-admin/aliyun/oss/**`（仅 OSS，无 MinIO） | `docs/ai/62-file.md` |
| my-mall-search | 8091 | `/mall-search/**`（Elasticsearch 索引 `pms`） | `docs/ai/63-search.md` |
| 新服务（D7 起） | — | — | `docs/ai/90-new-module.md` |
| 原服务（对照基线） | 8080 / 8085 / 8081 | mall-admin / mall-portal / mall-search | 仅作对比参照，不修改 |

## 七、自验命令（AI 完成后自行执行并在回复中贴出结果）

```powershell
mvn -o -pl <module> compile -DskipTests          # 编译
cd F:\develop\mall-work
.\scripts\baseline.ps1 -OutDir docs\baseline-<域>  # 8 条链路基线
.\scripts\diff.ps1 -Name <流程名>                  # 逐字段对比
.\scripts\switch.ps1 -List                        # 看当前灰度路由
```

## 八、维护规则

- 只有 `AGENTS.md` 与 `docs/ai/*.md` 写内容；三个入口文件只写指向。
- 新增服务：复制 `docs/ai/90-new-module.md` 的流程，并在第六节索引里加一行。
- 每踩一个新坑：追加到第五节的 14 项清单（或对应模块文件），保持"一次踩坑、长期免疫"。