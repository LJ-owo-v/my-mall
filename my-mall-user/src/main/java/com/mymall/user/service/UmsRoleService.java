package com.mymall.user.service;

import com.mymall.user.model.UmsMenu;

import java.util.List;

public interface UmsRoleService {
    /** 根据后台用户 ID 获取其可访问的菜单（/admin/info 的 menus 字段） */
    List<UmsMenu> getMenuList(Long adminId);
}
