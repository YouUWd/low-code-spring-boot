package com.jdec.platform.shared.third;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 外部 API 客户端自动装配。 */
@Slf4j
@Configuration
public class ExternalApiAutoConfiguration {

    @Bean
    public HttpClient externalApiHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "external-api.apis.word-api",
            name = "enabled",
            havingValue = "true")
    public WordApiClient wordApiClient(
            HttpClient externalApiHttpClient,
            ExternalApiProperties properties,
            ObjectMapper objectMapper) {
        log.info("初始化 WordApiClient, baseUrl={}", properties.getApi("word-api").getBaseUrl());
        return new WordApiClient(externalApiHttpClient, properties, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "external-api.apis.project-api",
            name = "enabled",
            havingValue = "true")
    public ProjectApiClient projectApiClient(
            HttpClient externalApiHttpClient,
            ExternalApiProperties properties,
            ObjectMapper objectMapper) {
        log.info("初始化 ProjectApiClient, baseUrl={}", properties.getApi("project-api").getBaseUrl());
        return new ProjectApiClient(externalApiHttpClient, properties, objectMapper);
    }
}
