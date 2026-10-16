package com.mymall.gateway.component;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.mymall.common.dto.UserDto;
import com.mymall.gateway.config.SaTokenConfig;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 权限列表来源（与原项目 component/StpInterfaceImpl 行为一致）：
 * 后台用户的权限列表在登录时就写进了 Sa-Token session 的 adminInfo（UserDto.permissionList），
 * 这里把它交给 Sa-Token，供 checkPermissionOr 使用；会员体系不做接口级权限，返回 null。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        if (StpUtil.getLoginType().equals(loginType)) {
            UserDto userDto = (UserDto) StpUtil.getSession().get(SaTokenConfig.STP_ADMIN_INFO);
            return userDto == null ? null : userDto.getPermissionList();
        }
        return null;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return null;
    }
}