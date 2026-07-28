package com.jdec.platform.shared.utils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 请求上下文工具类 - 获取当前HTTP请求的IP、浏览器、平台等信息 */
@Slf4j
public class RequestContextUtils {

    private static final String UNKNOWN = "unknown";
    private static final String[] IP_HEADER_CANDIDATES = {
        "X-Forwarded-For",
        "X-Real-IP",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "HTTP_X_FORWARDED_FOR",
        "HTTP_X_FORWARDED",
        "HTTP_X_CLUSTER_CLIENT_IP",
        "HTTP_CLIENT_IP",
        "HTTP_FORWARDED_FOR",
        "HTTP_FORWARDED",
        "HTTP_VIA",
        "REMOTE_ADDR"
    };

    /** 获取当前请求 */
    public static HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            log.warn("获取当前请求失败", e);
            return null;
        }
    }

    /**
     * 获取客户端真实IP地址
     *
     * <p>支持通过代理、负载均衡等场景获取真实IP
     */
    public static String getClientIp() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return "";
        }

        String ip = null;

        // 依次尝试各种Header
        for (String header : IP_HEADER_CANDIDATES) {
            ip = request.getHeader(header);
            if (StringUtils.hasText(ip) && !UNKNOWN.equalsIgnoreCase(ip)) {
                break;
            }
        }

        // 如果所有Header都没有，使用request.getRemoteAddr()
        if (!StringUtils.hasText(ip) || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // X-Forwarded-For可能包含多个IP（格式：client, proxy1, proxy2），取第一个
        if (StringUtils.hasText(ip) && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip != null ? ip : "";
    }

    /**
     * 获取浏览器类型
     *
     * <p>从User-Agent中解析浏览器信息
     */
    public static String getBrowser() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return "";
        }

        String userAgent = request.getHeader("User-Agent");
        if (!StringUtils.hasText(userAgent)) {
            return "";
        }

        // 简单识别主流浏览器
        userAgent = userAgent.toLowerCase();
        if (userAgent.contains("edg")) {
            return "Edge";
        } else if (userAgent.contains("chrome")) {
            return "Chrome";
        } else if (userAgent.contains("safari")) {
            return "Safari";
        } else if (userAgent.contains("firefox")) {
            return "Firefox";
        } else if (userAgent.contains("msie") || userAgent.contains("trident")) {
            return "IE";
        } else if (userAgent.contains("opera") || userAgent.contains("opr")) {
            return "Opera";
        } else {
            return "Other";
        }
    }

    /**
     * 获取操作系统/平台
     *
     * <p>从User-Agent中解析操作系统信息
     */
    public static String getPlatform() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return "";
        }

        String userAgent = request.getHeader("User-Agent");
        if (!StringUtils.hasText(userAgent)) {
            return "";
        }

        userAgent = userAgent.toLowerCase();
        if (userAgent.contains("windows")) {
            return "Windows";
        } else if (userAgent.contains("mac os")) {
            return "MacOS";
        } else if (userAgent.contains("linux")) {
            return "Linux";
        } else if (userAgent.contains("android")) {
            return "Android";
        } else if (userAgent.contains("iphone") || userAgent.contains("ipad")) {
            return "iOS";
        } else {
            return "Other";
        }
    }

    /**
     * 获取User-Agent原始字符串
     *
     * @return User-Agent字符串
     */
    public static String getUserAgent() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return "";
        }
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "";
    }

    /**
     * 获取请求URL
     *
     * @return 完整的请求URL（包含QueryString）
     */
    public static String getRequestUrl() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return "";
        }

        String url = request.getRequestURL().toString();
        String queryString = request.getQueryString();
        if (StringUtils.hasText(queryString)) {
            url += "?" + queryString;
        }
        return url;
    }

    /**
     * 获取请求URI
     *
     * @return 请求URI（不含域名和QueryString）
     */
    public static String getRequestUri() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return "";
        }
        return request.getRequestURI();
    }
}
