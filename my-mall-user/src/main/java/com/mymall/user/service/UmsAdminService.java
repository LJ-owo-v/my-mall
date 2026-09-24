package com.mymall.user.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.mymall.user.domain.UmsAdminParam;
import com.mymall.user.domain.UpdateAdminPasswordParam;
import com.mymall.user.model.UmsAdmin;
import com.mymall.user.model.UmsResource;
import com.mymall.user.model.UmsRole;

import java.util.List;

public interface UmsAdminService  {
    UmsAdmin getAdminByUsername(String username);
    UmsAdmin register(UmsAdminParam umsAdminParam);
    SaTokenInfo login(String username, String password);

    /** 获取指定用户的可访问资源（登录时算 permissionList） */
    List<UmsResource> getResourceList(Long adminId);


    List<UmsRole> getRoleList(Long id);

    UmsAdmin getCurrentAdmin();

    void logout();

    List<UmsAdmin> list(String keyword, Integer pageSize, Integer pageNum);

    UmsAdmin getItem(Long id);

    int update(Long id, UmsAdmin admin);

    int updatePassword(UpdateAdminPasswordParam updatePasswordParam);

    int updateRole(Long adminId, List<Long> roleIds);

    /** 删除指定用户（/admin/delete/{id}） */
    int delete(Long id);
}
