package com.jdec.platform.shared.third;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 基于 HMAC-SHA256 的签名提供者。 */
@Slf4j
@RequiredArgsConstructor
public class HmacSha256SignatureProvider implements SignatureProvider {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String apiSecret;

    @Override
    public String generateSign(Map<String, Object> params) {
        try {
            StringBuilder queryString = new StringBuilder();
            boolean first = true;

            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (!first) {
                    queryString.append("&");
                }
                String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
                String value = buildQueryValue(entry.getValue());
                queryString.append(key).append("=").append(value);
                first = false;
            }
            log.debug("签名原文: {}", queryString);
            return hmacSha256(queryString.toString(), apiSecret);
        } catch (Exception e) {
            log.error("生成签名失败", e);
            throw new RuntimeException("生成签名失败", e);
        }
    }

    @Override
    public Map<String, Object> buildSignedRequest(String action, Map<String, Object> body) {
        Map<String, Object> requestMap = new TreeMap<>();

        requestMap.put("action", action);
        requestMap.put("timestamp", System.currentTimeMillis() / 1000);
        requestMap.put("nonce", generateNonce());

        if (body != null && !body.isEmpty()) {
            requestMap.putAll(body);
        }

        String sign = generateSign(requestMap);
        requestMap.put("sign", sign);

        return requestMap;
    }

    private String generateNonce() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, 8);
    }

    @SuppressWarnings("unchecked")
    private String buildQueryValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append("&");
                }
                String key = (String) entry.getKey();
                Object val = entry.getValue();

                sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
                sb.append("=");
                if (val instanceof Map<?, ?>) {
                    sb.append(buildQueryValue(val));
                } else {
                    sb.append(URLEncoder.encode(String.valueOf(val), StandardCharsets.UTF_8));
                }
                first = false;
            }
            return sb.toString();
        } else if (value instanceof Object[] array) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < array.length; i++) {
                if (i > 0) sb.append("&");
                sb.append(URLEncoder.encode(String.valueOf(array[i]), StandardCharsets.UTF_8));
            }
            return sb.toString();
        } else {
            return URLEncoder.encode(
                    String.valueOf(value != null ? value : ""), StandardCharsets.UTF_8);
        }
    }

    private String hmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
        SecretKeySpec secretKey =
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM);
        mac.init(secretKey);
        byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(bytes);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
