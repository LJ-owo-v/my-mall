package com.mymall.user.service.impl;

import com.mymall.common.redis.RedisService;
import com.mymall.user.model.UmsAdmin;
import com.mymall.user.service.UmsAdminCacheService;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UmsAdminCacheServiceImpl implements UmsAdminCacheService {

    @Autowired  // 注入 RedisService（common-redis 里封装的 Redis 操作工具）
    private RedisService redisService;

    @Value("${redis.database}")  // 从配置文件读 redis.database，如 "mall"
    private String REDIS_DATABASE;

    @Value("${redis.expire.common}")  // 从配置文件读通用过期时间，如 86400（秒）
    private Long REDIS_EXPIRE;

    @Value("${redis.key.admin}")  // 从配置文件读用户缓存的 key 前缀，如 "ums:admin"
    private String REDIS_KEY_ADMIN;

    // ========== 查缓存 ==========
    @Override
    public UmsAdmin getAdmin(Long id) {
        // 拼接 key：mall:ums:admin:{id}
        String key = REDIS_DATABASE + ":" + REDIS_KEY_ADMIN + ":" + id;
        // 从 Redis 取值并强转为 UmsAdmin（缓存没有时返回 null）
        return (UmsAdmin) redisService.get(key);
    }

    // ========== 写缓存 ==========
    @Override
    public void setAdmin(UmsAdmin admin) {
        // 拼接 key：mall:ums:admin:{adminId}
        String key = REDIS_DATABASE + ":" + REDIS_KEY_ADMIN + ":" + admin.getId();
        // 写入 Redis 并设置过期时间（REDIS_EXPIRE 秒后自动删除，避免脏数据常驻）
        redisService.set(key, admin, REDIS_EXPIRE);
    }

    // ========== 删缓存 ==========
    @Override
    public void delAdmin(Long id) {
        // 拼接 key：mall:ums:admin:{id}
        String key = REDIS_DATABASE + ":" + REDIS_KEY_ADMIN + ":" + id;
        // 删除缓存（用户信息修改或登出时调用，保证下次查询从数据库加载最新数据）
        redisService.del(key);
    }
}