package com.example.order.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Service API")
                        .description("""
                                電子商務訂單服務 API - Resilience4j 韌性機制 PoC

                                ## 功能特色

                                - **Retry（重試）**: 對庫存服務的暫時性錯誤自動重試
                                - **CircuitBreaker（斷路器）**: 支付閘道快速失敗保護
                                - **TimeLimiter（超時控制）**: 物流服務超時降級處理

                                ## 韌性機制執行順序

                                `TimeLimiter → CircuitBreaker → Retry → HTTP Call`
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Order Service Team")
                                .email("order-service@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development")
                ));
    }
}
