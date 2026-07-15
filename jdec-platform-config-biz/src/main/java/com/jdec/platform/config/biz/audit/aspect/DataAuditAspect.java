package com.jdec.platform.config.biz.audit.aspect;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.biz.audit.dto.AuditResult;
import com.jdec.platform.config.biz.audit.dto.FieldDiff;
import com.jdec.platform.config.biz.audit.util.JsonDiffUtil;
import com.jdec.platform.config.biz.entity.SysDataSnapshot;
import com.jdec.platform.config.biz.service.SysDataSnapshotService;
import com.jdec.platform.shared.audit.annotation.DataAudit;
import com.jdec.platform.shared.audit.enums.OperationType;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 数据审计切面 拦截 @DataAudit 注解的方法，记录数据变更审计日志 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataAuditAspect {

    private final SysDataSnapshotService snapshotService;
    private final JsonDiffUtil jsonDiffUtil;
    private final ObjectMapper objectMapper;

    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(dataAudit)")
    public Object around(ProceedingJoinPoint joinPoint, DataAudit dataAudit) throws Throwable {
        if (!dataAudit.enabled()) {
            return joinPoint.proceed();
        }

        // 解析dataIdField获取实际dataId
        Long dataId = parseDataId(joinPoint, dataAudit.dataIdField());
        if (dataId == null) {
            log.info("新增操作，跳过数据审计: method={}", joinPoint.getSignature().getName());
            return joinPoint.proceed();
        }

        String tableName = dataAudit.tableName();
        OperationType operationType = dataAudit.operation();

        // 查询旧快照
        SysDataSnapshot oldSnapshot = snapshotService.getLatestSnapshot(tableName, dataId);

        // 执行业务方法
        Object result = joinPoint.proceed();

        // 根据操作类型处理审计
        if (operationType == OperationType.UPDATE) {
            handleUpdateAudit(joinPoint, dataAudit, dataId, tableName, oldSnapshot, result);
        } else if (operationType == OperationType.DELETE) {
            handleDeleteAudit(dataAudit, dataId, tableName, oldSnapshot);
        }

        return result;
    }

    /** 处理UPDATE操作审计 */
    private void handleUpdateAudit(
            ProceedingJoinPoint joinPoint,
            DataAudit dataAudit,
            Long dataId,
            String tableName,
            SysDataSnapshot oldSnapshot,
            Object result) {
        try {
            // 获取新数据（从方法参数中获取）
            Object newData = extractNewData(joinPoint);
            if (newData == null) {
                log.warn("数据审计失败: 无法获取新数据, method={}", joinPoint.getSignature().getName());
                return;
            }

            // 转换为JSON
            String newJson = objectMapper.writeValueAsString(newData);
            String oldJson = (oldSnapshot != null) ? oldSnapshot.getJsonData() : null;

            // 比较差异
            List<FieldDiff> diffs;
            if (oldJson == null) {
                // 没有旧快照，视为新增（所有字段都是新值）
                diffs = Collections.emptyList();
            } else {
                // 进行diff比较
                diffs = jsonDiffUtil.compareJsonObjects(oldJson, newJson, newData.getClass());
            }

            // 构建审计结果
            AuditResult auditResult =
                    AuditResult.builder()
                            .module(dataAudit.module())
                            .subModule(dataAudit.subModule())
                            .operation(dataAudit.operation().getDescription())
                            .tableName(tableName)
                            .dataId(dataId)
                            .diffs(diffs)
                            .operateTime(LocalDateTime.now())
                            .build();

            // 注册事务同步回调，在事务提交后发送审计日志
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                // TODO: 调用HTTP接口发送审计日志
                                sendAuditLog(auditResult);

                                // 更新或创建快照（带版本号乐观锁）
                                updateSnapshot(tableName, dataId, newJson, oldSnapshot);
                            }
                        });
            } else {
                // 没有事务，直接发送
                sendAuditLog(auditResult);
                updateSnapshot(tableName, dataId, newJson, oldSnapshot);
            }

        } catch (Exception e) {
            log.error("UPDATE审计处理失败: table={}, dataId={}", tableName, dataId, e);
        }
    }

    /** 处理DELETE操作审计 */
    private void handleDeleteAudit(
            DataAudit dataAudit, Long dataId, String tableName, SysDataSnapshot oldSnapshot) {
        try {
            if (oldSnapshot == null) {
                log.warn("DELETE审计失败: 快照不存在, table={}, dataId={}", tableName, dataId);
                return;
            }

            // 构建审计结果（所有字段都标记为删除）
            AuditResult auditResult =
                    AuditResult.builder()
                            .module(dataAudit.module())
                            .subModule(dataAudit.subModule())
                            .operation(dataAudit.operation().getDescription())
                            .tableName(tableName)
                            .dataId(dataId)
                            .diffs(Collections.emptyList()) // DELETE操作不需要字段级diff
                            .operateTime(LocalDateTime.now())
                            .build();

            // 注册事务同步回调
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                // TODO: 调用HTTP接口发送审计日志
                                sendAuditLog(auditResult);

                                // 删除快照记录
                                snapshotService.removeById(oldSnapshot.getId());
                            }

                            @Override
                            public void afterCompletion(int status) {
                                if (status != STATUS_COMMITTED) {
                                    log.info("事务回滚，取消删除快照: table={}, dataId={}", tableName, dataId);
                                }
                            }
                        });
            } else {
                sendAuditLog(auditResult);
                snapshotService.removeById(oldSnapshot.getId());
            }

        } catch (Exception e) {
            log.error("DELETE审计处理失败: table={}, dataId={}", tableName, dataId, e);
        }
    }

    /** 使用SpEL解析dataIdField获取实际dataId */
    private Long parseDataId(ProceedingJoinPoint joinPoint, String dataIdField) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();

            // 获取方法参数名和参数值
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] paramNames = signature.getParameterNames();
            Object[] paramValues = joinPoint.getArgs();

            // 将参数放入SpEL上下文
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], paramValues[i]);
            }

            // 解析SpEL表达式
            Object value = parser.parseExpression(dataIdField).getValue(context);
            if (value instanceof Long) {
                return (Long) value;
            } else if (value instanceof Integer) {
                return ((Integer) value).longValue();
            } else if (value instanceof String) {
                return Long.parseLong((String) value);
            }
        } catch (Exception e) {
            log.error("解析dataId失败: field={}", dataIdField, e);
        }
        return null;
    }

    /** 提取新数据（从方法参数中获取第一个请求对象） */
    private Object extractNewData(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0) {
            // 通常第一个参数是请求对象
            return args[0];
        }
        return null;
    }

    /** 更新或创建快照（差异化更新，合并前端传入的字段到快照中） */
    private void updateSnapshot(
            String tableName, Long dataId, String newJson, SysDataSnapshot oldSnapshot) {
        try {
            if (oldSnapshot != null) {
                // 差异化更新：将前端传入的字段合并到快照的JSON中
                String mergedJson = mergeJsonData(oldSnapshot.getJsonData(), newJson);

                // 更新快照（带版本号乐观锁）
                oldSnapshot.setJsonData(mergedJson);
                boolean success = snapshotService.updateSnapshotWithVersion(oldSnapshot);
                if (!success) {
                    log.warn("快照更新失败（版本冲突），重试: table={}, dataId={}", tableName, dataId);
                    // 重新查询最新快照并更新
                    SysDataSnapshot latestSnapshot =
                            snapshotService.getLatestSnapshot(tableName, dataId);
                    if (latestSnapshot != null) {
                        String latestMergedJson =
                                mergeJsonData(latestSnapshot.getJsonData(), newJson);
                        latestSnapshot.setJsonData(latestMergedJson);
                        snapshotService.updateSnapshotWithVersion(latestSnapshot);
                    }
                }
            }
        } catch (Exception e) {
            log.error("更新快照失败: table={}, dataId={}", tableName, dataId, e);
        }
    }

    /**
     * 合并JSON数据（将newJson中的字段合并到oldJson中）
     *
     * @param oldJson 旧JSON（快照）
     * @param newJson 新JSON（前端传入）
     * @return 合并后的JSON字符串
     */
    private String mergeJsonData(String oldJson, String newJson) {
        try {
            if (oldJson == null || oldJson.isEmpty()) {
                return newJson;
            }

            // 将两个JSON解析为JsonNode
            var oldNode = objectMapper.readTree(oldJson);
            var newNode = objectMapper.readTree(newJson);

            // 使用ObjectMapper合并：将newNode的字段更新到oldNode中
            var merged = objectMapper.readerForUpdating(oldNode).readValue(newNode);

            return objectMapper.writeValueAsString(merged);
        } catch (Exception e) {
            log.error("合并JSON数据失败: oldJson={}, newJson={}", oldJson, newJson, e);
            // 合并失败时返回新JSON
            return newJson;
        }
    }

    /**
     * 发送审计日志到HTTP接口
     *
     * <p>TODO: 实现HTTP调用逻辑 - 调用审计日志服务的HTTP接口 - 使用RestTemplate或WebClient发送POST请求 - 请求体：AuditResult
     * JSON - 处理失败重试和异常情况
     */
    private void sendAuditLog(AuditResult auditResult) {
        try {
            log.info(
                    "TODO: 发送审计日志到HTTP接口 - module={}, operation={}, table={}, dataId={}, diffs={}",
                    auditResult.getModule(),
                    auditResult.getOperation(),
                    auditResult.getTableName(),
                    auditResult.getDataId(),
                    JSONUtil.toJsonStr(auditResult.getDiffs()));

            // TODO: 实现实际的HTTP调用
            // Example:
            // String auditServiceUrl = "http://audit-service/api/audit/log";
            // HttpHeaders headers = new HttpHeaders();
            // headers.setContentType(MediaType.APPLICATION_JSON);
            // HttpEntity<AuditResult> request = new HttpEntity<>(auditResult, headers);
            // ResponseEntity<Void> response = restTemplate.postForEntity(auditServiceUrl,
            // request, Void.class);
            //
            // if (!response.getStatusCode().is2xxSuccessful()) {
            //     throw new RuntimeException("审计日志发送失败: " + response.getStatusCode());
            // }

        } catch (Exception e) {
            log.error("发送审计日志失败: {}", auditResult, e);
            // 注意：这里抛出异常会导致事务回滚
            throw new RuntimeException("审计日志发送失败", e);
        }
    }
}
