package com.mymall.gateway;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.mymall.common.dto.UserDto;
import com.mymall.common.redis.MyMallBaseRedisConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约兼容性测试 —— 用真实 Spring 上下文 + 真实 Redis，验证与原项目共享的契约。
 *
 * 跑法：mvn -pl my-mall-gateway test -Dtest=ContractCompatibilityTest
 *
 * 覆盖 4 项契约：
 *   ① Redis 序列化：用我们的 RedisTemplate 能读出 mall-admin 写的 auth:pathResourceMap
 *   ② Sa-Token 自动装配：Redis DAO 被正确装配（前提是存在 RedisTemplate<String,Object> Bean）
 *   ③ key 前缀：token-name=Authorization → 能定位 Authorization:login:session:{loginId}
 *   ④ 兼容类：session 里的 adminInfo 能还原成 com.mymall.common.dto.UserDto 并拿到权限列表
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "my-mall.probe.enabled=false"
        })
class ContractCompatibilityTest {

    private static final String PROBE_LOGIN_ID = "3";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private SaTokenDao saTokenDao;

    @Test
    @Order(1)
    @DisplayName("② Sa-Token 自动装配：Redis DAO 已就位（说明 RedisTemplate<String,Object> Bean 被认到了）")
    void saTokenDaoWired() {
        System.out.println("[验证②] SaTokenDao 实现类 = "
                + (saTokenDao == null ? "null（未装配！）" : saTokenDao.getClass().getName()));
        assertThat(saTokenDao).as("Sa-Token 必须装配了持久层 DAO").isNotNull();
        assertThat(saTokenDao.getClass().getName())
                .as("应是基于 Redis 的 DAO，而不是默认的内存 DAO（后者会出现重启即掉线）")
                .containsIgnoringCase("redis");
    }

    @Test
    @Order(2)
    @DisplayName("① Redis 序列化对齐：auth:pathResourceMap 读得出，且值是 String")
    void pathResourceMapReadable() {
        Map<Object, Object> map = redisTemplate.opsForHash()
                .entries(MyMallBaseRedisConfig.PATH_RESOURCE_MAP);
        System.out.println("[验证①] " + MyMallBaseRedisConfig.PATH_RESOURCE_MAP + " 条目数 = " + map.size());
        map.entrySet().stream().limit(3).forEach(e ->
                System.out.println("        " + e.getKey() + " -> " + e.getValue()
                        + "  (" + (e.getValue() == null ? "null" : e.getValue().getClass().getSimpleName()) + ")"));

        assertThat(map).as("权限映射表应存在（mall-admin 启动后写入）").isNotEmpty();
        assertThat(map.values()).as("值应为 String（若是 LinkedHashMap 说明 default typing 没开）")
                .allMatch(v -> v instanceof String);
    }

    @Test
    @Order(3)
    @DisplayName("③④ Sa-Token 会话：能定位 Authorization:login:session:3 并还原 UserDto")
    void saTokenSessionDeserializable() {
        SaSession session = StpUtil.getSessionByLoginId(PROBE_LOGIN_ID, false);
        Assumptions.assumeTrue(session != null,
                "会话 Authorization:login:session:" + PROBE_LOGIN_ID + " 不存在（服务已停或已过期），跳过");

        System.out.println("[验证③] 会话定位成功，id = " + session.getId()
                + " loginType = " + session.getLoginType());

        Object adminInfo = session.get("adminInfo");
        System.out.println("[验证④] adminInfo 实际类型 = "
                + (adminInfo == null ? "null" : adminInfo.getClass().getName()));
        assertThat(adminInfo)
                .as("必须能还原成 UserDto（包名已改为 com.mymall.common.dto —— 老服务的 com.macro 会话已清理）")
                .isInstanceOf(UserDto.class);

        UserDto dto = (UserDto) adminInfo;
        System.out.println("[验证④] UserDto 还原成功：id=" + dto.getId()
                + " username=" + dto.getUsername()
                + " clientId=" + dto.getClientId()
                + " 权限数=" + (dto.getPermissionList() == null ? 0 : dto.getPermissionList().size())
                + " 首个权限=" + (dto.getPermissionList() == null || dto.getPermissionList().isEmpty()
                        ? "-" : dto.getPermissionList().get(0)));
        assertThat(dto.getUsername()).isNotBlank();
        assertThat(dto.getPermissionList()).as("管理员权限列表不应为空").isNotEmpty();
    }
}