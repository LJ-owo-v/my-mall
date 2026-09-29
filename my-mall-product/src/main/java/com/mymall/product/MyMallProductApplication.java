package com.mymall.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 商品与内容域服务（8087）
 *
 * 接管路由（灰度时用最具体的路径前缀，详见 AGENTS.md / docs/ai/20-gateway.md）：
 *   /mall-admin/product/**            /mall-admin/brand/**
 *   /mall-admin/productCategory/**    /mall-admin/productAttribute/**
 *   /mall-admin/sku/**                /mall-admin/subject/**
 *   /mall-admin/prefrenceArea/**
 *
 * scanBasePackages = "com.mymall"：让 common-web 的 GlobalExceptionHandler / TraceIdFilter 生效。
 */
@SpringBootApplication(scanBasePackages = "com.mymall")
@EnableDiscoveryClient
@MapperScan("com.mymall.product.dao")
public class MyMallProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyMallProductApplication.class, args);
    }
}