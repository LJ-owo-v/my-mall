package com.mymall.portal;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 门户域服务（8092）
 *
 * 接管路由：网关 /mall-portal/**（StripPrefix=1 → 服务内看到 /sso、/cart、/order、/home ...）
 * 13 个 Controller / 65 端点：
 *   /sso(6) /member/address(5) /member/coupon(5) /member/attention(5) /member/productCollection(5)
 *   /member/readHistory(4) /cart(8) /order(10) /returnApply(1) /alipay(4) /home(6) /product(3) /brand(3)
 *
 * @EnableScheduling：超时未支付自动取消的定时兜底任务（OrderTimeOutCancelTask）
 * @EnableFeignClients：门户需要调用其它域（如商品/营销）时使用
 */
@SpringBootApplication(scanBasePackages = "com.mymall")
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
@MapperScan("com.mymall.portal.dao")
public class MyMallPortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyMallPortalApplication.class, args);
    }
}