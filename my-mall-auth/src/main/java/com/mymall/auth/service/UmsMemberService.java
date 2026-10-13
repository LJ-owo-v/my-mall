package com.mymall.auth.service;



import com.mymall.common.api.CommonResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 前台会员服务远程调用Service
 * 后期改my-mall-portal
 */
@FeignClient("my-mall-portal")
public interface UmsMemberService {
    @PostMapping("/sso/login")
    CommonResult login(@RequestParam("username") String username,
                       @RequestParam("password") String password);

}
