package com.mymall.gateway.util;

import cn.dev33.satoken.stp.StpLogic;

/**
 * 前台会员登录体系 —— 薄封装替代原项目 33KB 的 StpUtil 手抄版。
 *
 * 关键：TYPE 必须与原项目一致（"memberLogin"），因为它参与 Redis key 的拼接：
 *   Authorization:memberLogin:session:{loginId}
 * 写错一个字母 → 读不到会员会话 → 前台带 token 的接口全部 401。
 */
public class StpMemberUtil {

    public static final String TYPE = "memberLogin";

    /** 一个自定义 loginType 的 StpLogic，等价于原项目手抄的那一整套静态方法 */
    public static final StpLogic stpLogic = new StpLogic(TYPE);

    public static void checkLogin() {
        stpLogic.checkLogin();
    }

    public static Object getLoginId() {
        return stpLogic.getLoginId();
    }

    public static String getTokenValue() {
        return stpLogic.getTokenValue();
    }

    public static boolean isLogin() {
        return stpLogic.isLogin();
    }
}