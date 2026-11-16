package com.example.springai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SaaS 订阅订单管理系统 API")
                        .version("1.0.0")
                        .description("基于 Spring AI + MyBatis-Plus 的 SaaS 订阅订单管理接口"));
    }
}