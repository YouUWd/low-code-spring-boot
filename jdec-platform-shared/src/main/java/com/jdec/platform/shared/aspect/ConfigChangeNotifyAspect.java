package com.jdec.platform.shared.aspect;

import com.jdec.platform.shared.annotation.ConfigChangeNotify;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.ConfigChangeContext;
import com.jdec.platform.shared.enums.ChangeType;
import com.jdec.platform.shared.service.ConfigChangeMessagePublisher;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 配置变更通知切面
 *
 * <p>拦截标注了 {@link ConfigChangeNotify} 注解的方法，在方法执行成功后自动发布 Redis 消息
 *
 * <p>{@code @Order(100)} 确保在事务提交之后执行（事务切面默认 {@code @Order(Ordered.LOWEST_PRECEDENCE)}）
 */
@Slf4j
@Aspect
@Order(100)
@Component
@RequiredArgsConstructor
public class ConfigChangeNotifyAspect {

    private final ConfigChangeMessagePublisher messagePublisher;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Pointcut("@annotation(com.jdec.platform.shared.annotation.ConfigChangeNotify)")
    public void configChangeNotifyPointcut() {}

    @Around("configChangeNotifyPointcut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        ConfigChangeNotify annotation = method.getAnnotation(ConfigChangeNotify.class);

        Object result = null;
        Throwable exception = null;

        try {
            // 执行目标方法
            result = point.proceed();
            return result;
        } catch (Throwable e) {
            exception = e;
            throw e;
        } finally {
            // 1. 异常时默认不发（除非 notifyOnException=true）
            boolean shouldNotify = (exception == null) || annotation.notifyOnException();

            // 2. 检查当前事务是否已被标记为 rollback-only
            //    场景：业务代码调用 setRollbackOnly() 后不抛异常，但事务最终仍会回滚
            if (shouldNotify && TransactionSynchronizationManager.isActualTransactionActive()) {
                try {
                    // isRollbackOnly() 返回 true 说明事务即将回滚，跳过消息发布
                    if (TransactionAspectSupport.currentTransactionStatus().isRollbackOnly()) {
                        log.warn(
                                "事务已标记 rollback-only，跳过消息发布: method={}, changeType={}",
                                method.getName(),
                                annotation.changeType());
                        shouldNotify = false;
                    }
                } catch (Exception ignored) {
                    // 获取事务状态失败时保守处理：不影响主流程，继续发消息
                }
            }

            if (shouldNotify) {
                try {
                    publishMessage(annotation, point, result);
                } catch (Exception e) {
                    // 消息发布失败不影响业务，记录日志即可
                    log.error(
                            "发布配置变更消息失败: method={}, changeType={}",
                            method.getName(),
                            annotation.changeType(),
                            e);
                }
            }

            // 无论是否发布消息，都必须清理 ThreadLocal，防止内存泄漏
            ConfigChangeContext.clear();
        }
    }

    /** 发布消息 */
    private void publishMessage(
            ConfigChangeNotify annotation, ProceedingJoinPoint point, Object result) {
        ChangeType changeType = annotation.changeType();
        String roleIdExpr = annotation.roleIdExpr();
        String moduleIdExpr = annotation.moduleIdExpr();

        // 构建 SpEL 上下文
        EvaluationContext context = buildEvaluationContext(point, result);

        // 构建 payload
        Map<String, Object> payload = new HashMap<>();

        // 提取 roleId
        if (StringUtils.hasText(roleIdExpr)) {
            Long roleId = parseExpression(roleIdExpr, context, Long.class);
            if (roleId != null) {
                payload.put("roleId", roleId);
            }
        }

        // 提取 moduleId
        if (StringUtils.hasText(moduleIdExpr)) {
            Long moduleId = parseExpression(moduleIdExpr, context, Long.class);
            if (moduleId != null) {
                payload.put("moduleId", moduleId);
            }
        }

        // 从 ThreadLocal 读取受影响的用户/主体/模块 ID（由 Service 层在操作前/后 set）
        List<Long> affectedUserIds = ConfigChangeContext.getAffectedUserIds();
        List<Long> affectedSubjectIds = ConfigChangeContext.getAffectedSubjectIds();
        List<Long> affectedModuleIds = ConfigChangeContext.getAffectedModuleIds();
        if (!affectedUserIds.isEmpty()) {
            payload.put("affectedUserIds", affectedUserIds);
        }
        if (!affectedSubjectIds.isEmpty()) {
            payload.put("affectedSubjectIds", affectedSubjectIds);
        }
        if (!affectedModuleIds.isEmpty()) {
            payload.put("affectedModuleIds", affectedModuleIds);
        }

        // 从 ThreadLocal 读取自定义 payload 数据（由 Service 层设置）
        Map<String, Object> customPayload = ConfigChangeContext.getCustomPayload();
        if (!customPayload.isEmpty()) {
            payload.putAll(customPayload);
        }

        // 【关键】校验 payload 是否为空
        // 如果 requireNonEmptyPayload=true 且 payload 为空，则不发送消息
        if (annotation.requireNonEmptyPayload() && payload.isEmpty()) {
            log.debug(
                    "payload 为空（可能是新增操作），跳过消息发布: changeType={}, method={}",
                    changeType.getCode(),
                    point.getSignature().getName());
            return;
        }

        // 发布消息
        try {
            Long subjectId = AppContext.getSubjectId();
            String projectNo = AppContext.getProjectNo();

            messagePublisher.publish(subjectId, projectNo, changeType.getCode(), payload);

            log.info(
                    "配置变更消息发布成功: changeType={}, subjectId={}, projectNo={}, payload={}",
                    changeType.getCode(),
                    subjectId,
                    projectNo,
                    payload);
        } catch (Exception e) {
            log.error("发布消息到 Redis 失败", e);
        }
    }

    /** 构建 SpEL 求值上下文 */
    private EvaluationContext buildEvaluationContext(ProceedingJoinPoint point, Object result) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 注册方法参数（#argName 形式）
        MethodSignature signature = (MethodSignature) point.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = point.getArgs();

        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }

        // 注册返回值（#result 形式）
        if (result != null) {
            context.setVariable("result", result);
        }

        return context;
    }

    /** 解析 SpEL 表达式 */
    private <T> T parseExpression(String expression, EvaluationContext context, Class<T> type) {
        try {
            return parser.parseExpression(expression).getValue(context, type);
        } catch (Exception e) {
            log.warn("SpEL 表达式解析失败: expression={}", expression, e);
            return null;
        }
    }
}
