package com.mymall.user.dao;

import com.mymall.user.model.UmsAdminRoleRelation;
import com.mymall.user.model.UmsResource;
import com.mymall.user.model.UmsRole;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 自定义后台用户角色关系 Dao（MBG 生成不出来的 join SQL，XML 在 resources/dao/UmsAdminRoleRelationDao.xml）。
 *
 * ⚠️ 必须是 interface：MyBatis 靠"接口 + XML namespace"绑定；
 *    之前这里是 public class，Mapper 不会被注册，调接口会报 Invalid bound statement。
 */
public interface UmsAdminRoleRelationDao {

    /** 批量插入用户角色关系（updateRole 用） */
    int insertList(@Param("list") List<UmsAdminRoleRelation> list);

    /** 根据用户 id 获取其角色（admin_role → role） */
    List<UmsRole> getRoleList(@Param("adminId") Long adminId);

    /** 根据用户 id 获取其可访问资源（admin_role → role_resource → resource，登录时算 permissionList） */
    List<UmsResource> getResourceList(@Param("adminId") Long adminId);
}