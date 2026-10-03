package com.mymall.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 订单域服务（8089）：订单/发货/退货/订单设置/收货地址
 *
 * scanBasePackages = "com.mymall"：让 common-web 的 GlobalExceptionHandler / TraceIdFilter 生效。
 * 灰度时只加最具体的路径前缀，禁止切整个 /mall-admin/**（详见 AGENTS.md）。
 */
@SpringBootApplication(scanBasePackages = "com.mymall")
@EnableDiscoveryClient
@MapperScan("com.mymall.order.dao")
public class MyMallOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyMallOrderApplication.class, args);
    }
}