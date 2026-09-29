# 00 全局约定（所有模块通用）

## 技术栈
Java 17 · Spring Boot 3.5.14 · Spring Cloud 2025.0.2 · Spring Cloud Alibaba 2025.0.0.0 · MyBatis 3.5.17 + MBG 1.4.2 + PageHelper 6.1.1 + Druid · MySQL 8 · Nacos(dev 命名空间) · Sa-Token 1.42.0 · Redis

## 包与目录约定
- 业务包：`com.mymall.<服务>.<controller|service|service.impl|dao|model|domain|config>`
- MyBatis：Mapper 接口在 `com.mymall.<服务>.dao`，XML 在 `resources/dao/*.xml`，`@MapperScan("com.mymall.<服务>.dao")`
- 禁止：大写包名；禁止把 `Impl`/`Redis` 这类目录当包名
- 启动类必须 `@SpringBootApplication(scanBasePackages = "com.mymall")`（否则扫不到公共件）

## 契约（改动即视为 BUG）
| 项 | 值 |
|---|---|
| 统一响应 | `CommonResult`：成功 `code=200`，失败 `500`，参数校验失败 `404` |
| 分页 | `CommonPage` 五字段 `pageNum/pageSize/totalPage/total/list`；本项目**只有 `CommonPage.of(...)`**，没有 `restPage` |
| 分页写法 | `List<T> list = service.xxx(...); PageInfo<T> pi = new PageInfo<>(list); CommonPage.of(pi.getPageNum(), pi.getPageSize(), pi.getTotal(), pi.getList())` |
| token | `token-name=Authorization`、`token-prefix=Bearer`、**`tokenHead = prefix + " "`（含空格）** |
| session 对象 | 必须是 `com.macro.mall.common.dto.UserDto`（`adminInfo`），**不许自建同名类** |
| 权限表 | Redis key `auth:pathResourceMap`，**前缀写死 `/mall-admin`**（禁止用 `spring.application.name`） |
| 成功码 | 是 **200** 不是 0 |

## MyBatis 约定
- 只生成"域内表"（数据私有），不建共享 mbg 模块
- 手写 SQL 放 `resources/dao/<Dao名>.xml`，`namespace` 与接口全限定名一致
- MBG 重跑前先确认手写 XML 不会被覆盖（配 `UnmergeableXmlMappersPlugin`）

## 禁止项（AI 不得擅自做）
1. 新增依赖、升级依赖版本
2. 修改 `application*.yml`（尤其是数据库/Redis 密码、端口、namespace）
3. 修改公共模块的契约类（`CommonResult`/`CommonPage`/`UserDto`/`AuthConstant`）
4. 修改 `@MapperScan`/`scanBasePackages` 之外的项目结构
5. 硬编码环境信息（IP/账号/密码）

## 任务卡模板（每次交给 AI 前必须填）
```
【目标】一句话
【范围】只允许改：… 禁止改：…
【参照】照抄哪个现成实现（原项目路径 / 本项目模块）
【必须改动】逐条列（包名 / 分页 API / 配置前缀 / Bean 装配 / 返回值…）
【契约约束】字段/状态码/默认值 必须与参照一致
【验收】编译 + 哪个脚本 + 哪些用例
【禁止】不许改公共契约 / 不许加依赖 / 不许改配置
```

## 环境与端口
| 组件 | 地址 |
|---|---|
| 网关（自研） | 8201 |
| my-mall-auth | 8401 |
| my-mall-user | 8086 |
| my-mall-product（D6） | 8087 |
| 原 mall-admin / portal / search | 8080 / 8085 / 8081 |
| Nacos 控制台 / OpenAPI | 8180 / 8848（命名空间 `9883b8e5-fd64-49bc-8e24-d21d5690b8d0`） |
| MySQL / Redis / MongoDB | 3306(root/123456) / 6379(密码 root123) / 27017 |