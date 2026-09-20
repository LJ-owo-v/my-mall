package com.mymall.common.redis;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置基类 —— 与 mall-swarm 的 BaseRedisConfig 设计一致：
 * 本类**不带 @Configuration**，由各服务在本地包下写一个 @Configuration 子类继承，例如：
 *   package com.mymall.gateway.config;
 *   @Configuration public class RedisConfig extends MyMallBaseRedisConfig { }
 *
 * 为什么要这样：@Configuration 类必须在服务的 component-scan 路径下才会生效，
 * 放在公共模块（com.mymall.common.*）里服务扫不到 —— 这是踩过的坑。
 *
 * 三个必须对齐的点：
 *   1) Bean 名/类型：redisTemplate : RedisTemplate&lt;String,Object&gt;（Sa-Token starter 按类型查找）
 *   2) 序列化：key=String，value/hashValue=Jackson2Json 且开启 default typing
 *   3) 常量：权限映射表 key = auth:pathResourceMap（与原项目 AuthConstant 一致）
 */
public class MyMallBaseRedisConfig {

    /**
     * 权限映射表在 Redis 里的 key。
     * 定义已收敛到 {@link com.mymall.common.constant.AuthConstant#PATH_RESOURCE_MAP}，
     * 这里保留同名常量作为别名（网关 SaTokenConfig 已在引用它），避免字面量两处漂移。
     */
    public static final String PATH_RESOURCE_MAP = com.mymall.common.constant.AuthConstant.PATH_RESOURCE_MAP;

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisSerializer<Object> serializer = redisSerializer();
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        // 与原项目一致：pathResourceMap 的值在 Redis 里是带引号的 JSON 串（如 "5:商品管理"）
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @SuppressWarnings("deprecation")
    public RedisSerializer<Object> redisSerializer() {
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 关键：开启默认类型信息（写出 "@class"，读回真实类型）
        // 不开 → pathResourceMap 的值退化成 LinkedHashMap；Sa-Token session 里的 UserDto 也无法还原
        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL);
        return new Jackson2JsonRedisSerializer<>(om, Object.class);
    }
}