package com.mymall.gateway.config;

import cn.dev33.satoken.context.SaHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.mymall.common.api.CommonResult;
import com.mymall.common.redis.MyMallBaseRedisConfig;
import com.mymall.gateway.util.StpMemberUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 网关鉴权核心 —— 与原项目 config/SaTokenConfig 机制一致（用 Sa-Token 官方 SaReactorFilter）：
 *
 *   1) 白名单放行（secure.ignore.urls，与原项目同一份）
 *   2) OPTIONS 预检直接放行
 *   3) 双登录体系：/mall-portal/** → 会员 StpLogic(memberLogin)；/mall-admin/** → StpUtil(login)
 *   4) 接口级权限：Redis hash auth:pathResourceMap（路径→所需权限）匹配后 checkPermissionOr
 *      —— 权限列表由 component/StpInterfaceImpl 从 Sa-Token session 的 adminInfo 提供
 *   5) 异常统一转 CommonResult（与原项目返回结构、响应头一致）
 */
@Configuration
public class SaTokenConfig {

    private static final Logger log = LoggerFactory.getLogger(SaTokenConfig.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Sa-Token session 里存后台管理员信息的 key（定义收敛到 AuthConstant） */
    public static final String STP_ADMIN_INFO = com.mymall.common.constant.AuthConstant.STP_ADMIN_INFO;
    /** 会员信息 key（同上） */
    public static final String STP_MEMBER_INFO = com.mymall.common.constant.AuthConstant.STP_MEMBER_INFO;

    private final IgnoreUrlsProperties ignoreUrlsProperties;
    private final RedisTemplate<String, Object> redisTemplate;

    public SaTokenConfig(IgnoreUrlsProperties ignoreUrlsProperties,
                         RedisTemplate<String, Object> redisTemplate) {
        this.ignoreUrlsProperties = ignoreUrlsProperties;
        this.redisTemplate = redisTemplate;
    }

    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                // 拦截所有路径
                .addInclude("/**")
                // 白名单（Sa-Token 内部按原始请求路径匹配）
                .setExcludeList(ignoreUrlsProperties.getUrls())
                .setAuth(obj -> {
                    // ① 跨域预检直接放行
                    SaRouter.match(SaHttpMethod.OPTIONS).stop();

                    // ② 前台会员体系（loginType = memberLogin）
                    SaRouter.match("/mall-portal/**", r -> StpMemberUtil.checkLogin()).stop();

                    // ③ 后台管理体系（loginType = login）
                    SaRouter.match("/mall-admin/**", r -> StpUtil.checkLogin());

                    // ④ 接口级权限：路径 → 所需权限，命中则校验（拥有任意一个即可）
                    List<String> needPermissionList = matchPermission(
                            SaHolder.getRequest().getRequestPath());
                    if (!needPermissionList.isEmpty()) {
                        SaRouter.match(SaHolder.getRequest().getRequestPath(),
                                r -> StpUtil.checkPermissionOr(needPermissionList.toArray(new String[0])));
                    }
                })
                .setError(this::handleException);
    }

    /** 从 Redis 权限映射表里找出当前路径需要的权限码 */
    private List<String> matchPermission(String requestPath) {
        List<String> needPermissionList = new ArrayList<>();
        try {
            Map<Object, Object> pathResourceMap = redisTemplate.opsForHash()
                    .entries(MyMallBaseRedisConfig.PATH_RESOURCE_MAP);
            PathMatcher pathMatcher = new AntPathMatcher();
            for (Map.Entry<Object, Object> entry : pathResourceMap.entrySet()) {
                String pattern = String.valueOf(entry.getKey());
                if (pathMatcher.match(pattern, requestPath)) {
                    needPermissionList.add(String.valueOf(entry.getValue()));
                }
            }
        } catch (Exception e) {
            log.error("读取权限映射表失败，本次跳过接口级权限校验: {}", e.getMessage());
        }
        return needPermissionList;
    }

    /**
     * 鉴权异常统一响应。
     *
     * 注意：Sa-Token 的 setError 策略返回普通对象时**不会自动 JSON 序列化**，
     * 写出去的是对象的 toString()（会变成 CommonResult@1a2b3c 这种垃圾）。
     * 所以这里显式序列化成 JSON 字符串再返回。
     */
    private Object handleException(Throwable e) {
        SaHolder.getResponse()
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setHeader("Access-Control-Allow-Origin", "*")
                .setHeader("Cache-Control", "no-cache");
        CommonResult<?> result;
        if (e instanceof NotLoginException) {
            result = CommonResult.unauthorized(null);
        } else if (e instanceof NotPermissionException) {
            result = CommonResult.forbidden(null);
        } else {
            log.warn("网关鉴权异常: {}", e.getMessage());
            result = CommonResult.failed(e.getMessage());
        }
        try {
            return MAPPER.writeValueAsString(result);
        } catch (Exception ex) {
            return "{\"code\":500,\"message\":\"serialize error\",\"data\":null}";
        }
    }
}