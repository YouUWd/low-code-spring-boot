package com.jdec.platform.config.biz.audit.aspect;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.biz.audit.dto.*;
import com.jdec.platform.config.biz.audit.service.AuditLogSendService;
import com.jdec.platform.config.biz.audit.util.AuditLogFormatter;
import com.jdec.platform.config.biz.audit.util.JsonDiffUtil;
import com.jdec.platform.config.biz.entity.SysDataSnapshot;
import com.jdec.platform.config.biz.service.SysDataSnapshotService;
import com.jdec.platform.shared.audit.annotation.DataAudit;
import com.jdec.platform.shared.audit.enums.EnumDescribable;
import com.jdec.platform.shared.audit.enums.OperationType;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final AuditLogFormatter auditLogFormatter;
    private final AuditLogSendService auditLogSendService;

    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(dataAudit)")
    public Object around(ProceedingJoinPoint joinPoint, DataAudit dataAudit) throws Throwable {
        if (!dataAudit.enabled()) {
            return joinPoint.proceed();
        }

        String tableName = dataAudit.tableName();
        OperationType operationType = dataAudit.operation();

        // 解析子模块（支持从请求参数对象中动态获取，如：#req.category，并结合枚举类转为中文）
        String subModule = resolveSubModule(joinPoint, dataAudit);

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
                    handleCreateAudit(
                            joinPoint, dataAudit, newDataId, tableName, result, subModule);
                    return result;
                }
            }

            // 特殊处理：如果是UPDATE操作但dataId为null（说明是统一保存接口的新增场景）
            // 尝试从返回值中提取dataId，然后走CREATE审计流程
            if (operationType == OperationType.UPDATE && result instanceof Long) {
                Long newDataId = (Long) result;
                if (newDataId != null) {
                    // 从返回的dataId走CREATE审计流程
                    handleCreateAudit(
                            joinPoint, dataAudit, newDataId, tableName, result, subModule);
                    return result;
                }
            }

            // 只记录操作，不对比差异
            if (operationType == OperationType.UPDATE) {
                handleUpdateAuditWithoutCompare(dataAudit, subModule);
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
            handleUpdateAudit(
                    joinPoint, dataAudit, dataId, tableName, oldSnapshot, result, subModule);
        } else if (operationType == OperationType.DELETE) {
            handleDeleteAudit(dataAudit, dataId, tableName, oldSnapshot, subModule);
        } else if (operationType == OperationType.CREATE) {
            handleCreateAudit(joinPoint, dataAudit, dataId, tableName, result, subModule);
        }

        return result;
    }

    /** 处理UPDATE操作审计（无对比，仅记录操作） */
    private void handleUpdateAuditWithoutCompare(DataAudit dataAudit, String subModule) {
        try {
            // 构建审计结果（无差异列表）
            AuditResult auditResult =
                    AuditResult.builder()
                            .module(dataAudit.module())
                            .subModule(subModule)
                            .operation(dataAudit.operation().getDescription())
                            .tableName(dataAudit.tableName())
                            .dataId(null)
                            .diffs(Collections.emptyList())
                            .operateTime(LocalDateTime.now())
                            .build();

            // 注册事务同步回调，事务提交成功后发送审计日志（回滚则不发送）
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                // 仅在事务提交成功后发送审计日志
                                sendAuditLog(auditResult);
                            }

                            @Override
                            public void afterCompletion(int status) {
                                if (status == STATUS_ROLLED_BACK) {
                                    log.warn(
                                            "事务已回滚，审计日志将不被记录: module={}, table={}",
                                            dataAudit.module(),
                                            dataAudit.tableName());
                                }
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
            Object result,
            String subModule) {
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

            // 提取记录名称（修改操作时从旧快照提取，显示修改前的名称）
            String recordName;
            if (isCreate) {
                // 新增操作：从新数据中提取
                recordName = jsonDiffUtil.extractUniqueIdentifier(newData);
            } else {
                // 修改操作：从旧快照提取，显示修改前的名称
                if (oldSnapshot != null && oldSnapshot.getJsonData() != null) {
                    recordName =
                            jsonDiffUtil.extractUniqueIdentifierFromJson(
                                    oldSnapshot.getJsonData(), newData.getClass());
                } else {
                    // 降级：如果没有旧快照，则从新数据提取
                    recordName = jsonDiffUtil.extractUniqueIdentifier(newData);
                }
            }

            // 构建审计结果
            AuditResult auditResult =
                    AuditResult.builder()
                            .module(dataAudit.module())
                            .subModule(subModule)
                            .operation(actualOperation) // 使用自动判断的操作类型
                            .tableName(tableName)
                            .dataId(dataId)
                            .diffs(diffs)
                            .recordName(recordName)
                            .operateTime(LocalDateTime.now())
                            .build();

            // 注册事务同步回调
            // 注意：审计日志在 afterCommit 中发送，仅事务提交成功后才发送（回滚则不发送）
            // 快照更新也在 afterCommit 中执行（事务已提交，快照与日志无强一致性要求）
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                // 事务提交成功后发送审计日志
                                sendAuditLog(auditResult);
                                // 事务提交后更新或创建快照
                                if (isCreate) {
                                    // 新增：创建快照
                                    snapshotService.saveSnapshot(tableName, dataId, newJson);
                                } else {
                                    // 修改：更新快照
                                    updateSnapshot(tableName, dataId, newJson, oldSnapshot);
                                }
                            }

                            @Override
                            public void afterCompletion(int status) {
                                if (status == STATUS_ROLLED_BACK) {
                                    log.warn(
                                            "事务已回滚，审计日志将不被记录: table={}, dataId={}",
                                            tableName,
                                            dataId);
                                }
                            }
                        });
            } else {
                // 没有事务，直接发送并更新快照
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
            Object result,
            String subModule) {
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

            // 提取记录名称
            String recordName = jsonDiffUtil.extractUniqueIdentifier(newData);

            // 构建审计结果（强制使用"新增"操作描述）
            AuditResult auditResult =
                    AuditResult.builder()
                            .module(dataAudit.module())
                            .subModule(subModule)
                            .operation("新增")
                            .tableName(tableName)
                            .dataId(dataId)
                            .diffs(diffs)
                            .recordName(recordName)
                            .operateTime(LocalDateTime.now())
                            .build();

            // 注册事务同步回调
            // 注意：审计日志在 afterCommit 中发送，仅事务提交成功后才发送（回滚则不发送）
            // 快照创建也在 afterCommit 中执行
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                // 事务提交成功后发送审计日志
                                sendAuditLog(auditResult);
                                // 创建快照（检查是否已存在，避免业务代码手动创建后重复）
                                SysDataSnapshot existingSnapshot =
                                        snapshotService.getLatestSnapshot(tableName, dataId);
                                if (existingSnapshot == null) {
                                    snapshotService.saveSnapshot(tableName, dataId, newJson);
                                }
                            }

                            @Override
                            public void afterCompletion(int status) {
                                if (status == STATUS_ROLLED_BACK) {
                                    log.warn(
                                            "事务已回滚，审计日志将不被记录: table={}, dataId={}",
                                            tableName,
                                            dataId);
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
            DataAudit dataAudit,
            Long dataId,
            String tableName,
            SysDataSnapshot oldSnapshot,
            String subModule) {
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

            // 构建删除操作的 FieldDiff 列表（只提取指定的显示字段）
            List<FieldDiff> diffs = Collections.emptyList();
            String oldJson = oldSnapshot.getJsonData();
            String deleteDisplayField = dataAudit.deleteDisplayField();
            Class<?> entityClass = dataAudit.entityClass();

            if (oldJson != null
                    && !oldJson.isEmpty()
                    && deleteDisplayField != null
                    && !deleteDisplayField.isEmpty()
                    && entityClass != null
                    && entityClass != void.class) {
                try {
                    // 从快照中提取指定字段作为删除记录的显示信息
                    diffs =
                            jsonDiffUtil.buildDeleteDiffWithDisplayField(
                                    oldJson, entityClass, deleteDisplayField);
                } catch (Exception e) {
                    log.warn(
                            "解析删除快照JSON失败: table={}, dataId={}, displayField={}",
                            tableName,
                            dataId,
                            deleteDisplayField,
                            e);
                    diffs = Collections.emptyList();
                }
            }

            // 提取记录名称（直接从快照JSON中提取uniqueIdentifier，避免反序列化LocalDateTime等字段失败）
            String recordName = "";
            if (oldJson != null
                    && !oldJson.isEmpty()
                    && dataAudit.entityClass() != null
                    && dataAudit.entityClass() != void.class) {
                recordName =
                        jsonDiffUtil.extractUniqueIdentifierFromJson(
                                oldJson, dataAudit.entityClass());
            }

            // 构建审计结果（所有字段都标记为删除）
            AuditResult auditResult =
                    AuditResult.builder()
                            .module(dataAudit.module())
                            .subModule(subModule)
                            .operation(dataAudit.operation().getDescription())
                            .tableName(tableName)
                            .dataId(dataId)
                            .diffs(diffs)
                            .recordName(recordName)
                            .operateTime(LocalDateTime.now())
                            .build();

            // 注册事务同步回调，事务提交成功后发送审计日志（回滚则不发送）
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                // 仅在事务提交成功后发送审计日志
                                sendAuditLog(auditResult);
                            }

                            @Override
                            public void afterCompletion(int status) {
                                if (status == STATUS_ROLLED_BACK) {
                                    log.warn(
                                            "事务已回滚，审计日志将不被记录: table={}, dataId={}",
                                            tableName,
                                            dataId);
                                }
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

    /**
     * 解析业务子模块。
     *
     * <p>优先使用 subModuleField 的SpEL表达式从请求参数对象中动态获取（如：#req.category）， 结合 subModuleEnumClass
     * 可将枚举code转换为中文描述；解析失败或未指定时回退到静态 subModule。
     */
    private String resolveSubModule(ProceedingJoinPoint joinPoint, DataAudit dataAudit) {
        if (dataAudit.subModuleField() != null && !dataAudit.subModuleField().isEmpty()) {
            try {
                Object value = parseSpelValue(joinPoint, dataAudit.subModuleField());
                if (value != null) {
                    // 结合枚举类将枚举code转换为中文描述
                    String desc = resolveEnumValue(value, dataAudit.subModuleEnumClass());
                    if (desc != null && !desc.isEmpty()) {
                        return desc;
                    }
                    return value.toString();
                }
            } catch (Exception e) {
                log.warn("解析子模块SpEL表达式失败: field={}, 使用静态subModule", dataAudit.subModuleField(), e);
            }
        }
        return dataAudit.subModule();
    }

    /**
     * 使用SpEL解析指定表达式获取实际值
     *
     * @param joinPoint 连接点
     * @param expression SpEL表达式（如：#req.category）
     * @return 解析后的值，失败返回null
     */
    private Object parseSpelValue(ProceedingJoinPoint joinPoint, String expression) {
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
            return parser.parseExpression(expression).getValue(context);
        } catch (Exception e) {
            log.error("解析SpEL表达式失败: expression={}", expression, e);
            return null;
        }
    }

    /** 使用SpEL解析dataIdField获取实际dataId */
    private Long parseDataId(ProceedingJoinPoint joinPoint, String dataIdField) {
        try {
            Object value = parseSpelValue(joinPoint, dataIdField);
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

    /**
     * 将枚举值转换为中文描述（兼容EnumDescribable的code匹配与ordinal匹配）
     *
     * @param value 枚举值（code或ordinal）
     * @param enumClass 枚举类，非枚举或void.class时直接返回null
     * @return 中文描述，未匹配返回null
     */
    private String resolveEnumValue(Object value, Class<?> enumClass) {
        if (value == null || enumClass == null || enumClass == void.class || !enumClass.isEnum()) {
            return null;
        }
        try {
            Object[] enumConstants = enumClass.getEnumConstants();
            if (enumConstants == null || enumConstants.length == 0) {
                return null;
            }
            // 优先按EnumDescribable.getValue()匹配code值
            for (Object enumConstant : enumConstants) {
                if (enumConstant instanceof EnumDescribable) {
                    Object enumCode = ((EnumDescribable) enumConstant).getValue();
                    if (enumValuesEqual(enumCode, value)) {
                        return ((EnumDescribable) enumConstant).getDescription();
                    }
                } else if (enumConstant instanceof Enum<?> e) {
                    // 兼容非EnumDescribable枚举按name匹配
                    if (e.name().equals(value.toString())) {
                        return e.name();
                    }
                }
            }
            // 未匹配到则回退按ordinal匹配
            if (value instanceof Number number) {
                int ordinal = number.intValue();
                if (ordinal >= 0 && ordinal < enumConstants.length) {
                    Object enumConstant = enumConstants[ordinal];
                    if (enumConstant instanceof EnumDescribable) {
                        return ((EnumDescribable) enumConstant).getDescription();
                    }
                    return ((Enum<?>) enumConstant).name();
                }
            }
        } catch (Exception e) {
            log.warn("解析枚举值失败: value={}, enumClass={}", value, enumClass, e);
        }
        return null;
    }

    /** 判断两个枚举值是否相等（兼容Integer/Long等数值类型） */
    private boolean enumValuesEqual(Object a, Object b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        if (a instanceof Number na && b instanceof Number nb) {
            return na.longValue() == nb.longValue();
        }
        return false;
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
     * 发送审计日志到监控服务（非阻塞）。
     *
     * <p>仅组装调用方自定义的字段（单据ID、操作名称、备注），其余信息（登录用户/模拟用户、角色、主体、菜单、模块、IP、浏览器、平台等） 由 {@link
     * AuditLogSendService} 根据当前请求上下文统一补全后发送。
     *
     * <p>注意：此方法在事务提交成功后调用，发送失败仅记录日志，不会影响业务事务。
     */
    private void sendAuditLog(AuditResult auditResult) {
        // 构建controlName（单据操作名称）- 简化为"修改了XX配置"、"新增了XX配置"、"删除了XX配置"
        String controlName =
                auditResult.getOperation()
                        + "了"
                        + (auditResult.getSubModule() != null
                                        && !auditResult.getSubModule().isEmpty()
                                ? auditResult.getSubModule()
                                : "配置");

        // 构建remark（新格式：ModuleAction列表的JSON）
        String remark = "";
        if (auditResult.getDiffs() != null && !auditResult.getDiffs().isEmpty()) {
            List<ModuleAction> moduleActions;
            String moduleName =
                    auditResult.getSubModule() != null && !auditResult.getSubModule().isEmpty()
                            ? auditResult.getSubModule()
                            : "配置";
            String recordName =
                    auditResult.getRecordName() != null ? auditResult.getRecordName() : "";

            // 根据操作类型构建不同的ModuleAction
            if (auditResult.getOperation().equals(OperationType.CREATE.name())
                    || auditResult.getOperation().contains("新增")) {
                moduleActions =
                        auditLogFormatter.buildCreateAction(
                                moduleName, recordName, auditResult.getDiffs());
            } else if (auditResult.getOperation().equals(OperationType.DELETE.name())
                    || auditResult.getOperation().contains("删除")) {
                moduleActions =
                        auditLogFormatter.buildDeleteAction(
                                moduleName, recordName, auditResult.getDiffs());
            } else {
                // 默认为修改操作
                moduleActions =
                        auditLogFormatter.buildUpdateAction(
                                moduleName, recordName, auditResult.getDiffs());
            }

            if (moduleActions != null && !moduleActions.isEmpty()) {
                Map<String, Object> remarkMap = new HashMap<>();
                remarkMap.put("change", moduleActions);
                remark = JSONUtil.toJsonStr(remarkMap);
            }
        }

        // 构建发送上下文，其余信息（用户/角色/主体/菜单/模块/IP等）由 AuditLogSendService 自动补全
        AuditLogSendContext context =
                AuditLogSendContext.builder()
                        .module(auditResult.getModule())
                        .subModule(auditResult.getSubModule())
                        .dataId(auditResult.getDataId())
                        .controlName(controlName)
                        .logRemark(remark)
                        .build();

        auditLogSendService.sendAuditLog(context);
    }
}
