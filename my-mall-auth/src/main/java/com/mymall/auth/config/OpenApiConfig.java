package com.mymall.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 这个类是 **Swagger/Knife4j 接口文档的配置**
 * 做两件事：
 * ① 文档长什么样、怎么填 token；
 * ② 让 `/swagger-ui/` 能直接打开。
 */
@Configuration
public class OpenApiConfig implements WebMvcConfigurer {
    private static final String SECURITY_SCHEME_NAME = "Authorization";
    @Bean
    public OpenAPI mallAuthOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("mall认证中心")                    // 文档页标题
                        .description("mall认证中心相关接口文档")    // 描述
                        .version("v1.0.0"))                    // 版本号
                        .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                        .components(new Components()
                                .addSecuritySchemes("Authorization",
                                        new SecurityScheme()
                                                .name("Authorization")              // 请求头名字
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")                  // Bearer 方案
                                                .bearerFormat("JWT")));            // token 格式
    }
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        //配置访问`/swagger-ui/`路径时可以直接跳转到`/swagger-ui/index.html`
        registry.addViewController("/swagger-ui/").setViewName("redirect:/swagger-ui/index.html");
    }
}

