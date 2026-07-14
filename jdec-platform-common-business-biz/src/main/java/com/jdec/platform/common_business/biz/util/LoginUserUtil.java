package com.jdec.platform.common_business.biz.util;

import static com.jdec.platform.common_business.biz.util.ValidationUtils.requirePositive;

import com.jdec.platform.common_business.biz.dto.LoginUserPrincipal;
import com.jdec.platform.shared.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 登录用户工具类 — 获取登录用户信息 */
public class LoginUserUtil {

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

        return parseLongSafe(request.getHeader("Module-Id"));
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

        return parseLongSafe(request.getHeader("Role-Id"));
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

        return parseLongSafe(request.getHeader("Subject-Id"));
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
        Long mockUserId = parseLongSafe(request.getHeader("Mock-User-Id"));
        Long subjectId = parseLongSafe(request.getHeader("Subject-Id"));

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
