package com.mymall.portal.config;

import com.mymall.common.redis.MyMallBaseRedisConfig;
import org.springframework.context.annotation.Configuration;

/**
 * 门户域 Redis 配置：必须存在，否则 common-redis 里的 RedisServiceImpl（带 @Service）
 * 会被组件扫描强制实例化，却找不到 RedisTemplate → 启动即崩（D4/D6/D7 同款坑）。
 */
@Configuration
public class RedisConfig extends MyMallBaseRedisConfig {
}