package com.jdec.platform.monitorlog.api;

import com.jdec.platform.monitorlog.api.client.MonitorCenterApiClient;
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

    @Value("${monitor-center.base-url}")
    private String configCenterBase;

    @Bean("monitorCenterWebClient")
    WebClient configCenterWebClient() {
        return WebClient.builder()
                // 添加全局默认请求头
                .defaultHeader("X-Project-No", "student_manage") // X-Subject-Id
                // 给请求添加过滤器，添加自定义的认证头
                .filter(
                        (request, next) -> {
                            ClientRequest filtered =
                                    ClientRequest.from(request)
                                            // .header("Authorization", tokenHolder.getToken())
                                            .build();
                            return next.exchange(filtered);
                        })
                .baseUrl(configCenterBase)
                .build();
    }

    @Bean
    MonitorCenterApiClient configCenterApiClient(
            @Qualifier("monitorCenterWebClient") WebClient client) {
        HttpServiceProxyFactory factory =
                HttpServiceProxyFactory.builderFor(WebClientAdapter.create(client)).build();
        return factory.createClient(MonitorCenterApiClient.class);
    }
}
