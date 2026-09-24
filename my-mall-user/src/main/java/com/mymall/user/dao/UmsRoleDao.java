package com.mymall.user.dao;

import com.mymall.user.model.UmsMenu;
import com.mymall.user.model.UmsResource;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 自定义后台角色管理 Dao（XML 在 resources/dao/UmsRoleDao.xml）。
 *
 * ⚠️ D4 只需要 getMenuList：/admin/info 的 menus 字段靠它。
 *    原项目还有 getMenuListByRoleId / getResourceListByRoleId（D5 的 /role/**、/menu/** 才用），
 *    到时候再加方法 + 补 XML 即可。
 */
public interface UmsRoleDao {

    /** 根据后台用户 id 获取其可访问菜单（admin_role → role → role_menu → menu） */
    List<UmsMenu> getMenuList(@Param("adminId") Long adminId);

    // UmsRoleDao.java 追加
    List<UmsMenu> getMenuListByRoleId(@Param("roleId") Long roleId);        // /role/listMenu/{roleId}
    List<UmsResource> getResourceListByRoleId(@Param("roleId") Long roleId); // /role/listResource/{roleId}
}