package com.mymall.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 自研网关启动类。
 * D2 待补：TraceIdFilter、AuthGlobalFilter、GatewayRouteConfig（这才是 D2 的正主）
 */
@EnableDiscoveryClient
@SpringBootApplication
public class MyMallGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyMallGatewayApplication.class, args);
    }
}