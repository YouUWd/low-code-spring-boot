package com.jdec.platform.shared.permission;

import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 权限拦截器 (AOP)
 *
 * <p>拦截所有Controller方法
 * <p>在请求处理前验证写操作权限
 * <p>在响应返回前根据读权限过滤字段
 * <p>支持单个对象、集合、分页结果的过滤
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class PermissionInterceptor {

    private final FieldFilterEngine fieldFilterEngine;
    private final PermissionValidator permissionValidator;

    /**
     * 拦截所有Controller方法
     *
     * @param joinPoint 连接点
     * @return 过滤后的响应
     * @throws Throwable 异常
     */
    @Around("execution(* com.jdec.platform.*.biz.controller.*.*(..))")
    public Object intercept(ProceedingJoinPoint joinPoint) throws Throwable {
        log.debug("拦截方法: {}.{}", joinPoint.getTarget().getClass().getSimpleName(),
                joinPoint.getSignature().getName());

        // 1. 获取HTTP方法
        String httpMethod = getHttpMethod();
        
        // 2. 对于写操作，先验证权限
        if (isWriteOperation(httpMethod)) {
            validateWritePermission(joinPoint);
        }

        // 3. 执行原方法
        Object result = joinPoint.proceed();

        // 4. 对于读操作，过滤响应
        if (isReadOperation(httpMethod) && shouldFilter(result)) {
            result = filterResponse(result);
        }

        return result;
    }

    /**
     * 获取HTTP方法
     *
     * @return HTTP方法 (GET, POST, PUT, DELETE等)
     */
    private String getHttpMethod() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "GET";
        }
        HttpServletRequest request = attributes.getRequest();
        return request.getMethod();
    }

    /**
     * 检查是否为写操作
     *
     * @param httpMethod HTTP方法
     * @return 是否为写操作
     */
    private boolean isWriteOperation(String httpMethod) {
        return "POST".equalsIgnoreCase(httpMethod) || 
               "PUT".equalsIgnoreCase(httpMethod) || 
               "DELETE".equalsIgnoreCase(httpMethod);
    }

    /**
     * 检查是否为读操作
     *
     * @param httpMethod HTTP方法
     * @return 是否为读操作
     */
    private boolean isReadOperation(String httpMethod) {
        return "GET".equalsIgnoreCase(httpMethod) || 
               "POST".equalsIgnoreCase(httpMethod) || 
               "PUT".equalsIgnoreCase(httpMethod);
    }

    /**
     * 验证写操作权限
     *
     * @param joinPoint 连接点
     * @throws PermissionDeniedException 权限不足时抛出
     */
    private void validateWritePermission(ProceedingJoinPoint joinPoint) throws PermissionDeniedException {
        String httpMethod = getHttpMethod();
        
        // 遍历方法参数，查找ModuleVO对象
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg != null && isModuleVO(arg)) {
                String moduleCode = getModuleCode(arg);
                String permissionType = getPermissionType(httpMethod);
                String permissionCode = moduleCode + ":*:" + permissionType;
                
                log.debug("验证写操作权限: {}", permissionCode);
                
                if (!permissionValidator.hasPermission(permissionCode)) {
                    log.warn("权限不足: {}", permissionCode);
                    throw new PermissionDeniedException(
                        "Permission denied for " + permissionType + " operation on module: " + moduleCode,
                        permissionCode,
                        permissionType
                    );
                }
            }
        }
    }

    /**
     * 获取模块代码
     *
     * @param obj SysModule对象
     * @return 模块代码
     */
    private String getModuleCode(Object obj) {
        com.jdec.platform.shared.annotation.SysModule annotation = 
            obj.getClass().getAnnotation(com.jdec.platform.shared.annotation.SysModule.class);
        if (annotation != null) {
            return annotation.moduleCode();
        }
        return "unknown";
    }

    /**
     * 根据HTTP方法获取权限类型
     *
     * @param httpMethod HTTP方法
     * @return 权限类型 (WRITE, UPDATE, DELETE)
     */
    private String getPermissionType(String httpMethod) {
        if ("POST".equalsIgnoreCase(httpMethod)) {
            return "write";
        } else if ("PUT".equalsIgnoreCase(httpMethod)) {
            return "update";
        } else if ("DELETE".equalsIgnoreCase(httpMethod)) {
            return "delete";
        }
        return "write";
    }

    /**
     * 检查是否需要过滤
     *
     * @param result 方法返回值
     * @return 是否需要过滤
     */
    private boolean shouldFilter(Object result) {
        return result instanceof ApiResponse;
    }

    /**
     * 过滤响应
     *
     * @param result 方法返回值
     * @return 过滤后的响应
     */
    private Object filterResponse(Object result) {
        @SuppressWarnings("unchecked")
        ApiResponse<Object> apiResponse = (ApiResponse<Object>) result;
        Object data = apiResponse.getData();

        if (data == null) {
            log.debug("响应数据为空，无需过滤");
            return result;
        }

        // 处理单个ModuleVO
        if (isModuleVO(data)) {
            log.debug("过滤单个ModuleVO对象");
            Object filtered = fieldFilterEngine.filterObject(data);
            apiResponse.setData(filtered);
        }
        // 处理PageResult<ModuleVO>
        else if (data instanceof PageResult) {
            log.debug("过滤分页结果");
            fieldFilterEngine.filterPageResult((PageResult<?>) data);
        }
        // 处理List<ModuleVO>
        else if (data instanceof List) {
            log.debug("过滤集合");
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) data;
            List<?> filtered = fieldFilterEngine.filterList(list);
            apiResponse.setData(filtered);
        }

        return result;
    }

    /**
     * 检查对象是否为SysModule
     *
     * @param obj 对象
     * @return 是否为SysModule
     */
    private boolean isModuleVO(Object obj) {
        return obj.getClass().isAnnotationPresent(
                com.jdec.platform.shared.annotation.SysModule.class);
    }
}
