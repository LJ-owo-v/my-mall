package com.mymall.portal.service.impl;

import com.mymall.common.redis.RedisService;
import com.mymall.portal.model.UmsMember;
import com.mymall.portal.service.UmsMemberCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 会员信息缓存 / 验证码缓存
 *
 * <p>key 规范（必须与 mall-swarm 一致，否则新老服务互相读不到）：
 * <pre>
 *   会员信息：{redis.database}:{redis.key.member}:{memberId}     例 mall-swarm:ums:member:3
 *   验证码　：{redis.database}:{redis.key.authCode}:{telephone}  例 mall-swarm:ums:authCode:136xxxxxxxx
 * </pre>
 *
 * <p>过期策略：会员信息用 redis.expire.common（24 小时），验证码用 redis.expire.authCode（90 秒）。
 * 会员信息发生变更时必须调 {@link #delMember(Long)}，否则下次查询会读到旧值。
 */
@Service
public class UmsMemberCacheServiceImpl implements UmsMemberCacheService {

    private static final Logger log = LoggerFactory.getLogger(UmsMemberCacheServiceImpl.class);

    @Autowired
    private RedisService redisService;
    @Value("${redis.database}")
    private String REDIS_DATABASE;
    @Value("${redis.expire.common}")
    private Long REDIS_EXPIRE;
    @Value("${redis.expire.authCode}")
    private Long REDIS_EXPIRE_AUTH_CODE;
    @Value("${redis.key.member}")
    private String REDIS_KEY_MEMBER;
    @Value("${redis.key.authCode}")
    private String REDIS_KEY_AUTH_CODE;

    /**
     * 会员信息缓存 key：{redis.database}:{redis.key.member}:{memberId}
     */
    private String memberKey(Long memberId) {
        return REDIS_DATABASE + ":" + REDIS_KEY_MEMBER + ":" + memberId;
    }

    /**
     * 验证码缓存 key：{redis.database}:{redis.key.authCode}:{telephone}
     */
    private String authCodeKey(String telephone) {
        return REDIS_DATABASE + ":" + REDIS_KEY_AUTH_CODE + ":" + telephone;
    }

    @Override
    public void delMember(Long memberId) {
        redisService.del(memberKey(memberId));
    }

    @Override
    public UmsMember getMember(Long memberId) {
        if (memberId == null) {
            return null;
        }
        String key = memberKey(memberId);
        try {
            Object value = redisService.get(key);
            // 命中但类型不对（例如被别的结构写进了同名 key）也按未命中处理
            return value instanceof UmsMember ? (UmsMember) value : null;
        } catch (Exception e) {
            // 迁移期新老服务共用同一个 Redis 实例：老系统写入的值带的是 com.macro.* 的 @class，
            // 在 com.mymall.* 下反序列化会抛异常而不是返回 null。
            // 这里按“未命中”降级并清掉脏 key，避免每个请求都走一遍异常、刷一条错误日志。
            log.warn("[memberCache] 反序列化失败，按未命中处理并清除脏 key={}", key, e);
            redisService.del(key);
            return null;
        }
    }

    @Override
    public void setMember(UmsMember member) {
        if (member == null || member.getId() == null) {
            return;
        }
        redisService.set(memberKey(member.getId()), member, REDIS_EXPIRE);
    }

    @Override
    public void setAuthCode(String telephone, String authCode) {
        redisService.set(authCodeKey(telephone), authCode, REDIS_EXPIRE_AUTH_CODE);
    }

    @Override
    public String getAuthCode(String telephone) {
        Object value = redisService.get(authCodeKey(telephone));
        return value == null ? null : value.toString();
    }
}
