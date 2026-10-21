package com.jdec.platform.shared.third;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.shared.exception.BusinessException;
import java.net.http.HttpClient;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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
     * 获取关系词编辑信息。
     *
     * <p>请求参数示例：
     *
     * <pre>{@code
     * {
     *     "param": [
     *         {
     *             "business_no": "GX2608251510391",
     *             "word_uuid": ["xabsx","xsfew"]
     *         },
     *         {
     *             "business_no": "GX2608251510391",
     *             "word_uuid": ["xabsx","xsfew"]
     *         }
     *     ]
     * }
     * }</pre>
     *
     * @param params 请求参数（可为包含 "param" 的 Map 或请求对象，亦可直接传入 param 列表）
     * @param dataType 返回数据类型
     * @return InnerApiResponse 中的 data
     */
    public <T> T getRelationWordEdit(Object params, TypeReference<T> dataType) {
        Map<String, Object> body = new HashMap<>();
        Object paramVal = null;
        if (params instanceof Map<?, ?> map) {
            if (map.containsKey("param")) {
                paramVal = map.get("param");
            } else {
                paramVal = map;
            }
        } else if (params instanceof Collection<?>
                || (params != null && params.getClass().isArray())) {
            paramVal = params;
        } else if (params != null) {
            Map<String, Object> map = convertToMap(params);
            if (map.containsKey("param")) {
                paramVal = map.get("param");
            } else {
                paramVal = params;
            }
        }
        // 关键：Word API 约定 param 字段必须为 JSON 字符串参与签名与传输
        String paramJson = (paramVal instanceof String str) ? str : objectMapperToJson(paramVal);
        body.put("param", paramJson);
        return invokeAndUnwrap("getRelationWordEdit", body, dataType);
    }

    /**
     * 获取关系词编辑信息（直接传入 param 列表）。
     *
     * @param paramList 参数项列表，每项需包含 business_no 和 word_uuid
     * @param dataType 返回数据类型
     * @return InnerApiResponse 中的 data
     */
    public <T> T getRelationWordEdit(List<?> paramList, TypeReference<T> dataType) {
        Map<String, Object> body = new HashMap<>();
        body.put("param", objectMapperToJson(paramList));
        return invokeAndUnwrap("getRelationWordEdit", body, dataType);
    }

    /**
     * 获取关系词编辑信息（默认返回 JsonNode）。
     *
     * @param params 请求参数
     * @return JsonNode 数据节点
     */
    public JsonNode getRelationWordEdit(Object params) {
        return getRelationWordEdit(params, new TypeReference<>() {});
    }

    /**
     * 绑定或取消绑定。
     *
     * @param params 请求参数
     */
    public void getBindOrCancel(Object params) {
        invoke("getBindOrCancel", params, new TypeReference<Void>() {});
    }

    /** 发送带签名的 POST 请求 */
    private <R> R invoke(String action, Object body, TypeReference<R> typeReference) {
        return invoke(action, body, getObjectMapper().constructType(typeReference.getType()));
    }

    private <R> R invoke(String action, Object body, JavaType javaType) {
        Map<String, Object> signedBody =
                signatureProvider.buildSignedRequest(action, convertToMap(body));
        return post("/invoke", signedBody, javaType);
    }

    /** 调用接口并自动剥离 InnerApiResponse 包装 */
    private <T> T invokeAndUnwrap(String action, Object body, TypeReference<T> dataType) {
        var javaType =
                getObjectMapper()
                        .getTypeFactory()
                        .constructParametricType(
                                InnerApiResponse.class,
                                getObjectMapper().constructType(dataType.getType()));
        InnerApiResponse<T> response = this.invoke(action, body, javaType);
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
        if (obj == null) {
            return "[]";
        }
        try {
            return getObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new BusinessException(500, "序列化参数失败: " + e.getMessage(), e);
        }
    }
}
