package com.mymall.user.controller;

import com.github.pagehelper.PageInfo;
import com.mymall.common.api.CommonPage;
import com.mymall.common.api.CommonResult;
import com.mymall.user.domain.UmsMenuNode;
import com.mymall.user.model.UmsMenu;
import com.mymall.user.service.UmsMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台菜单管理 Controller —— 复刻原项目 UmsMenuController 的 7 个接口。
 *
 * 与原项目的唯一实现差异：分页。
 *   原项目 CommonPage.restPage(list) → 我的 common-core 只有 of(...)，所以用 PageInfo 取值再 of(...)，
 *   输出 5 个字段（pageNum/pageSize/totalPage/total/list）完全一致。
 */
@Controller
@Tag(name = "UmsMenuController", description = "后台菜单管理")
@RequestMapping("/menu")
public class UmsMenuController {

    /** 默认分页参数：必须与原项目一致（pageSize=5 / pageNum=1） */
    private static final String DEFAULT_PAGE_SIZE = "5";
    private static final String DEFAULT_PAGE_NUM = "1";

    @Autowired
    private UmsMenuService menuService;

    @Operation(summary = "添加后台菜单")
    @RequestMapping(value = "/create", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult create(@RequestBody UmsMenu umsMenu) {
        int count = menuService.create(umsMenu);
        if (count > 0) {
            return CommonResult.success(count);
        } else {
            return CommonResult.failed();
        }
    }

    @Operation(summary = "修改后台菜单")
    @RequestMapping(value = "/update/{id}", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult update(@PathVariable Long id,
                               @RequestBody UmsMenu umsMenu) {
        int count = menuService.update(id, umsMenu);
        if (count > 0) {
            return CommonResult.success(count);
        } else {
            return CommonResult.failed();
        }
    }

    @Operation(summary = "根据ID获取菜单详情")
    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<UmsMenu> getItem(@PathVariable Long id) {
        UmsMenu umsMenu = menuService.getItem(id);
        return CommonResult.success(umsMenu);
    }

    @Operation(summary = "根据ID删除后台菜单")
    @RequestMapping(value = "/delete/{id}", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult delete(@PathVariable Long id) {
        int count = menuService.delete(id);
        if (count > 0) {
            return CommonResult.success(count);
        } else {
            return CommonResult.failed();
        }
    }

    @Operation(summary = "分页查询后台菜单")
    @RequestMapping(value = "/list/{parentId}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<CommonPage<UmsMenu>> list(@PathVariable Long parentId,
                                                  @RequestParam(value = "pageSize", defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
                                                  @RequestParam(value = "pageNum", defaultValue = DEFAULT_PAGE_NUM) Integer pageNum) {
        List<UmsMenu> menuList = menuService.list(parentId, pageSize, pageNum);
        PageInfo<UmsMenu> pageInfo = new PageInfo<>(menuList);
        return CommonResult.success(CommonPage.of(pageInfo.getPageNum(), pageInfo.getPageSize(),
                pageInfo.getTotal(), pageInfo.getList()));
    }

    @Operation(summary = "树形结构返回所有菜单列表")
    @RequestMapping(value = "/treeList", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<UmsMenuNode>> treeList() {
        List<UmsMenuNode> list = menuService.treeList();
        return CommonResult.success(list);
    }

    @Operation(summary = "修改菜单显示状态")
    @RequestMapping(value = "/updateHidden/{id}", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult updateHidden(@PathVariable Long id, @RequestParam("hidden") Integer hidden) {
        int count = menuService.updateHidden(id, hidden);
        if (count > 0) {
            return CommonResult.success(count);
        } else {
            return CommonResult.failed();
        }
    }
}