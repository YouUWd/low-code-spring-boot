package com.jdec.platform.shared.third;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.shared.exception.BusinessException;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProjectApiClient extends BaseApiClient {
    private final SignatureProvider signatureProvider;

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
    }

    /**
     * 根据业务编号及指定列查询项目信息
     *
     * @param businessNo 项目业务编号 (必填，未传入时抛出 BusinessException)
     * @param columns 指定列属性列表 (为空时默认查询 title, logo, icon)
     * @return 外部项目系统返回的数据 Map
     */
    public Map<String, Object> getProjectInfoByNo(String businessNo, List<String> columns) {
        if (StrUtil.isBlank(businessNo)) {
            throw new BusinessException(400, "项目业务编号 businessNo 不能为空");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("business_no", businessNo.trim());

        List<String> targetColumns =
                (columns != null && !columns.isEmpty())
                        ? columns
                        : List.of("title", "logo", "icon");
        for (int i = 0; i < targetColumns.size(); i++) {
            params.put("columns[" + i + "]", targetColumns.get(i));
        }

        return invokeAndUnwrap("getProjectInfo", params, new TypeReference<>() {});
    }

    /**
     * 根据业务编号查询项目信息 (默认查询常用列 title, logo, icon)
     *
     * @param businessNo 项目业务编号 (必填，未传入时抛出 BusinessException)
     * @return 外部项目系统返回的数据 Map
     */
    public Map<String, Object> getProjectInfoByNo(String businessNo) {
        return getProjectInfoByNo(businessNo, null);
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
        Map<String, Object> signedBody = signatureProvider.buildSignedRequest(action, params);
        return post("/invoke", signedBody, typeReference);
    }
}
