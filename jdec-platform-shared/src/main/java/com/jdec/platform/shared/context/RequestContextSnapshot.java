package com.jdec.platform.shared.context;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 请求上下文快照（不可变）。
 *
 * <p>在请求线程内一次性提取，可安全跨线程传递。
 */
@Getter
@ToString
@Builder
public class RequestContextSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 客户端IP */
    private final String clientIp;

    /** 原始 User-Agent */
    private final String userAgent;

    /** 浏览器类型：Chrome / Edge / Safari... */
    private final String browser;

    /** 平台：Windows / MacOS / Android / iOS... */
    private final String platform;

    /** 完整 URL（含 QueryString） */
    private final String requestUrl;

    /** 请求 URI（不含域名、QueryString） */
    private final String requestUri;

    /** 请求方法：GET / POST... */
    private final String requestMethod;

    /** 链路 traceId（来自 MDC） */
    private final String traceId;

    /** 当前登录用户ID（来自 SecurityContext 或业务上下文，可选） */
    private final String userId;

    /** 自定义扩展字段（如业务透传参数） */
    private final Map<String, String> extensions;

    public Map<String, String> getExtensions() {
        return extensions == null ? Collections.emptyMap() : extensions;
    }

    /** 从扩展字段取值 */
    public String getExtension(String key) {
        return getExtensions().get(key);
    }
}
