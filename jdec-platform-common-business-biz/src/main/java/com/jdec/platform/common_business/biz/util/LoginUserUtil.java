package com.jdec.platform.common_business.biz.util;

import static com.jdec.platform.common_business.biz.util.ValidationUtils.requirePositive;

import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.jdec.platform.common_business.biz.dto.LoginUserPrincipal;
import com.jdec.platform.shared.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 登录用户工具类 — 获取登录用户信息 */
public class LoginUserUtil {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 获取当前登录用户id,如果是模拟用户登录，则返回模拟用户id
     *
     * @return
     */
    public static Long getLoginUserId() {
        Triple<Long, Long, Long> current = getCurrent();
        Long loginUserId = current.getLeft();
        if (current.getMiddle() != null && current.getMiddle() > 0) {
            loginUserId = current.getMiddle();
        }
        return loginUserId;
    }

    /**
     * 获取当前登录用户id
     *
     * @return
     */
    public static Long getRealLoginUserId() {
        Triple<Long, Long, Long> current = getCurrent();
        return current.getLeft();
    }

    /**
     * 获取当前登录用户主体id
     *
     * @return
     */
    public static Short getSubjectId() {
        Triple<Long, Long, Long> current = getCurrent();

        requirePositive(current.getRight(), "未指定主体");

        return current.getRight().shortValue();
    }

    /**
     * 获取当前登录用户主体id
     *
     * @return
     */
    public static Long getLongSubjectId() {
        Triple<Long, Long, Long> current = getCurrent();

        requirePositive(current.getRight(), "未指定主体");

        return current.getRight();
    }

    /**
     * 获取当前用户角色ID
     *
     * @return
     */
    public static Long getModuleId() {

        // 2. 安全获取 RequestContext 属性，防止在异步线程中调用时抛出异常
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            // 如果不在 Web 上下文中，直接返回 userId，Header 参数给 null（或根据业务需求抛出异常）
            return 0L;
        }

        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();

        // 3. 安全获取并转换 Header 数据 (String -> Long)

        return parseLongSafe(request.getHeader("X-Module-Id"));
    }

    /**
     * 获取当前用户角色ID
     *
     * @return
     */
    public static Long getRoleId() {

        // 2. 安全获取 RequestContext 属性，防止在异步线程中调用时抛出异常
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            // 如果不在 Web 上下文中，直接返回 userId，Header 参数给 null（或根据业务需求抛出异常）
            return 0L;
        }

        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();

        // 3. 安全获取并转换 Header 数据 (String -> Long)

        return parseLongSafe(request.getHeader("X-Role-Id"));
    }

    /**
     * 获取当前用户角色ID
     *
     * @return
     */
    public static Long getSubjectIdFromHeader() {

        // 2. 安全获取 RequestContext 属性，防止在异步线程中调用时抛出异常
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            // 如果不在 Web 上下文中，直接返回 userId，Header 参数给 null（或根据业务需求抛出异常）
            return 0L;
        }

        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();

        // 3. 安全获取并转换 Header 数据 (String -> Long)

        return parseLongSafe(request.getHeader("X-Subject-Id"));
    }

    /**
     * 获取当前用户 的 project NO
     *
     * @return
     */
    public static String getProjectNoFromHeader() {

        // 2. 安全获取 RequestContext 属性，防止在异步线程中调用时抛出异常
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            // 如果不在 Web 上下文中，直接返回 userId，Header 参数给 null（或根据业务需求抛出异常）
            return null;
        }

        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();

        // 3. 安全获取并转换 Header 数据 (String -> Long)

        return StringUtils.isNotEmpty(request.getHeader("X-Project-No"))
                ? request.getHeader("X-Project-No")
                : "student_manage";
    }

    /**
     * 获取当前用户 的 Os
     *
     * @return
     */
    public static String getOsFromHeader() {

        // 2. 安全获取 RequestContext 属性，防止在异步线程中调用时抛出异常
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            // 如果不在 Web 上下文中，直接返回 userId，Header 参数给 null（或根据业务需求抛出异常）
            return null;
        }

        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        if (StringUtils.isEmpty(request.getHeader("User-Agent"))) {
            return "Unknown";
        }
        UserAgent ua = UserAgentUtil.parse(request.getHeader("User-Agent"));
        if (ua != null && StringUtils.isNotEmpty(ua.getOs().getName())) {
            String[] ss = ua.getOs().getName().split(" ");
            return (ss != null && ss.length > 0) ? ss[0] : "Unknown";
        }
        return "Unknown";
    }

    /**
     * 获取当前用户 的 Os
     *
     * @return
     */
    public static String getBrowserFromHeader() {

        // 2. 安全获取 RequestContext 属性，防止在异步线程中调用时抛出异常
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            // 如果不在 Web 上下文中，直接返回 userId，Header 参数给 null（或根据业务需求抛出异常）
            return null;
        }

        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        if (StringUtils.isEmpty(request.getHeader("User-Agent"))) {
            return "Unknown";
        }
        UserAgent ua = UserAgentUtil.parse(request.getHeader("User-Agent"));
        if (ua != null && StringUtils.isNotEmpty(ua.getBrowser().getName())) {
            return ua.getBrowser().getName();
        }
        return "Unknown";
    }

    /**
     * 获取请求的源
     *
     * @return
     */
    public static String getRefererFromHeader() {

        // 2. 安全获取 RequestContext 属性，防止在异步线程中调用时抛出异常
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            // 如果不在 Web 上下文中，直接返回 userId，Header 参数给 null（或根据业务需求抛出异常）
            return null;
        }
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        String referer = request.getHeader("Origin");

        if (StringUtils.isEmpty(referer)) {
            referer = request.getHeader("Referer");
        }
        return referer;
    }

    /**
     * 获取当前用户 的 Os
     *
     * @return
     */
    public static String getIpFromHeader() {

        // 2. 安全获取 RequestContext 属性，防止在异步线程中调用时抛出异常
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            // 如果不在 Web 上下文中，直接返回 userId，Header 参数给 null（或根据业务需求抛出异常）
            return null;
        }

        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 获取请求主机IP地址,如果通过代理进来，则透过防火墙获取真实IP地址
        String headerName = "x-forwarded-for";
        String ip = request.getHeader(headerName);
        if (null != ip && ip.length() != 0 && !"unknown".equalsIgnoreCase(ip)) {
            // 多次反向代理后会有多个IP值，第一个IP才是真实IP,它们按照英文逗号','分割
            if (ip.indexOf(",") != -1) {
                ip = ip.split(",")[0];
            }
        }
        if (checkIp(ip)) {
            headerName = "Proxy-Client-IP";
            ip = request.getHeader(headerName);
        }
        if (checkIp(ip)) {
            headerName = "WL-Proxy-Client-IP";
            ip = request.getHeader(headerName);
        }
        if (checkIp(ip)) {
            headerName = "HTTP_CLIENT_IP";
            ip = request.getHeader(headerName);
        }
        if (checkIp(ip)) {
            headerName = "HTTP_X_FORWARDED_FOR";
            ip = request.getHeader(headerName);
        }
        if (checkIp(ip)) {
            headerName = "X-Real-IP";
            ip = request.getHeader(headerName);
        }
        if (checkIp(ip)) {
            // headerName = "remote addr";
            ip = request.getRemoteAddr();
            // 127.0.0.1 ipv4, 0:0:0:0:0:0:0:1 ipv6
            if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
                // 根据网卡取本机配置的IP
                InetAddress inet = null;
                try {
                    inet = InetAddress.getLocalHost();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                ip = inet.getHostAddress();
            }
        }
        return ip;
    }

    private static boolean checkIp(String ip) {
        if (null == ip || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            return true;
        }
        return false;
    }

    /**
     * 获取当前用户 的 project NO
     *
     * @return
     */
    public static String getTokenFromHeader() {

        // 2. 安全获取 RequestContext 属性，防止在异步线程中调用时抛出异常
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            // 如果不在 Web 上下文中，直接返回 userId，Header 参数给 null（或根据业务需求抛出异常）
            return null;
        }

        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();

        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (org.springframework.util.StringUtils.hasText(header)
                && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * 全局获取用户信息
     *
     * @return [登陆用户id，模拟用户id，主体id ]
     */
    public static Triple<Long, Long, Long> getCurrent() {

        // 1. 尽早校验认证状态，防止 getAuthentication() 返回 null 导致的 NPE
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof LoginUserPrincipal loginUser)) {
            throw new BusinessException(401, "未登录或令牌已过期");
        }

        // 2. 安全获取 RequestContext 属性，防止在异步线程中调用时抛出异常
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            // 如果不在 Web 上下文中，直接返回 userId，Header 参数给 null（或根据业务需求抛出异常）
            return Triple.of(loginUser.getUserId(), null, null);
        }

        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();

        // 3. 安全获取并转换 Header 数据 (String -> Long)
        Long mockUserId = parseLongSafe(request.getHeader("X-Mock-User-Id"));
        Long subjectId = parseLongSafe(request.getHeader("X-Subject-Id"));

        return Triple.of(loginUser.getUserId(), mockUserId, subjectId);
    }

    public static Long getLoginSubjectId() {
        // 1. 尽早校验认证状态，防止 getAuthentication() 返回 null 导致的 NPE
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof LoginUserPrincipal loginUser)) {
            throw new BusinessException(401, "未登录或令牌已过期");
        }
        return loginUser.getSubjectId();
    }

    /** 辅助方法：安全地将字符串转换为 Long，避免 NumberFormatException */
    private static Long parseLongSafe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            // 解析失败时返回 null（或者你可以视业务需求抛出 400 Bad Request 参数异常）
            return null;
        }
    }
}
