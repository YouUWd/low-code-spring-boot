package com.jdec.platform.shared.datasource;

import com.jdec.platform.shared.context.AppCodeContext;
import com.jdec.platform.shared.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 租户拦截器 - 从请求头中提取租户 ID 并设置数据源
 * <p>
 * 工作原理：
 * 1. 从请求头 X-Tenant-Id 获取租户标识（数据源名称）
 * 2. 验证数据源是否存在（防止使用不存在的数据源）
 * 3. 将数据源名称压入栈底（作为默认数据源）
 * 4. 如果 Service 有 @DataSource 注解，会在栈顶压入新的数据源
 * 5. 请求结束后弹出栈底的默认数据源
 * <p>
 * 注意：
 * - TenantInterceptor 设置的数据源作为"默认数据源"（栈底）
 * - @DataSource 注解设置的数据源会覆盖默认数据源（栈顶）
 * - 栈结构保证了嵌套切换的正确性，无需额外的标记位
 * - 如果数据源不存在，会抛出异常（而不是静默失败）
 */
@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private static final String TENANT_ID_HEADER = "X-Tenant-Id";

    private final DataSourceValidator dataSourceValidator;

    public TenantInterceptor(DataSourceValidator dataSourceValidator) {
        this.dataSourceValidator = dataSourceValidator;
    }

    @Override
    public boolean preHandle(@org.springframework.lang.NonNull HttpServletRequest request,
                             @org.springframework.lang.NonNull HttpServletResponse response,
                             @org.springframework.lang.NonNull Object handler) {
        String tenantId = request.getHeader(TENANT_ID_HEADER);

        if (tenantId != null && !tenantId.isEmpty()) {
            // 验证数据源是否存在
            if (dataSourceValidator.isValid(tenantId)) {
                log.error("数据源 '{}' 不存在。可用数据源: {}",
                        tenantId, dataSourceValidator.getAvailableDataSources());
                throw new BusinessException(
                        String.format("数据源 '%s' 不存在。可用数据源: %s",
                                tenantId, dataSourceValidator.getAvailableDataSources())
                );
            }

            // 压入数据源到栈底
            DataSourceContextHolder.set(tenantId);
            AppCodeContext.set(tenantId);
            log.debug("TenantInterceptor 设置默认数据源: {}", tenantId);
        } else {
            throw new BusinessException("未携带应用编码");
        }

        return true;
    }

    @Override
    public void afterCompletion(@org.springframework.lang.NonNull HttpServletRequest request,
                                @org.springframework.lang.NonNull HttpServletResponse response,
                                @org.springframework.lang.NonNull Object handler,
                                @org.springframework.lang.Nullable Exception ex) {
        // 弹出栈底的数据源
        // 注意：如果有 @DataSource 注解，它们会先被 DataSourceAspect 弹出
        // 这里只弹出 TenantInterceptor 压入的那个元素
        DataSourceContextHolder.clear();
        AppCodeContext.remove();
    }
}
