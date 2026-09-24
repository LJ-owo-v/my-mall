package com.mymall.user.controller;

import com.github.pagehelper.PageInfo;
import com.mymall.common.api.CommonPage;
import com.mymall.common.api.CommonResult;
import com.mymall.user.model.UmsMenu;
import com.mymall.user.model.UmsResource;
import com.mymall.user.service.UmsResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 后台资源管理Controller
 */
@Controller
@Tag(name = "UmsResourceController", description = "后台资源管理")
@RequestMapping("/resource")
public class UmsResourceController {

    private static final String DEFAULT_PAGE_SIZE = "5";
    private static final String DEFAULT_PAGE_NUM = "1";

    @Autowired
    private UmsResourceService resourceService;

    @Operation(summary = "添加后台资源")
    @RequestMapping(value = "/create", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult create(@RequestBody UmsResource umsResource) {
        int count = resourceService.create(umsResource);
        if (count > 0) {
            return CommonResult.success(count);
        } else {
            return CommonResult.failed();
        }
    }

    @Operation(summary = "修改后台资源")
    @RequestMapping(value = "/update/{id}", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult update(@PathVariable Long id,
                               @RequestBody UmsResource umsResource) {
        int count = resourceService.update(id, umsResource);
        if (count > 0) {
            return CommonResult.success(count);
        } else {
            return CommonResult.failed();
        }
    }

    @Operation(summary = "根据ID获取资源详情")
    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<UmsResource> getItem(@PathVariable Long id) {
        UmsResource umsResource = resourceService.getItem(id);
        return CommonResult.success(umsResource);
    }
    @Operation(summary = "根据ID删除后台资源")
    @RequestMapping(value = "/delete/{id}", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult delete(@PathVariable Long id) {
        int count = resourceService.delete(id);
        if (count > 0) {
            return CommonResult.success(count);
        } else {
            return CommonResult.failed();
        }
    }

    @Operation(summary = "分页模糊查询后台资源")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<CommonPage<UmsResource>> list(@RequestParam(required = false) Long categoryId,
                                                      @RequestParam(required = false) String nameKeyword,
                                                      @RequestParam(required = false) String urlKeyword,
                                                      @RequestParam(value = "pageSize", defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
                                                      @RequestParam(value = "pageNum", defaultValue = DEFAULT_PAGE_NUM) Integer pageNum) {
        List<UmsResource> resourceList = resourceService.list(categoryId,nameKeyword, urlKeyword, pageSize, pageNum);
        PageInfo<UmsResource> pageInfo = new PageInfo<>(resourceList);
        return CommonResult.success(CommonPage.of(pageInfo.getPageNum(), pageInfo.getPageSize(), pageInfo.getTotal(), pageInfo.getList()));
    }

    @Operation(summary = "查询所有后台资源")
    @RequestMapping(value = "/listAll", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<UmsResource>> listAll() {
        List<UmsResource> resourceList = resourceService.listAll();
        return CommonResult.success(resourceList);
    }
    @Operation(summary = "初始化路径和资源的关联数据")
    @RequestMapping(value = "/initPathResourceMap", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult initPathResourceMap() {
        Map<String, String> pathResourceMap = resourceService.initPathResourceMap();
        return CommonResult.success(pathResourceMap);
    }

}
