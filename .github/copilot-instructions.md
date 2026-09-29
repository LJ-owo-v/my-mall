# Copilot 指令（指向 AGENTS.md）

本项目 AI 使用规范的唯一真源是根目录 **`AGENTS.md`**；模块细节见 `docs/ai/*.md`。

## 三条铁律
1. 只做可机械验证的活；`契约/事务/并发/鉴权/配置密钥` 由人写或人逐行审。
2. 任何改动必须过验证四件套（编译 + 基线/契约对比 + 边界手测 + 回滚点），未过不许提交。
3. 每行都要能解释"为什么对"。

## 硬约束（摘要）
- 不新增依赖、不改 `application*.yml`、不改公共契约类。
- 分页只能用 `new PageInfo<>(list)` + `CommonPage.of(...)`（没有 `restPage`）。
- session 里必须是 `com.macro.mall.common.dto.UserDto`。
- 权限表 key 前缀写死 `/mall-admin`。
- 成功码 200 / 失败 500 / 参数校验失败 404。

完整 14 项自检清单、模块索引、提交模板 → 见 `AGENTS.md`。