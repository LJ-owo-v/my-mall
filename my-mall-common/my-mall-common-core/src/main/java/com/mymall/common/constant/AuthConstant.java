package com.mymall.common.constant;

/**
 * 认证/权限相关常量 —— 取值与原项目 com.macro.mall.common.constant.AuthConstant **逐字一致**。
 *
 * 为什么不需要像 UserDto 那样对齐包名：
 *   这些都是 static final String，属于「编译期常量」，会被直接内联进调用方的字节码，
 *   运行时根本不加载这个类，也就不存在序列化/反序列化的类型匹配问题。
 *   （UserDto 会被 Jackson 带 @class 写进 Redis session，所以那个必须用原包名。）
 *
 * 使用方式（auth 的 Controller）：
 *   import com.mymall.common.constant.AuthConstant;
 *   if (AuthConstant.ADMIN_CLIENT_ID.equals(clientId)) { ... }
 */
public interface AuthConstant {

    /** 后台管理 client_id */
    String ADMIN_CLIENT_ID = "admin-app";

    /** 前台商城 client_id */
    String PORTAL_CLIENT_ID = "portal-app";

    /** 后台管理接口路径匹配 */
    String ADMIN_URL_PATTERN = "/mall-admin/**";

    /** Redis 里"路径 → 所需权限"的映射表（hash 结构） */
    String PATH_RESOURCE_MAP = "auth:pathResourceMap";

    /** 认证信息请求头 */
    String JWT_TOKEN_HEADER = "Authorization";

    /** 令牌前缀（注意末尾有一个空格，与原项目一致） */
    String JWT_TOKEN_PREFIX = "Bearer ";

    /** 用户信息请求头 */
    String USER_TOKEN_HEADER = "user";

    /** Sa-Token session 中存储的会员信息 key */
    String STP_MEMBER_INFO = "memberInfo";

    /** Sa-Token session 中存储的后台管理员信息 key */
    String STP_ADMIN_INFO = "adminInfo";

    /** 权限前缀（原项目保留字段） */
    String AUTHORITY_PREFIX = "ROLE_";

    /** 权限属性名（原项目保留字段） */
    String AUTHORITY_CLAIM_NAME = "authorities";
}