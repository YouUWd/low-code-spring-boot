package com.jdec.platform.shared.third;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.shared.exception.BusinessException;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;

public class ProjectApiClient extends BaseApiClient {
    private final SignatureProvider signatureProvider;
    private final String businessNo;

    public ProjectApiClient(
            HttpClient httpClient, ExternalApiProperties properties, ObjectMapper objectMapper) {
        super(
                httpClient,
                objectMapper,
                properties.getApi("project-api").getBaseUrl(),
                properties.getApi("project-api").getTimeout(),
                properties.getApi("project-api").getHeaders(),
                "projectApi");
        var config = properties.getApi("project-api");
        this.signatureProvider = new HmacSha256SignatureProvider(config.getApiSecret());
        this.businessNo = config.getApiKey();
    }

    /**
     * 绑定或取消绑定。
     *
     * @param params 请求参数
     */
    public void getProjectInfoByNo(Object params) {
        invoke("getProjectInfo", params, new TypeReference<Void>() {});
    }

    public Map<String, Object> getProjectInfo() {
        Map<String, Object> params = new HashMap<>();
        params.put("columns[0]", "title");
        params.put("columns[1]", "logo");
        params.put("columns[2]", "icon");
        // business_no 自动从配置中读取 (apiKey)
        return invokeAndUnwrap(
                "getProjectInfo", params, new TypeReference<Map<String, Object>>() {});
    }

    /** 通用方法：调用接口并自动剥离 InnerApiResponse 包装 */
    private <T> T invokeAndUnwrap(String action, Object body, TypeReference<T> ignoredDataType) {
        var response = this.invoke(action, body, new TypeReference<InnerApiResponse<T>>() {});
        if (response == null || response.getStatus() != 200) {
            String errorMsg =
                    (response != null && StrUtil.isNotBlank(response.getMsg()))
                            ? response.getMsg()
                            : "外部项目接口异常";
            throw new BusinessException(500, errorMsg);
        }
        return response.getData();
    }

    /** 发送带签名的 POST 请求 */
    private <R> R invoke(String action, Object body, TypeReference<R> typeReference) {
        Map<String, Object> params = convertToMap(body);
        if (StrUtil.isNotBlank(businessNo)) {
            params.put("business_no", businessNo);
        }
        Map<String, Object> signedBody = signatureProvider.buildSignedRequest(action, params);
        return post("/invoke", signedBody, typeReference);
    }
}
