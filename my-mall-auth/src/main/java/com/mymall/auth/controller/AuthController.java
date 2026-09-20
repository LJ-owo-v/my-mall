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
@Controller
@RequestMapping("/auth")
@Tag(name = "AuthController", description = "统一认证授权接口")
public class AuthController {
    @Autowired
    private UmsAdminService adminService;

    @Autowired
    private UmsMemberService memberService;

    @Operation(summary = "登录以后返回token")
    @RequestMapping(value = "/login",method = RequestMethod.POST)
    @ResponseBody
    public CommonResult login(@RequestParam String clientId,
                              @RequestParam String username,
                              @RequestParam String password) {
        if(AuthConstant.ADMIN_CLIENT_ID.equals(clientId)) {
            UmsAdminLoginParam loginParam = new UmsAdminLoginParam();
            loginParam.setUsername(username);
            loginParam.setPassword(password);
            return adminService.login(loginParam);
        }else if(AuthConstant.PORTAL_CLIENT_ID.equals(clientId)){
            return memberService.login(username,password);
        }else {
            return CommonResult.failed("clientId不正确");
        }

    }



}
