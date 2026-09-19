package com.mymall.gateway.probe;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.macro.mall.common.dto.UserDto;
import com.mymall.common.redis.MyMallBaseRedisConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 契约自检（仅 my-mall.probe.enabled=true 时生效）：
 * 一次验证三件事 —— Redis 序列化 / Sa-Token key 前缀 / 兼容类 UserDto。
 */
@Component
@ConditionalOnProperty(name = "my-mall.probe.enabled", havingValue = "true")
public class RedisContractProbe implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RedisContractProbe.class);
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisContractProbe(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        // ① 应用数据：权限映射表（验证 RedisTemplate 序列化与原项目一致）
        try {
            Map<Object, Object> map = redisTemplate.opsForHash()
                    .entries(MyMallBaseRedisConfig.PATH_RESOURCE_MAP);
            log.info("[契约自检] {} 读取成功，条目数={}", MyMallBaseRedisConfig.PATH_RESOURCE_MAP, map.size());
            map.entrySet().stream().limit(3).forEach(e ->
                    log.info("    {} -> {} ({})", e.getKey(), e.getValue(),
                            e.getValue() == null ? "null" : e.getValue().getClass().getSimpleName()));
        } catch (Exception e) {
            log.error("[契约自检] 权限映射表读取失败：Redis 序列化配置与原项目不一致", e);
        }

        // ② Sa-Token 会话（验证 token-name 前缀 + 兼容类 UserDto）
        String loginId = System.getProperty("my-mall.probe.loginId", "3");
        try {
            SaSession session = StpUtil.getSessionByLoginId(loginId, false);
            Object adminInfo = (session == null) ? null : session.get("adminInfo");
            log.info("[契约自检] session({}) 读取：adminInfo={}", loginId,
                    adminInfo == null ? "null（会话不存在/已过期）" : adminInfo.getClass().getName());
            if (adminInfo instanceof UserDto) {
                UserDto dto = (UserDto) adminInfo;
                log.info("[契约自检] UserDto 还原成功：id={} username={} clientId={} 权限数={}",
                        dto.getId(), dto.getUsername(), dto.getClientId(),
                        dto.getPermissionList() == null ? 0 : dto.getPermissionList().size());
            }
        } catch (Exception e) {
            log.error("[契约自检] Sa-Token 会话读取失败（多半是缺少兼容类 com.macro.mall.common.dto.UserDto）", e);
        }
    }
}