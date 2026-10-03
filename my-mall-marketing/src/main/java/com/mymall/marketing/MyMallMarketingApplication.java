package com.mymall.marketing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 营销域服务（8088）：首页运营位/优惠券/秒杀活动与场次
 *
 * scanBasePackages = "com.mymall"：让 common-web 的 GlobalExceptionHandler / TraceIdFilter 生效。
 * 灰度时只加最具体的路径前缀，禁止切整个 /mall-admin/**（详见 AGENTS.md）。
 */
@SpringBootApplication(scanBasePackages = "com.mymall")
@EnableDiscoveryClient
@MapperScan("com.mymall.marketing.dao")
public class MyMallMarketingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyMallMarketingApplication.class, args);
    }
}