package com.mymall.user.service;

import com.mymall.user.domain.UmsMenuNode;
import com.mymall.user.model.UmsMenu;

import java.util.List;

/**
 * 后台菜单管理 Service —— 与原项目 com.macro.mall.service.UmsMenuService 的 7 个方法逐个对齐。
 */
public interface UmsMenuService {

    int create(UmsMenu umsMenu);

    int update(Long id, UmsMenu umsMenu);

    UmsMenu getItem(Long id);

    int delete(Long id);

    List<UmsMenu> list(Long parentId, Integer pageSize, Integer pageNum);

    /** 树形结构返回所有菜单（/menu/treeList） */
    List<UmsMenuNode> treeList();

    /** 修改菜单显示状态（/menu/updateHidden/{id}） */
    int updateHidden(Long id, Integer hidden);
}