package com.mymall.search;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * 搜索域服务（8091）
 *
 * 接管路由：网关 /mall-search/**（StripPrefix=1 → 服务内看到 /esProduct/**）
 * 端点（8 个，EsProductController）：importAll、delete/{id}、delete/batch、create/{id}、
 *                                   search/simple、search、recommend/{id}、search/relate
 *
 * scanBasePackages="com.mymall"：让 common-web 的 GlobalExceptionHandler / TraceIdFilter 生效
 */
@SpringBootApplication(scanBasePackages = "com.mymall")
@EnableDiscoveryClient
@EnableElasticsearchRepositories("com.mymall.search.repository")
@MapperScan("com.mymall.search.dao")
public class MyMallSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyMallSearchApplication.class, args);
    }
}