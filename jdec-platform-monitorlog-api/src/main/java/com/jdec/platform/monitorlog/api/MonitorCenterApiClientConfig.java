package com.jdec.platform.monitorlog.api;

import com.jdec.platform.monitorlog.api.client.MonitorCenterApiClient;
import com.jdec.platform.shared.context.TokenContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class MonitorCenterApiClientConfig {

    // 从配置文件application-dev.yml读取 monitor-center.base-url 作为基础 URL
    @Value("${monitor-center.base-url}")
    private String monitorLogBase;

    // 创建一个 WebClient 实例，用于与监控中心 API 进行交互
    @Bean("monitorLogWebClient")
    WebClient monitorLogWebClient() {
        return WebClient.builder()
                // 设置默认请求头 X-Project-No: student_manage
                .defaultHeader("X-Project-No", "monitorlog-center")
                .filter(
                        (request, next) -> {
                            String token = TokenContext.getToken();
                            ClientRequest.Builder builder = ClientRequest.from(request);
                            if (token != null && !token.isBlank()) {
                                builder.header("Authorization", "Bearer " + token);
                            }
                            return next.exchange(builder.build());
                        })
                .baseUrl(monitorLogBase)
                .build();
    }

    // 创建一个 MonitorCenterApiClient 实例，用于调用监控中心 API
    @Bean
    MonitorCenterApiClient monitorLogApiClient(@Qualifier("monitorLogWebClient") WebClient client) {
        HttpServiceProxyFactory factory =
                HttpServiceProxyFactory.builderFor(WebClientAdapter.create(client)).build();
        return factory.createClient(MonitorCenterApiClient.class);
    }
}
