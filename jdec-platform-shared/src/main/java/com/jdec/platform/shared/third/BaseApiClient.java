package com.jdec.platform.shared.third;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.shared.exception.BusinessException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * HTTP API 客户端基类。
 *
 * <p>封装通用的 HTTP 请求逻辑（JSON 序列化/反序列化、请求头、超时），子类只需关注业务方法。
 */
@Slf4j
public abstract class BaseApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final Duration timeout;
    private final Map<String, String> defaultHeaders;
    private final String clientName;

    protected BaseApiClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String baseUrl,
            Duration timeout,
            Map<String, String> defaultHeaders,
            String clientName) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(10);
        this.defaultHeaders = defaultHeaders != null ? defaultHeaders : Map.of();
        this.clientName = clientName;
    }

    /**
     * 发送 POST 请求。
     *
     * @param path 请求路径（相对于 baseUrl）
     * @param body 请求体对象（会被序列化为 JSON）
     * @param typeReference 响应类型
     * @return 反序列化后的响应
     */
    protected <R> R post(String path, Object body, TypeReference<R> typeReference) {
        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            String url = baseUrl + path;

            HttpRequest.Builder requestBuilder =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(timeout)
                            .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            defaultHeaders.forEach(requestBuilder::header);

            log.debug("[{}] POST {} body={}", clientName, url, jsonBody);

            HttpResponse<String> response =
                    httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            log.debug(
                    "[{}] 响应 status={} body={}",
                    clientName,
                    response.statusCode(),
                    response.body());

            if (response.statusCode() != 200) {
                throw new BusinessException(
                        500,
                        String.format(
                                "[%s] HTTP 请求失败: status=%d, body=%s",
                                clientName, response.statusCode(), response.body()));
            }

            return objectMapper.readValue(response.body(), typeReference);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] 请求异常", clientName, e);
            throw new BusinessException(500, "[" + clientName + "] 请求异常: " + e.getMessage(), e);
        }
    }

    /**
     * 将对象转换为 Map（用于签名等场景）。
     *
     * @param obj 待转换对象
     * @return Map 表示
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> convertToMap(Object obj) {
        if (obj == null) {
            return new LinkedHashMap<>();
        }
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        return objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});
    }
}
