package com.mymall.user.service;

import com.mymall.user.model.UmsMenu;
import com.mymall.user.model.UmsResource;
import com.mymall.user.model.UmsRole;

import java.util.List;

public interface UmsRoleService {
    /** 根据后台用户 ID 获取其可访问的菜单（/admin/info 的 menus 字段） */
    List<UmsMenu> getMenuList(Long adminId);

    int create(UmsRole role);

    int update(Long id, UmsRole role);

    int delete(List<Long> ids);

    List<UmsRole> list();

    /** 根据角色名称分页查询（/role/list） */
    List<UmsRole> list(String keyword, Integer pageSize, Integer pageNum);

    List<UmsMenu> listMenu(Long roleId);

    List<UmsResource> listResource(Long roleId);

    int allocMenu(Long roleId, List<Long> menuIds);

    int allocResource(Long roleId, List<Long> resourceIds);
}
