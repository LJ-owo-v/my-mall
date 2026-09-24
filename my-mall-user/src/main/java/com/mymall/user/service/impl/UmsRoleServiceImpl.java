package com.mymall.user.service.impl;

import com.github.pagehelper.PageHelper;
import com.mymall.user.dao.UmsRoleDao;
import com.mymall.user.dao.UmsRoleMapper;
import com.mymall.user.dao.UmsRoleMenuRelationMapper;
import com.mymall.user.dao.UmsRoleResourceRelationMapper;
import com.mymall.user.model.*;
import com.mymall.user.service.UmsResourceService;
import com.mymall.user.service.UmsRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;


/**
 * 后台角色管理实现 —— D4 只有 /admin/info 需要的 getMenuList。
 * 实现直接委托给手写 DAO（admin_role → role → role_menu → menu，MBG 生成不出来）。
 */

@Service
public class UmsRoleServiceImpl implements UmsRoleService {
    @Autowired
    private UmsRoleMapper roleMapper;
    @Autowired
    private UmsRoleMenuRelationMapper roleMenuRelationMapper;
    @Autowired
    private UmsRoleResourceRelationMapper roleResourceRelationMapper;
    @Autowired
    private UmsRoleDao roleDao;
    @Autowired
    private UmsResourceService resourceService;

    @Override
    public List<UmsMenu> getMenuList(Long adminId) {
        return roleDao.getMenuList(adminId);
    }

    @Override
    public int create(UmsRole role) {
        role.setCreateTime(new Date());
        role.setAdminCount(0);
        role.setSort(0);
        return roleMapper.insert(role);
    }

    @Override
    public int update(Long id, UmsRole role) {
        role.setId(id);
        return roleMapper.updateByPrimaryKeySelective(role);
    }

    @Override
    public int delete(List<Long> ids) {
        // 1. 创建查询条件构造器
        UmsRoleExample example = new UmsRoleExample();
        // 2. 添加条件：id IN (ids)
        example.createCriteria().andIdIn(ids);
        // 3. 按条件批量删除角色
        int count = roleMapper.deleteByExample(example);
        // 4. ★ 重建权限映射表
        //    删除角色后，该角色关联的资源权限也失效了
        //    必须重新扫描所有资源，刷新 Redis 里的 auth:pathResourceMap
        //    否则网关还按旧的映射表校验，已删除角色的权限可能残留
        resourceService.initPathResourceMap();
        return count;
    }

    @Override
    public List<UmsRole> list() {
        return roleMapper.selectByExample(new UmsRoleExample());
    }

    @Override
    public List<UmsRole> list(String keyword, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum, pageSize);
        UmsRoleExample example = new UmsRoleExample();
        if (!StringUtils.isEmpty(keyword)) {
            example.createCriteria().andNameLike("%" + keyword + "%");
        }
        return roleMapper.selectByExample(example);
    }
    @Override
    public List<UmsMenu> listMenu(Long roleId) {
        return roleDao.getMenuListByRoleId(roleId);
    }

    @Override
    public List<UmsResource> listResource(Long roleId) {
        return roleDao.getResourceListByRoleId(roleId);
    }

    @Override
    public int allocMenu(Long roleId, List<Long> menuIds) {
        //先删除原有关系
        UmsRoleMenuRelationExample example=new UmsRoleMenuRelationExample();
        example.createCriteria().andRoleIdEqualTo(roleId);
        roleMenuRelationMapper.deleteByExample(example);
        //批量插入新关系
        for (Long menuId : menuIds) {
            UmsRoleMenuRelation relation = new UmsRoleMenuRelation();
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            roleMenuRelationMapper.insert(relation);
        }
        return menuIds.size();
    }

    @Override
    public int allocResource(Long roleId, List<Long> resourceIds) {
        //先删除原有关系
        UmsRoleResourceRelationExample example=new UmsRoleResourceRelationExample();
        example.createCriteria().andRoleIdEqualTo(roleId);
        roleResourceRelationMapper.deleteByExample(example);
        //批量插入新关系
        for (Long resourceId : resourceIds) {
            UmsRoleResourceRelation relation = new UmsRoleResourceRelation();
            relation.setRoleId(roleId);
            relation.setResourceId(resourceId);
            roleResourceRelationMapper.insert(relation);
        }
        resourceService.initPathResourceMap();
        return resourceIds.size();
    }

}
