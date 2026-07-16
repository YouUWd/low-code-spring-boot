package com.jdec.platform.common_business.biz.configcenter.client;

import com.jdec.platform.common_business.biz.context.TokenContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.*;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
@Slf4j
public class WebClientConfig {

    @Value("${config.center.base}")
    private String configCenterBase;

    @Value("${app.project.number}")
    private String projectNumber;

    @Bean("comBizConfigCenterWebClient")
    WebClient comBizConfigCenterWebClient() {
        return WebClient.builder()
                // 添加全局默认请求头
                .defaultHeader("X-Project-No", projectNumber) // X-Subject-Id
                // 给请求添加过滤器，添加自定义的认证头
                .filter(
                        (request, next) -> {
                            ClientRequest filtered =
                                    ClientRequest.from(request)
                                            .header(
                                                    "Authorization",
                                                    "Bearer "
                                                            + TokenContextHolder
                                                                    .getCurrentUserToken())
                                            .build();
                            return next.exchange(filtered);
                        })
                //                .filter(new ExchangeFilterFunction() {
                //                    @Override
                //                    public Mono<ClientResponse> filter(ClientRequest request,
                // ExchangeFunction next) {
                //
                // log.info("headers->"+request.headers().asSingleValueMap().toString());
                //                        return next.exchange(request);
                //                    }
                //                })
                .baseUrl(configCenterBase)
                .build();
    }

    @Bean(name = "comBizConfigCenterApiClient")
    ConfigCenterApiClient comBizConfigCenterApiClient(
            @Qualifier("comBizConfigCenterWebClient") WebClient client) {
        HttpServiceProxyFactory factory =
                HttpServiceProxyFactory.builderFor(WebClientAdapter.create(client)).build();
        return factory.createClient(ConfigCenterApiClient.class);
    }
}
