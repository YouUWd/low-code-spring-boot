package com.jdec.platform.shared.third;

import java.util.Map;

/** 签名提供者接口，用于为外部 API 请求生成签名。 */
public interface SignatureProvider {

    /**
     * 根据参数生成签名。
     *
     * @param params 待签名的参数
     * @return 签名字符串
     */
    String generateSign(Map<String, Object> params);

    /**
     * 构建带签名的完整请求体。
     *
     * @param action 接口方法名
     * @param body 原始请求体
     * @return 包含签名的请求体
     */
    Map<String, Object> buildSignedRequest(String action, Map<String, Object> body);
}
