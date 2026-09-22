package com.mymall.user.controller;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.hutool.core.collection.CollUtil;
import com.github.pagehelper.PageInfo;
import com.mymall.common.api.CommonPage;
import com.mymall.common.api.CommonResult;
import com.mymall.user.domain.UmsAdminLoginParam;
import com.mymall.user.domain.UmsAdminParam;
import com.mymall.user.domain.UpdateAdminPasswordParam;
import com.mymall.user.model.UmsAdmin;
import com.mymall.user.model.UmsRole;
import com.mymall.user.service.UmsAdminService;
import com.mymall.user.service.UmsRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * 12个接口
 */
@Controller
@Tag(name = "AdminController", description = "后台用户管理")
@RequestMapping("/admin")
public class AdminController {
    @Autowired
    private UmsAdminService adminService;

    @Autowired
    private UmsRoleService roleService;

    @Value("${sa-token.token-prefix}")
    private String tokenHead;


    private static final String DEFAULT_PAGE_SIZE = "10";
    private static final String DEFAULT_PAGE_NUM = "1";


    //注册后台用户
    @Operation(summary = "注册后台用户")
    @RequestMapping(value = "/register", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<UmsAdmin> register(@Validated @RequestBody UmsAdminParam umsAdminParam) {
        UmsAdmin umsAdmin = adminService.register(umsAdminParam);
        if (umsAdmin == null){
            return CommonResult.failed();
        }
        return CommonResult.success(umsAdmin);
    }
    //登入后返回Token
    @Operation(summary = "登入后返回Token")
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<Map<String,String>> login(@Validated @RequestBody
                                                  UmsAdminLoginParam umsAdminLoginParam) {
        SaTokenInfo saTokenInfo = adminService.login(umsAdminLoginParam.getUsername(), umsAdminLoginParam.getPassword());
        if (saTokenInfo == null){
            return CommonResult.validateFailed("用户名或密码错误");
        }
        Map<String,String> tokenMap = new HashMap<>();
        tokenMap.put("token",saTokenInfo.getTokenValue());
        // ★ tokenHead 必须带一个空格：Bearer + " " = "Bearer "
        tokenMap.put("tokenHead",tokenHead+" ");
        return CommonResult.success(tokenMap);
    }
    //获取登入用户信息
    @Operation(summary = "获取登入用户信息")
    @RequestMapping(value = "/info", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<Map<String, Object>> getAdminInfo() {
        // 1. 从 Sa-Token 会话中获取当前登录用户的 ID，再查出完整的 UmsAdmin 信息
        UmsAdmin umsAdmin = adminService.getCurrentAdmin();

        // 2. 组装返回数据
        Map<String, Object> data = new HashMap<>();
        // 2.1 用户名
        data.put("username", umsAdmin.getUsername());
        // 2.2 根据用户 ID 查出他能看到的后台菜单树（前端侧边栏用）
        data.put("menus", roleService.getMenuList(umsAdmin.getId()));
        // 2.3 用户头像 URL
        data.put("icon", umsAdmin.getIcon());

        // 2.4 查出用户的角色列表
        List<UmsRole> roleList = adminService.getRoleList(umsAdmin.getId());
        if (CollUtil.isNotEmpty(roleList)) {
            // 把角色对象列表提取成角色名称字符串列表，前端用这个做权限标识
            List<String> roles = roleList.stream()
                    .map(UmsRole::getName)
                    .collect(Collectors.toList());
            data.put("roles", roles);
        }

        // 3. 返回给前端
        return CommonResult.success(data);
    }

    //登出功能
    @Operation(summary = "登出功能")
    @RequestMapping(value = "/logout", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<Object> logout() {
        adminService.logout();
        return CommonResult.success("登出成功");
    }

    //获取用户列表（带可选模糊搜索条件的分页列表接口）
    @Operation(summary = "根据用户名或姓名分页获取用户列表")  // Swagger 接口文档描述
    @RequestMapping(value = "/list", method = RequestMethod.GET)  // GET /admin/list
    @ResponseBody
    public CommonResult<CommonPage<UmsAdmin>> list(
            @RequestParam(value = "keyword", required = false) String keyword,       // 搜索关键词，不传则查全部
            @RequestParam(value = "pageSize", defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,  // 每页条数，默认值
            @RequestParam(value = "pageNum", defaultValue = DEFAULT_PAGE_NUM) Integer pageNum      // 第几页，默认值
    ) {
        // 1. 调 Service 执行分页查询（PageHelper 已在 Service 里加了 LIMIT）
        List<UmsAdmin> adminList = adminService.list(keyword, pageSize, pageNum);

        // 2. PageInfo 从分页结果中提取总条数、总页数等信息
        PageInfo<UmsAdmin> pageInfo = new PageInfo<>(adminList);

        // 3. 组装统一的分页返回结构
        //    CommonPage 包含：pageNum / pageSize / totalPage / total / list
        CommonPage<UmsAdmin> page = CommonPage.of(
                pageInfo.getPageNum(),
                pageInfo.getPageSize(),
                pageInfo.getTotal(),
                adminList
        );

        return CommonResult.success(page);
    }

    //获取指定用户信息
    @Operation(summary = "获取指定用户信息")
    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<UmsAdmin> getItem(@PathVariable Long id) {
        UmsAdmin admin = adminService.getItem(id);
        return CommonResult.success(admin);
    }

    //修改指定用户信息
    @Operation(summary = "修改指定用户信息")
    @RequestMapping(value = "/update/{id}", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<Integer> update(@PathVariable Long id,
                                        @RequestBody UmsAdmin admin) {
        int count = adminService.update(id, admin);
        if(count > 0){
            return CommonResult.success(count);
        }
        return CommonResult.failed();
    }
    //修改指定用户密码
    @Operation(summary = "修改指定用户密码")  // Swagger 接口文档描述
    @RequestMapping(value = "/updatePassword", method = RequestMethod.POST)  // POST /admin/updatePassword
    @ResponseBody
    public CommonResult<Integer> updatePassword(
            @Validated @RequestBody UpdateAdminPasswordParam updatePasswordParam  // 接收旧密码+新密码，触发参数校验
    ) {
        // 1. 调 Service 执行密码修改，Service 内部从 Sa-Token 会话取当前用户 id，不需要前端传
        int status = adminService.updatePassword(updatePasswordParam);

        // 2. 根据 Service 返回的状态码返回不同结果
        if (status > 0) {
            return CommonResult.success(status);          // 修改成功
        } else if (status == -1) {
            return CommonResult.failed("提交参数不合法");  // 旧密码或新密码为空
        } else if (status == -2) {
            return CommonResult.failed("找不到该用户");    // 会话里的 id 查不到用户
        } else if (status == -3) {
            return CommonResult.failed("旧密码错误");      // 输入的旧密码和数据库不匹配
        } else {
            return CommonResult.failed();                   // 其他未知错误
        }
    }

    //给用户分配角色(可以有多个角色)
    @Operation(summary = "给用户分配角色")
    @RequestMapping(value = "/role/update", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<Integer> updateRole(@RequestParam("adminId") Long adminId,
                                            @RequestParam("roleIds") List<Long> roleIds) {
        int count = adminService.updateRole(adminId, roleIds);
        if (count >= 0) {
            return CommonResult.success(count);
        }
        return CommonResult.failed();
    }

    //获取指定用户的角色
    @Operation(summary = "获取指定用户的角色")
    @RequestMapping(value = "/role/{adminId}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<UmsRole>> getRoleList(@PathVariable Long adminId) {
        List<UmsRole> roleList = adminService.getRoleList(adminId);
        return CommonResult.success(roleList);
    }



}
