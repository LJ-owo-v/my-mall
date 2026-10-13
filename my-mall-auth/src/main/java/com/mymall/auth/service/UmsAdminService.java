package com.mymall.auth.service;



import com.mymall.auth.domain.UmsAdminLoginParam;
import com.mymall.common.api.CommonResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 后台用户管理Service
 * 后期改my-mall-user
 */
@FeignClient("my-mall-user")
public interface UmsAdminService {

    @PostMapping("/admin/login")
    CommonResult login(@RequestBody UmsAdminLoginParam param);
}

