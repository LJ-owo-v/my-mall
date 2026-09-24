package com.mymall.user.service.impl;

import com.mymall.user.dao.UmsRoleDao;
import com.mymall.user.model.UmsMenu;
import com.mymall.user.service.UmsRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * 后台角色管理实现 —— D4 只有 /admin/info 需要的 getMenuList。
 * 实现直接委托给手写 DAO（admin_role → role → role_menu → menu，MBG 生成不出来）。
 */

@Service
public class UmsRoleServiceImpl implements UmsRoleService {
    @Autowired
    private UmsRoleDao roleDao;

    @Override
    public List<UmsMenu> getMenuList(Long adminId) {
        return roleDao.getMenuList(adminId);
    }
}
