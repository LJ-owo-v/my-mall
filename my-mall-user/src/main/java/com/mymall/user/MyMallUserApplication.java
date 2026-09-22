package com.mymall.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 用户与权限服务（8086）—— 接管原 mall-admin 的 /admin/** 接口。
 *
 * scanBasePackages = "com.mymall"：为了让 common-web 里的
 * GlobalExceptionHandler / TraceIdFilter 等 Bean 被扫到（D4 会补进去）。
 */
@SpringBootApplication(scanBasePackages = "com.mymall")
@EnableDiscoveryClient
@MapperScan("com.mymall.user.dao")
public class MyMallUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyMallUserApplication.class, args);
    }
}