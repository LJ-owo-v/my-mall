package com.mymall.gateway.config;

import com.mymall.common.redis.MyMallBaseRedisConfig;
import org.springframework.context.annotation.Configuration;

/**
 * 网关本地 Redis 配置 —— 与原项目 gateway 的 RedisConfig(279B) 同样只做继承，
 * 好处：@Configuration 在网关自己的扫描路径下，基类里的 @Bean 方法会被正常注册。
 */
@Configuration
public class RedisConfig extends MyMallBaseRedisConfig {
}