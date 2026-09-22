package com.mymall.auth.controller;

import com.mymall.common.constant.AuthConstant;
import com.mymall.auth.domain.UmsAdminLoginParam;
import com.mymall.auth.service.UmsAdminService;
import com.mymall.auth.service.UmsMemberService;
import com.mymall.common.api.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * 统一认证授权接口
 */
@Controller                              // 普通 MVC 控制器（方法上需配 @ResponseBody 才返回 JSON）
@RequestMapping("/auth")                 // 本控制器所有接口前缀：/auth/**
@Tag(name = "AuthController", description = "统一认证授权接口")   // Knife4j 接口文档分组
public class AuthController {

    // 后台登录服务（Feign：远程调 mall-admin 的登录逻辑）
    @Autowired
    private UmsAdminService adminService;

    // 前台会员登录服务（Feign：远程调 mall-portal 的登录逻辑）
    @Autowired
    private UmsMemberService memberService;

    /**
     * 统一登录入口：靠 clientId 区分是后台还是前台，转发给对应服务
     * 请求方式：POST /auth/login?clientId=admin-app&username=xxx&password=xxx
     * 注意：参数是 @RequestParam（表单/Query），不是 JSON body
     */
    @Operation(summary = "登录以后返回token")   // Knife4j 文档说明
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    @ResponseBody                            // 返回值序列化成 JSON（有了它方法不用返回视图名）
    public CommonResult login(@RequestParam String clientId,   // 调用方标识：admin-app / portal-app
                              @RequestParam String username,   // 用户名
                              @RequestParam String password) { // 明文密码，HTTPS 下传输

        // 分支①：后台管理员登录 → 组装原项目的登录参数对象，交给 adminService
        if (AuthConstant.ADMIN_CLIENT_ID.equals(clientId)) {
            UmsAdminLoginParam loginParam = new UmsAdminLoginParam();
            loginParam.setUsername(username);
            loginParam.setPassword(password);
            return adminService.login(loginParam);          // 内部 Feign 调 mall-admin，返回带 token 的 CommonResult

            // 分支②：前台会员登录 → 直接两个参数透传给 memberService
        } else if (AuthConstant.PORTAL_CLIENT_ID.equals(clientId)) {
            return memberService.login(username, password);  // 内部 Feign 调 mall-portal

            // 分支③：clientId 不认识 → 失败返回
        } else {
            return CommonResult.failed("clientId不正确");
        }
    }
}
