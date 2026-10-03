package com.mymall.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 文件服务（8090）：接管 /mall-admin/aliyun/oss/**
 *   GET  /aliyun/oss/policy    → 生成前端直传 OSS 的签名（OssPolicyResult）
 *   POST /aliyun/oss/callback  → OSS 上传成功后的回调校验（OssCallbackParam → OssCallbackResult）
 *
 * 本模块没有数据源、没有 MyBatis、没有 Redis（故 pom 里排除 common-redis）。
 */
@SpringBootApplication(scanBasePackages = "com.mymall")
@EnableDiscoveryClient
public class MyMallFileApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyMallFileApplication.class, args);
    }
}