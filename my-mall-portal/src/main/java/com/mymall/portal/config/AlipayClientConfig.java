package com.mymall.portal.config;

import com.alipay.api.AlipayClient;

import com.alipay.api.DefaultAlipayClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AlipayClientConfig {
    @Bean   // 方法返回值注册为 Bean，默认 Bean 名 = 方法名 alipayClient
    public AlipayClient alipayClient(AlipayConfig config) {
        return new DefaultAlipayClient(
                config.getGatewayUrl(),      // 支付宝网关地址（沙箱：https://openapi.sandbox.dl.alipaydev.com/gateway.do）
                config.getAppId(),           // 应用ID（沙箱应用的 APPID）
                config.getAppPrivateKey(),   // 应用私钥：商户自己持有，用于给"发出去的请求"签名
                config.getFormat(),          // 参数返回格式，固定 JSON
                config.getCharset(),         // 请求编码，UTF-8
                config.getAlipayPublicKey(), // 支付宝公钥：用于"验证支付宝响应/异步回调"的签名
                config.getSignType());       // 签名算法，RSA2（SHA256WithRSA）
    }
}

