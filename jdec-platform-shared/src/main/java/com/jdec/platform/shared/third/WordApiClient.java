package com.jdec.platform.shared.third;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.shared.exception.BusinessException;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Word API 客户端。
 *
 * <p>封装 Word 外部服务的调用，所有请求自动携带 HMAC-SHA256 签名。
 */
@Slf4j
public class WordApiClient extends BaseApiClient {

    private final SignatureProvider signatureProvider;

    public WordApiClient(
            HttpClient httpClient, ExternalApiProperties properties, ObjectMapper objectMapper) {
        super(
                httpClient,
                objectMapper,
                properties.getApi("word-api").getBaseUrl(),
                properties.getApi("word-api").getTimeout(),
                properties.getApi("word-api").getHeaders(),
                "wordApi");

        String apiSecret = properties.getApi("word-api").getApiSecret();
        this.signatureProvider = new HmacSha256SignatureProvider(apiSecret);
    }

    /**
     * 获取所有关系 — 直接返回 data 部分。
     *
     * @param dataType 返回数据的类型（不含 InnerApiResponse 包装）
     * @return InnerApiResponse 中的 data
     */
    public <T> T getAllRelation(TypeReference<T> dataType) {
        return invokeAndUnwrap("getAllRelation", new HashMap<>(), dataType);
    }

    /**
     * 获取关系详情。
     *
     * @param businessNo 业务编号
     * @param dataType 返回数据类型
     * @return InnerApiResponse 中的 data
     */
    public <T> T getRelationInfo(String businessNo, TypeReference<T> dataType) {
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("business_no", businessNo);
        body.put("param", objectMapperToJson(params));
        return invokeAndUnwrap("getRelationInfo", body, dataType);
    }

    /**
     * 获取关系详情树。
     *
     * @param businessNo 业务编号
     * @param dataType 返回数据类型
     * @return InnerApiResponse 中的 data
     */
    public <T> T getRelationInfoTree(String businessNo, TypeReference<T> dataType) {
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("business_no", businessNo);
        body.put("param", objectMapperToJson(params));
        return invokeAndUnwrap("getRelationInfoTree", body, dataType);
    }

    /**
     * 获取关系详情。
     *
     * @param businessNo 业务编号
     * @param dataType 返回数据类型
     * @return InnerApiResponse 中的 data
     */
    public <T> T getRelationWord(String businessNo, TypeReference<T> dataType) {
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("business_no", businessNo);
        body.put("param", objectMapperToJson(params));
        return invokeAndUnwrap("getRelationWord", body, dataType);
    }

    /**
     * 绑定或取消绑定。
     *
     * @param params 请求参数
     */
    public void getBindOrCancel(Object params) {
        invoke("getBindOrCancel", params, new TypeReference<Void>() {});
    }

    // ==================== 内部方法 ====================

    /** 发送带签名的 POST 请求 */
    private <R> R invoke(String action, Object body, TypeReference<R> typeReference) {
        Map<String, Object> signedBody =
                signatureProvider.buildSignedRequest(action, convertToMap(body));
        return post("/invoke", signedBody, typeReference);
    }

    /** 调用接口并自动剥离 InnerApiResponse 包装 */
    private <T> T invokeAndUnwrap(String action, Object body, TypeReference<T> ignoredDataType) {
        var response = this.invoke(action, body, new TypeReference<InnerApiResponse<T>>() {});
        if (response == null || !response.isSuccess()) {
            String errorMsg =
                    (response != null && response.getMsg() != null && !response.getMsg().isBlank())
                            ? response.getMsg()
                            : "外部接口异常";
            throw new BusinessException(500, errorMsg);
        }
        return response.getData();
    }

    /** 将对象序列化为 JSON 字符串（用于 param 字段） */
    private String objectMapperToJson(Object obj) {
        try {
            // 复用父类的 ObjectMapper 不可直接访问，这里通过 convertToMap 间接实现
            // 对于简单 Map 直接 toString 即可满足原始逻辑
            var map = convertToMap(obj);
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"")
                        .append(entry.getKey())
                        .append("\":\"")
                        .append(entry.getValue())
                        .append("\"");
                first = false;
            }
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            throw new BusinessException(500, "序列化参数失败: " + e.getMessage(), e);
        }
    }
}
