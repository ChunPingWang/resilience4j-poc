package com.example.order.infrastructure.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for WebClient instances.
 */
@Configuration
public class WebClientConfig {

    @Value("${services.inventory.base-url:http://localhost:8081}")
    private String inventoryBaseUrl;

    @Value("${services.payment.base-url:http://localhost:8082}")
    private String paymentBaseUrl;

    @Value("${services.shipping.base-url:http://localhost:8083}")
    private String shippingBaseUrl;

    @Bean
    public WebClient inventoryWebClient(WebClient.Builder builder) {
        return createWebClient(builder, inventoryBaseUrl, 4000);
    }

    @Bean
    public WebClient paymentWebClient(WebClient.Builder builder) {
        return createWebClient(builder, paymentBaseUrl, 8000);
    }

    @Bean
    public WebClient shippingWebClient(WebClient.Builder builder) {
        return createWebClient(builder, shippingBaseUrl, 3000);
    }

    private WebClient createWebClient(WebClient.Builder builder, String baseUrl, int timeoutMs) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                .responseTimeout(Duration.ofMillis(timeoutMs))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS)));

        return builder
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
