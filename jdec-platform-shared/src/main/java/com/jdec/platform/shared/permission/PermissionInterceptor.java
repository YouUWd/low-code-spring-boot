package com.jdec.platform.shared.permission;

import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
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
        if (isCreateOperation(httpMethod)) {
            validateCreatePermission(joinPoint);
        } else if (isUpdateOperation(httpMethod)) {
            validateUpdatePermission(joinPoint);
        } else if (isDeleteOperation(httpMethod)) {
            validateDeletePermission(joinPoint);
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
     * 检查是否为创建操作
     *
     * @param httpMethod HTTP方法
     * @return 是否为创建操作
     */
    private boolean isCreateOperation(String httpMethod) {
        return "POST".equalsIgnoreCase(httpMethod);
    }

    /**
     * 检查是否为更新操作
     *
     * @param httpMethod HTTP方法
     * @return 是否为更新操作
     */
    private boolean isUpdateOperation(String httpMethod) {
        return "PUT".equalsIgnoreCase(httpMethod);
    }

    /**
     * 检查是否为删除操作
     *
     * @param httpMethod HTTP方法
     * @return 是否为删除操作
     */
    private boolean isDeleteOperation(String httpMethod) {
        return "DELETE".equalsIgnoreCase(httpMethod);
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
     * 验证创建操作权限（模块级权限）
     *
     * @param joinPoint 连接点
     * @throws PermissionDeniedException 权限不足时抛出
     */
    private void validateCreatePermission(ProceedingJoinPoint joinPoint) throws PermissionDeniedException {
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg != null && isModuleVO(arg)) {
                String moduleCode = getModuleCode(arg);
                String permissionCode = moduleCode + ":*:write";
                
                log.debug("验证创建操作权限: {}", permissionCode);
                
                if (!permissionValidator.hasPermission(permissionCode)) {
                    log.warn("权限不足: {}", permissionCode);
                    throw new PermissionDeniedException(
                        "Permission denied for create operation on module: " + moduleCode,
                        permissionCode,
                        "write"
                    );
                }
            }
        }
    }

    /**
     * 验证更新操作权限（字段级权限）
     *
     * @param joinPoint 连接点
     * @throws PermissionDeniedException 权限不足时抛出
     */
    private void validateUpdatePermission(ProceedingJoinPoint joinPoint) throws PermissionDeniedException {
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg != null && isModuleVO(arg)) {
                String moduleCode = getModuleCode(arg);
                // 获取对象中所有非空字段，验证每个字段的更新权限
                validateFieldUpdatePermissions(arg, moduleCode);
            }
        }
    }

    /**
     * 验证删除操作权限（模块级权限）
     *
     * @param joinPoint 连接点
     * @throws PermissionDeniedException 权限不足时抛出
     */
    private void validateDeletePermission(ProceedingJoinPoint joinPoint) throws PermissionDeniedException {
        // 删除操作通常没有请求体，需要从路径参数或其他方式获取模块信息
        // 这里通过检查方法参数中的SysModule对象来获取模块代码
        Object[] args = joinPoint.getArgs();
        
        // 如果参数中有SysModule对象，使用它来获取模块代码
        for (Object arg : args) {
            if (arg != null && isModuleVO(arg)) {
                String moduleCode = getModuleCode(arg);
                String permissionCode = moduleCode + ":*:delete";
                
                log.debug("验证删除操作权限: {}", permissionCode);
                
                if (!permissionValidator.hasPermission(permissionCode)) {
                    log.warn("权限不足: {}", permissionCode);
                    throw new PermissionDeniedException(
                        "Permission denied for delete operation on module: " + moduleCode,
                        permissionCode,
                        "delete"
                    );
                }
                return;
            }
        }
        
        // 如果参数中没有SysModule对象，尝试从方法签名推断模块代码
        // 这是一个备选方案，实际应用中可能需要更复杂的逻辑
        log.debug("删除操作未找到SysModule对象，跳过权限验证");
    }

    /**
     * 验证字段级更新权限
     *
     * @param obj 更新对象
     * @param moduleCode 模块代码
     * @throws PermissionDeniedException 权限不足时抛出
     */
    private void validateFieldUpdatePermissions(Object obj, String moduleCode) throws PermissionDeniedException {
        java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
        for (java.lang.reflect.Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(obj);
                // 只验证非空字段
                if (value != null) {
                    String fieldCode = field.getName();
                    String permissionCode = moduleCode + ":" + fieldCode + ":update";
                    
                    log.debug("验证字段更新权限: {}", permissionCode);
                    
                    if (!permissionValidator.hasPermission(permissionCode)) {
                        log.warn("权限不足: {}", permissionCode);
                        throw new PermissionDeniedException(
                            "Permission denied for updating field: " + fieldCode + " on module: " + moduleCode,
                            permissionCode,
                            "update"
                        );
                    }
                }
            } catch (IllegalAccessException e) {
                log.debug("无法访问字段: {}", field.getName(), e);
            }
        }
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
