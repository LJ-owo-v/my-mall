package com.mymall.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableDiscoveryClient
@EnableFeignClients
@SpringBootApplication(scanBasePackages = "com.mymall")
public class MyMallAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyMallAuthApplication.class, args);
    }
}
