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

    @Value("${monitor-center.base-url}")
    private String configCenterBase;

    @Bean("monitorCenterWebClient")
    WebClient configCenterWebClient() {
        return WebClient.builder()
                .defaultHeader("X-Project-No", "student_manage")
                .filter(
                        (request, next) -> {
                            String token = TokenContext.getToken();
                            ClientRequest.Builder builder = ClientRequest.from(request);
                            if (token != null && !token.isBlank()) {
                                builder.header("Authorization", "Bearer " + token);
                            }
                            return next.exchange(builder.build());
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
