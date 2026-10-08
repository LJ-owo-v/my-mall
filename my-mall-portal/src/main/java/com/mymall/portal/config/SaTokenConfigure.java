// my-mall-portal/src/main/java/com/mymall/portal/config/SaTokenConfigure.java
package com.mymall.portal.config;

import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpLogic;
import com.mymall.portal.util.StpMemberUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SaTokenConfigure {
    /** 会员登录态必须是 JWT（与老服务一致：载荷 loginType=memberLogin） */
    @Bean
    public StpLogic getStpLogicMember() {
        return new StpLogicJwtForSimple(StpMemberUtil.TYPE);
    }
}