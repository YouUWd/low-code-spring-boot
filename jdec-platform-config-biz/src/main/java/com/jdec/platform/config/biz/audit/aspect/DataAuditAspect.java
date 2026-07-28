package com.jdec.platform.config.biz.audit.aspect;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.biz.audit.dto.AuditLogContent;
import com.jdec.platform.config.biz.audit.dto.AuditLogRequest;
import com.jdec.platform.config.biz.audit.dto.AuditResult;
import com.jdec.platform.config.biz.audit.dto.FieldDiff;
import com.jdec.platform.config.biz.audit.util.JsonDiffUtil;
import com.jdec.platform.config.biz.entity.SysDataSnapshot;
import com.jdec.platform.config.biz.service.SysDataSnapshotService;
import com.jdec.platform.shared.audit.annotation.DataAudit;
import com.jdec.platform.shared.audit.enums.OperationType;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.security.SecurityUtils;
import com.jdec.platform.shared.security.model.LoginUser;
import com.jdec.platform.shared.utils.RequestContextUtils;
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

        String tableName = dataAudit.tableName();
        OperationType operationType = dataAudit.operation();

        // 解析dataIdField获取实际dataId
        Long dataId = null;
        if (dataAudit.dataIdField() != null && !dataAudit.dataIdField().isEmpty()) {
            dataId = parseDataId(joinPoint, dataAudit.dataIdField());
        }

        // 如果没有dataId，且不强制对比，则只记录操作不对比差异
        if (dataId == null && !dataAudit.forceCompare()) {
            // 执行业务方法
            Object result = joinPoint.proceed();

            // 方法执行后重新解析dataId（方法内部可能在新增后设置了request.id）
            if (dataAudit.dataIdField() != null && !dataAudit.dataIdField().isEmpty()) {
                Long newDataId = parseDataId(joinPoint, dataAudit.dataIdField());
                if (newDataId != null) {
                    handleCreateAudit(joinPoint, dataAudit, newDataId, tableName, result);
                    return result;
                }
            }

            // 特殊处理：如果是UPDATE操作但dataId为null（说明是统一保存接口的新增场景）
            // 尝试从返回值中提取dataId，然后走CREATE审计流程
            if (operationType == OperationType.UPDATE && result instanceof Long) {
                Long newDataId = (Long) result;
                if (newDataId != null) {
                    // 从返回的dataId走CREATE审计流程
                    handleCreateAudit(joinPoint, dataAudit, newDataId, tableName, result);
                    return result;
                }
            }

            // 只记录操作，不对比差异
            if (operationType == OperationType.UPDATE) {
                handleUpdateAuditWithoutCompare(dataAudit);
            }
            return result;
        }

        // 有dataId的情况，按原逻辑处理
        // 查询旧快照
        SysDataSnapshot oldSnapshot =
                (dataId != null) ? snapshotService.getLatestSnapshot(tableName, dataId) : null;

        // 执行业务方法
        Object result = joinPoint.proceed();

        // 根据操作类型处理审计
        if (operationType == OperationType.UPDATE) {
            handleUpdateAudit(joinPoint, dataAudit, dataId, tableName, oldSnapshot, result);
        } else if (operationType == OperationType.DELETE) {
            handleDeleteAudit(dataAudit, dataId, tableName, oldSnapshot);
        } else if (operationType == OperationType.CREATE) {
            handleCreateAudit(joinPoint, dataAudit, dataId, tableName, result);
        }

        return result;
    }

    /** 处理UPDATE操作审计（无对比，仅记录操作） */
    private void handleUpdateAuditWithoutCompare(DataAudit dataAudit) {
        try {
            // 构建审计结果（无差异列表）
            AuditResult auditResult =
                    AuditResult.builder()
                            .module(dataAudit.module())
                            .subModule(dataAudit.subModule())
                            .operation(dataAudit.operation().getDescription())
                            .tableName(dataAudit.tableName())
                            .dataId(null)
                            .diffs(Collections.emptyList())
                            .operateTime(LocalDateTime.now())
                            .build();

            // 注册事务同步回调
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                sendAuditLog(auditResult);
                            }
                        });
            } else {
                sendAuditLog(auditResult);
            }

        } catch (Exception e) {
            log.error("UPDATE审计处理失败（无对比）: table={}", dataAudit.tableName(), e);
        }
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

            // 判断是新增还是修改
            boolean isCreate = (oldSnapshot == null);
            String actualOperation = isCreate ? "新增" : dataAudit.operation().getDescription();

            // 比较差异
            List<FieldDiff> diffs;
            if (isCreate) {
                // 没有旧快照，视为新增（生成新增的diff列表，oldValue为null，newValue为实际值）
                diffs = jsonDiffUtil.buildCreateDiffs(newJson, newData.getClass());
            } else {
                // 进行diff比较（修改操作）
                diffs = jsonDiffUtil.compareJsonObjects(oldJson, newJson, newData.getClass());
            }

            // 如果是update操作（非新增）且diff结果为空，说明没有任何修改，不记录日志
            if (!isCreate && dataId != null && (diffs == null || diffs.isEmpty())) {
                log.debug("数据未发生变更，跳过审计日志: table={}, dataId={}", tableName, dataId);
                return;
            }

            // 构建审计结果
            AuditResult auditResult =
                    AuditResult.builder()
                            .module(dataAudit.module())
                            .subModule(dataAudit.subModule())
                            .operation(actualOperation) // 使用自动判断的操作类型
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
                                if (isCreate) {
                                    // 新增：创建快照
                                    snapshotService.saveSnapshot(tableName, dataId, newJson);
                                } else {
                                    // 修改：更新快照
                                    updateSnapshot(tableName, dataId, newJson, oldSnapshot);
                                }
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

    /** 处理CREATE操作审计 */
    private void handleCreateAudit(
            ProceedingJoinPoint joinPoint,
            DataAudit dataAudit,
            Long dataId,
            String tableName,
            Object result) {
        try {
            // 获取新数据（从方法参数中获取）
            Object newData = extractNewData(joinPoint);
            if (newData == null) {
                log.warn("CREATE审计失败: 无法获取新数据, method={}", joinPoint.getSignature().getName());
                return;
            }

            // 转换为JSON
            String newJson = objectMapper.writeValueAsString(newData);

            // 构建新增的diff列表（所有字段都是新增）
            List<FieldDiff> diffs = jsonDiffUtil.buildCreateDiffs(newJson, newData.getClass());

            // 构建审计结果（强制使用"新增"操作描述）
            AuditResult auditResult =
                    AuditResult.builder()
                            .module(dataAudit.module())
                            .subModule(dataAudit.subModule())
                            .operation("新增")
                            .tableName(tableName)
                            .dataId(dataId)
                            .diffs(diffs)
                            .operateTime(LocalDateTime.now())
                            .build();

            // 注册事务同步回调，在事务提交后发送审计日志并创建快照
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                sendAuditLog(auditResult);
                                // 创建快照（检查是否已存在，避免业务代码手动创建后重复）
                                SysDataSnapshot existingSnapshot =
                                        snapshotService.getLatestSnapshot(tableName, dataId);
                                if (existingSnapshot == null) {
                                    snapshotService.saveSnapshot(tableName, dataId, newJson);
                                }
                            }
                        });
            } else {
                // 没有事务，直接发送
                sendAuditLog(auditResult);
                SysDataSnapshot existingSnapshot =
                        snapshotService.getLatestSnapshot(tableName, dataId);
                if (existingSnapshot == null) {
                    snapshotService.saveSnapshot(tableName, dataId, newJson);
                }
            }

        } catch (Exception e) {
            log.error("CREATE审计处理失败: table={}, dataId={}", tableName, dataId, e);
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

            // 在事务内立即删除快照记录（与业务删除在同一事务中）
            snapshotService.removeById(oldSnapshot.getId());
            log.debug(
                    "已删除快照记录: table={}, dataId={}, snapshotId={}",
                    tableName,
                    dataId,
                    oldSnapshot.getId());

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

            // 注册事务同步回调，在事务提交后才发送审计日志
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                // 事务提交后发送审计日志
                                sendAuditLog(auditResult);
                            }
                        });
            } else {
                // 没有事务，直接发送
                sendAuditLog(auditResult);
            }

        } catch (Exception e) {
            log.error("DELETE审计处理失败: table={}, dataId={}", tableName, dataId, e);
            // 注意：这里不捕获异常，让其向上传播以触发事务回滚
            throw new RuntimeException(
                    "DELETE审计处理失败: table=" + tableName + ", dataId=" + dataId, e);
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
     * <p>封装为统一的HTTP请求格式，从 UserContext 获取用户信息
     */
    private void sendAuditLog(AuditResult auditResult) {
        try {
            // 从 UserContext 获取登录用户信息
            LoginUser loginUser = SecurityUtils.getLoginUser();

            // 构建logContent
            String operatorResult =
                    auditResult.getOperation()
                            + "了"
                            + (auditResult.getSubModule() != null
                                            && !auditResult.getSubModule().isEmpty()
                                    ? auditResult.getSubModule()
                                    : "配置");

            AuditLogContent logContent =
                    AuditLogContent.builder()
                            .module(auditResult.getModule())
                            .orderId(
                                    auditResult.getSubModule() != null
                                                    && !auditResult.getSubModule().isEmpty()
                                            ? auditResult.getSubModule()
                                            : auditResult.getTableName())
                            .hrefUrl(loginUser != null ? loginUser.getHrefUrl() : "")
                            .controlName(operatorResult)
                            .build();

            // 构建logRemark（字段差异JSON）
            String logRemark = "";
            if (auditResult.getDiffs() != null && !auditResult.getDiffs().isEmpty()) {
                logRemark = JSONUtil.toJsonStr(auditResult.getDiffs());
            }

            // 从 UserContext 获取用户信息和模拟登录标识
            Long loginUserId =
                    loginUser != null ? loginUser.getRealUserId() : AppContext.getUserId();
            Long roleId = loginUser != null ? loginUser.getRoleId() : null;
            boolean isMockLogin =
                    loginUser != null && Boolean.TRUE.equals(loginUser.getIsMockLogin());
            Long mockUserId = isMockLogin && loginUser != null ? loginUser.getMockUserId() : null;

            // 获取请求上下文信息
            String ip = RequestContextUtils.getClientIp();
            String browser = RequestContextUtils.getBrowser();
            String platform = RequestContextUtils.getPlatform();

            // 构建HTTP请求体
            AuditLogRequest request =
                    AuditLogRequest.builder()
                            .logType("operation-log")
                            .subjectId(
                                    loginUser != null
                                            ? loginUser.getSubjectId()
                                            : AppContext.getSubjectId())
                            .loginUserId(loginUserId)
                            .loginRoleId(roleId)
                            .mockFlag(isMockLogin ? 1 : 0)
                            .mockUserId(mockUserId != null ? mockUserId : 0L)
                            .mockRoleId(0L) // 暂时为0，后续可扩展
                            .ip(ip != null ? ip : "")
                            .browser(browser != null ? browser : "")
                            .platForm(platform != null ? platform : "")
                            .logContent(JSONUtil.toJsonStr(logContent))
                            .logRemark(logRemark)
                            .build();

            log.info("发送审计日志到HTTP接口 - request={}", JSONUtil.toJsonStr(request));

            // TODO: 实现实际的HTTP调用
            // Example:
            // String auditServiceUrl = "http://audit-service/api/audit/log";
            // HttpHeaders headers = new HttpHeaders();
            // headers.setContentType(MediaType.APPLICATION_JSON);
            // HttpEntity<AuditLogRequest> httpRequest = new HttpEntity<>(request, headers);
            // ResponseEntity<Void> response = restTemplate.postForEntity(auditServiceUrl,
            // httpRequest, Void.class);
            //
            // if (!response.getStatusCode().is2xxSuccessful()) {
            //     throw new RuntimeException("审计日志发送失败: " + response.getStatusCode());
            // }

        } catch (Exception e) {
            log.error("发送审计日志失败: {}", auditResult, e);
            // 注意：这里不抛出异常，避免影响业务流程
        }
    }
}
