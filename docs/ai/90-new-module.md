# 90 新服务模板（D7 起的营销/订单域照此执行）

## 步骤
1. **复制骨架**：从 `my-mall-product`（或 `my-mall-user`）复制 pom / 启动类 / `application.yml` / `application-dev.yml` / `generator/generatorConfig.xml`
2. **改三处命名**：`artifactId`、`spring.application.name`、`server.port`（8088 起顺延，先 `netstat` 确认不冲突）
3. **父 pom** 加 `<module>my-mall-<域></module>`
4. **依赖最小集**：`my-mall-common-web` + `nacos-discovery` + `mybatis/pagehelper/druid/mysql`；
   **按需**再加：需要 Redis 才加 `my-mall-common-redis` + 本模块 `config/RedisConfig extends MyMallBaseRedisConfig`；需要签发/读会话才加 `sa-token-*`
5. **启动类**：`@SpringBootApplication(scanBasePackages = "com.mymall")` + `@EnableDiscoveryClient` + `@MapperScan("com.mymall.<域>.dao")`
6. **yml**：数据源（同一个 `mall` 库）、`mybatis.mapper-locations: classpath:dao/*.xml`、pagehelper、actuator 只暴露 health/info、`logging.pattern.console` 带 `%X{traceId}`
7. **dev.yml**：`spring.cloud.nacos.discovery.server-addr: localhost:8848` + `namespace: 9883b8e5-fd64-49bc-8e24-d21d5690b8d0`（**不要**抄老项目的 `192.168.141.1`）
8. **MBG**：`generatorConfig.xml` 只列**本域**的表 → **在模块目录内**执行 `mvn mybatis-generator:generate`（`targetProject` 相对当前工作目录，从仓库根跑会落错位置）
9. **验证**：`mvn -o clean install -DskipTests` 通过 → 起服务 → Nacos 控制台能看到该服务 → `curl /actuator/health` = 200
10. **灰度**：用 `switch.ps1` 加**最具体的路径前缀**（绝不切 `/mall-admin/**`）→ `baseline.ps1` 8/8 → `diff.ps1` 0 差异 → `smoke.ps1`

## 每个新服务都要重复的"隐形契约清单"
- 响应/分页/token/session/权限表前缀 → 见 `00-global.md`
- 成功码 200、失败 500、校验失败 404
- 新增服务的端口与 namespace 必须写进 `AGENTS.md` 第六节索引