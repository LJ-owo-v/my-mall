package com.mymall.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关白名单 —— 前缀与原项目保持一致（secure.ignore.urls），
 * 这样原项目 Nacos 里的 mall-gateway-dev.yaml 可以几乎原样复用。
 */
@Component
@ConfigurationProperties(prefix = "secure.ignore")
public class IgnoreUrlsProperties {

    private List<String> urls = new ArrayList<>();

    public List<String> getUrls() { return urls; }
    public void setUrls(List<String> urls) { this.urls = urls; }
}