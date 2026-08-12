package com.jdec.platform.config.biz.audit.service;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.biz.audit.dto.AuditLogSendContext;
import com.jdec.platform.config.biz.audit.dto.FieldDiff;
import com.jdec.platform.config.biz.audit.dto.ModuleAction;
import com.jdec.platform.config.biz.audit.util.AuditLogFormatter;
import com.jdec.platform.config.biz.audit.util.JsonDiffUtil;
import com.jdec.platform.config.biz.entity.SysDataSnapshot;
import com.jdec.platform.config.biz.service.SysDataSnapshotService;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 数据审计编程式API服务
 *
 * <p>提供编程式方法，支持用户自己传入参数（dataId, tableName, jsonData等）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataAuditService {

    private final SysDataSnapshotService snapshotService;
    private final JsonDiffUtil jsonDiffUtil;
    private final ObjectMapper objectMapper;
    private final AuditLogFormatter auditLogFormatter;
    private final AuditLogSendService auditLogSendService;

    /**
     * 记录数据变更审计（带差异对比）
     *
     * @param module 业务模块
     * @param subModule 业务子模块（模块名称，如：角色配置、菜单配置）
     * @param operation 操作类型（如：修改、删除）
     * @param tableName 表名
     * @param dataId 数据ID
     * @param newData 新数据对象
     * @param recordName 记录名称（通过@AuditField标注的唯一标识字段值）
     */
    public void auditWithCompare(
            String module,
            String subModule,
            String operation,
            String tableName,
            Long dataId,
            Object newData,
            String recordName) {
        try {
            // 如果 recordName 为空，尝试从对象中自动提取
            if (recordName == null || recordName.isEmpty()) {
                recordName = jsonDiffUtil.extractUniqueIdentifier(newData);
            }

            // 查询旧快照
            SysDataSnapshot oldSnapshot = snapshotService.getLatestSnapshot(tableName, dataId);

            // 转换为JSON
            String newJson = objectMapper.writeValueAsString(newData);
            String oldJson = (oldSnapshot != null) ? oldSnapshot.getJsonData() : null;

            // 比较差异
            List<FieldDiff> diffs;
            if (oldJson == null) {
                diffs = Collections.emptyList();
            } else {
                diffs = jsonDiffUtil.compareJsonObjects(oldJson, newJson, newData.getClass());
            }

            // 如果是update操作（dataId不为空）且diff结果为空，说明没有任何修改，不记录日志
            if (dataId != null && (diffs == null || diffs.isEmpty())) {
                log.debug("数据未发生变更，跳过审计日志: table={}, dataId={}", tableName, dataId);
                return;
            }

            // 发送审计日志
            sendAuditLog(module, subModule, operation, tableName, dataId, diffs, recordName);

            // 更新快照
            updateSnapshot(tableName, dataId, newJson, oldSnapshot);

        } catch (Exception e) {
            log.error("数据审计失败（带对比）: module={}, table={}, dataId={}", module, tableName, dataId, e);
        }
    }

    /**
     * 记录数据变更审计（带差异对比）- 兼容旧版本，recordName为空
     *
     * @deprecated 使用带recordName参数的版本
     */
    @Deprecated
    public void auditWithCompare(
            String module,
            String subModule,
            String operation,
            String tableName,
            Long dataId,
            Object newData) {
        auditWithCompare(module, subModule, operation, tableName, dataId, newData, "");
    }

    /**
     * 记录数据新增审计（生成diff列表，oldValue为空，newValue为新增的值）
     *
     * @param module 业务模块
     * @param subModule 业务子模块（模块名称，如：角色配置、菜单配置）
     * @param operation 操作类型（如：新增）
     * @param tableName 表名
     * @param dataId 数据ID（可选，新增时可能还没有生成ID）
     * @param newData 新数据对象
     * @param recordName 记录名称（通过@AuditField标注的唯一标识字段值）
     */
    public void auditCreate(
            String module,
            String subModule,
            String operation,
            String tableName,
            Long dataId,
            Object newData,
            String recordName) {
        try {
            // 如果 recordName 为空，尝试从对象中自动提取
            if (recordName == null || recordName.isEmpty()) {
                recordName = jsonDiffUtil.extractUniqueIdentifier(newData);
            }

            // 转换为JSON
            String newJson = objectMapper.writeValueAsString(newData);

            // 构建新增操作的diff列表（oldValue为null，newValue为新增的值）
            List<FieldDiff> diffs = jsonDiffUtil.buildCreateDiffs(newJson, newData.getClass());

            // 发送审计日志
            sendAuditLog(module, subModule, operation, tableName, dataId, diffs, recordName);

            // 如果有dataId，创建初始快照
            if (dataId != null) {
                snapshotService.saveSnapshot(tableName, dataId, newJson);
            }

        } catch (Exception e) {
            log.error("数据审计失败（新增）: module={}, table={}, dataId={}", module, tableName, dataId, e);
        }
    }

    /**
     * 记录数据新增审计 - 兼容旧版本，recordName为空
     *
     * @deprecated 使用带recordName参数的版本
     */
    @Deprecated
    public void auditCreate(
            String module,
            String subModule,
            String operation,
            String tableName,
            Long dataId,
            Object newData) {
        auditCreate(module, subModule, operation, tableName, dataId, newData, "");
    }

    /**
     * 记录数据变更审计（不对比差异，仅记录操作）
     *
     * @param module 业务模块
     * @param subModule 业务子模块
     * @param operation 操作类型（如：修改、删除）
     * @param tableName 表名
     * @param dataId 数据ID
     */
    public void auditWithoutCompare(
            String module, String subModule, String operation, String tableName, Long dataId) {
        try {
            // 发送审计日志（无差异）
            sendAuditLog(
                    module, subModule, operation, tableName, dataId, Collections.emptyList(), "");

        } catch (Exception e) {
            log.error("数据审计失败（无对比）: module={}, table={}", module, tableName, e);
        }
    }

    /**
     * 记录数据变更审计（自定义JSON数据）
     *
     * @param module 业务模块
     * @param subModule 业务子模块
     * @param operation 操作类型
     * @param tableName 表名
     * @param dataId 数据ID
     * @param jsonData JSON数据（字符串）
     */
    public void auditWithJson(
            String module,
            String subModule,
            String operation,
            String tableName,
            Long dataId,
            String jsonData) {
        try {
            // 查询旧快照
            SysDataSnapshot oldSnapshot = snapshotService.getLatestSnapshot(tableName, dataId);
            String oldJson = (oldSnapshot != null) ? oldSnapshot.getJsonData() : null;

            // 比较差异（使用通用对象类型）
            List<FieldDiff> diffs;
            if (oldJson == null) {
                diffs = Collections.emptyList();
            } else {
                diffs = jsonDiffUtil.compareJsonObjects(oldJson, jsonData, Object.class);
            }

            // 如果是update操作（dataId不为空）且diff结果为空，说明没有任何修改，不记录日志
            if (dataId != null && (diffs == null || diffs.isEmpty())) {
                log.debug("数据未发生变更，跳过审计日志: table={}, dataId={}", tableName, dataId);
                return;
            }

            // 发送审计日志
            sendAuditLog(module, subModule, operation, tableName, dataId, diffs, "");

            // 更新快照
            updateSnapshot(tableName, dataId, jsonData, oldSnapshot);

        } catch (Exception e) {
            log.error("数据审计失败（JSON）: module={}, table={}, dataId={}", module, tableName, dataId, e);
        }
    }

    /**
     * 删除数据审计
     *
     * @param module 业务模块
     * @param subModule 业务子模块（模块名称，如：角色配置、菜单配置）
     * @param tableName 表名
     * @param dataId 数据ID
     * @param recordName 记录名称（通过@AuditField标注的唯一标识字段值）
     */
    public void auditDelete(
            String module, String subModule, String tableName, Long dataId, String recordName) {
        try {
            // 查询旧快照
            SysDataSnapshot oldSnapshot = snapshotService.getLatestSnapshot(tableName, dataId);
            if (oldSnapshot == null) {
                log.warn("DELETE审计失败: 快照不存在, table={}, dataId={}", tableName, dataId);
                return;
            }

            // 构建删除操作的 FieldDiff 列表（从快照中提取字段，old有值，new为空）
            List<FieldDiff> diffs = Collections.emptyList();
            String oldJson = oldSnapshot.getJsonData();
            if (oldJson != null && !oldJson.isEmpty()) {
                try {
                    // 将快照JSON解析为Map，提取所有字段作为删除记录
                    diffs = jsonDiffUtil.buildDeleteDiffs(oldJson);
                } catch (Exception e) {
                    log.warn("解析删除快照JSON失败: table={}, dataId={}", tableName, dataId, e);
                    diffs = Collections.emptyList();
                }
            }

            // 在事务内立即删除快照记录（与业务删除在同一事务中）
            snapshotService.removeById(oldSnapshot.getId());
            log.debug(
                    "已删除快照记录: table={}, dataId={}, snapshotId={}",
                    tableName,
                    dataId,
                    oldSnapshot.getId());

            // 发送审计日志（在事务提交后发送）
            sendAuditLog(module, subModule, "删除", tableName, dataId, diffs, recordName);

        } catch (Exception e) {
            log.error("DELETE审计失败: module={}, table={}, dataId={}", module, tableName, dataId, e);
            // 抛出异常以触发事务回滚
            throw new RuntimeException(
                    "DELETE审计失败: module=" + module + ", table=" + tableName + ", dataId=" + dataId,
                    e);
        }
    }

    /**
     * 删除数据审计 - 兼容旧版本，recordName为空
     *
     * @deprecated 使用带recordName参数的版本
     */
    @Deprecated
    public void auditDelete(String module, String subModule, String tableName, Long dataId) {
        auditDelete(module, subModule, tableName, dataId, "");
    }

    /**
     * 删除数据审计（带实体类，从快照中提取记录名称和 name 字段作为删除显示字段）
     *
     * @param module 业务模块
     * @param subModule 业务子模块（模块名称，如：角色配置、菜单配置）
     * @param tableName 表名
     * @param dataId 数据ID
     * @param entityClass 实体类（用于解析 @AuditField 字段名）
     */
    public void auditDelete(
            String module, String subModule, String tableName, Long dataId, Class<?> entityClass) {
        try {
            // 查询旧快照
            SysDataSnapshot oldSnapshot = snapshotService.getLatestSnapshot(tableName, dataId);
            if (oldSnapshot == null) {
                log.warn("DELETE审计失败: 快照不存在, table={}, dataId={}", tableName, dataId);
                return;
            }

            // 从快照中提取记录名称（用于 d.name）
            String oldJson = oldSnapshot.getJsonData();
            String recordName = "";
            if (oldJson != null && !oldJson.isEmpty() && entityClass != null) {
                recordName = jsonDiffUtil.extractUniqueIdentifierFromJson(oldJson, entityClass);
            }

            // 构建删除操作的单字段 diff（只保留 name 字段）
            List<FieldDiff> diffs = Collections.emptyList();
            if (oldJson != null && !oldJson.isEmpty() && entityClass != null) {
                try {
                    diffs =
                            jsonDiffUtil.buildDeleteDiffWithDisplayField(
                                    oldJson, entityClass, "name");
                } catch (Exception e) {
                    log.warn("解析删除快照JSON失败: table={}, dataId={}", tableName, dataId, e);
                    diffs = Collections.emptyList();
                }
            }

            // 删除操作的显示名称（d.name）：优先取 name 字段的值，无则回退到唯一标识字段值
            String deleteName = recordName;
            if (diffs != null && !diffs.isEmpty() && diffs.get(0).getOldValue() != null) {
                deleteName = diffs.get(0).getOldValue().toString();
            }

            // 在事务内立即删除快照记录
            snapshotService.removeById(oldSnapshot.getId());
            log.debug(
                    "已删除快照记录: table={}, dataId={}, snapshotId={}",
                    tableName,
                    dataId,
                    oldSnapshot.getId());

            // 发送审计日志
            sendAuditLog(module, subModule, "删除", tableName, dataId, diffs, deleteName);

        } catch (Exception e) {
            log.error("DELETE审计失败: module={}, table={}, dataId={}", module, tableName, dataId, e);
            throw new RuntimeException(
                    "DELETE审计失败: module=" + module + ", table=" + tableName + ", dataId=" + dataId,
                    e);
        }
    }

    /**
     * 批量删除数据审计
     *
     * @param module 业务模块
     * @param subModule 业务子模块
     * @param tableName 表名
     * @param dataIds 数据ID列表
     */
    public void auditBatchDelete(
            String module, String subModule, String tableName, List<Long> dataIds) {
        try {
            if (dataIds == null || dataIds.isEmpty()) {
                log.warn("批量DELETE审计失败: dataIds为空, table={}", tableName);
                return;
            }

            // 批量删除快照记录（在事务内立即删除）
            int deletedCount = snapshotService.deleteSnapshotsByDataIds(tableName, dataIds);
            log.debug(
                    "批量删除快照记录: table={}, dataIds={}, deletedCount={}",
                    tableName,
                    dataIds,
                    deletedCount);

            // 为每个dataId发送审计日志（在事务提交后发送）
            for (Long dataId : dataIds) {
                sendAuditLog(
                        module, subModule, "删除", tableName, dataId, Collections.emptyList(), "");
            }

        } catch (Exception e) {
            log.error(
                    "批量DELETE审计失败: module={}, table={}, dataIds={}", module, tableName, dataIds, e);
            // 抛出异常以触发事务回滚
            throw new RuntimeException(
                    "批量DELETE审计失败: module=" + module + ", table=" + tableName, e);
        }
    }

    /**
     * 记录数据变更审计（使用自定义diff）
     *
     * @param module 业务模块
     * @param subModule 业务子模块
     * @param operation 操作描述（如:"修改了test角色的邓由由用户的有效期"）
     * @param tableName 表名
     * @param dataId 数据ID
     * @param diffs 自定义的字段差异列表
     */
    public void auditWithCustomDiff(
            String module,
            String subModule,
            String operation,
            String tableName,
            Long dataId,
            List<FieldDiff> diffs) {
        try {
            // 发送审计日志
            sendAuditLog(module, subModule, operation, tableName, dataId, diffs, "");

        } catch (Exception e) {
            log.error("数据审计失败（自定义diff）: module={}, table={}", module, tableName, e);
        }
    }

    /**
     * 记录数据变更审计（使用自定义remark JSON）
     *
     * <p>适用场景：需要自定义 logRemark 格式的复杂审计场景，如权限变更审计。 remarkJson 需为合法的 JSON
     * 字符串，格式如：{"change":[{"name":"角色名","mId":"","actions":[...]}]}
     *
     * @param module 业务模块
     * @param subModule 业务子模块
     * @param operation 操作描述（如："新增权限"、"取消权限"）
     * @param tableName 表名
     * @param dataId 数据ID
     * @param remarkJson 自定义的 logRemark JSON 字符串
     */
    public void auditWithCustomRemark(
            String module,
            String subModule,
            String operation,
            String tableName,
            Long dataId,
            String remarkJson) {
        try {
            sendAuditLogWithRemark(module, subModule, operation, tableName, dataId, remarkJson);
        } catch (Exception e) {
            log.error("数据审计失败（自定义remark）: module={}, table={}", module, tableName, e);
        }
    }

    private void sendAuditLog(
            String module,
            String subModule,
            String operation,
            String tableName,
            Long dataId,
            List<FieldDiff> diffs,
            String recordName) {

        // 构建logContent - 简化为"修改了XX配置"、"新增了XX配置"、"删除了XX配置"
        String controlName;
        if (operation.contains("了")) {
            // 已经是完整描述（如："新增了test角色"），直接使用
            controlName = operation;
        } else if (operation.startsWith("新增：") || operation.startsWith("取消：")) {
            // 操作已是完整描述（如："取消：超级管理员->业务菜单权限->学生详情"），直接使用
            controlName = operation;
        } else {
            // 只是动词（如："修改"、"删除"、"新增"），需要拼接
            controlName =
                    operation
                            + "了"
                            + (subModule != null && !subModule.isEmpty() ? subModule : "配置");
        }

        // 构建remark（新格式：ModuleAction列表的JSON）
        String remark = "";
        if (diffs != null && !diffs.isEmpty()) {
            List<ModuleAction> moduleActions = null;
            String moduleName = subModule != null && !subModule.isEmpty() ? subModule : "配置";

            // 根据操作类型构建不同的ModuleAction
            if (operation.equals("新增") || operation.contains("新增")) {
                moduleActions = auditLogFormatter.buildCreateAction(moduleName, recordName, diffs);
            } else if (operation.equals("删除") || operation.contains("删除")) {
                moduleActions = auditLogFormatter.buildDeleteAction(moduleName, recordName, diffs);
            } else {
                // 默认为修改操作
                moduleActions = auditLogFormatter.buildUpdateAction(moduleName, recordName, diffs);
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
                        .module(module)
                        .subModule(subModule)
                        .dataId(dataId)
                        .controlName(controlName)
                        .logRemark(remark)
                        .build();

        sendAuditLogRequest(context);
    }

    /** 发送审计日志（使用自定义remark） */
    private void sendAuditLogWithRemark(
            String module,
            String subModule,
            String operation,
            String tableName,
            Long dataId,
            String remarkJson) {

        // 构建logContent
        String controlName;
        if (operation.contains("了")) {
            controlName = operation;
        } else {
            controlName =
                    operation
                            + "了"
                            + (subModule != null && !subModule.isEmpty() ? subModule : "配置");
        }

        // 使用自定义remark
        String remark = remarkJson != null ? remarkJson : "";

        // 构建发送上下文，其余信息（用户/角色/主体/菜单/模块/IP等）由 AuditLogSendService 自动补全
        AuditLogSendContext context =
                AuditLogSendContext.builder()
                        .module(module)
                        .subModule(subModule)
                        .dataId(dataId)
                        .controlName(controlName)
                        .logRemark(remark)
                        .build();

        sendAuditLogRequest(context);
    }

    /**
     * 注册事务同步，在事务提交成功后发送审计日志（非阻塞，失败不影响业务事务）。
     *
     * <p>仅事务提交成功后才发送审计日志；如果业务方法抛出未捕获异常导致事务回滚，则不会发送。
     *
     * @param context 审计日志发送上下文
     */
    private void sendAuditLogRequest(AuditLogSendContext context) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            auditLogSendService.sendAuditLog(context);
                        }

                        @Override
                        public void afterCompletion(int status) {
                            if (status == STATUS_ROLLED_BACK) {
                                log.info("事务已回滚，跳过审计日志: subModule={}", context.getSubModule());
                            }
                        }
                    });
        } else {
            auditLogSendService.sendAuditLog(context);
        }
    }

    /** 更新快照 */
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

    /** 合并JSON数据 */
    private String mergeJsonData(String oldJson, String newJson) {
        try {
            if (oldJson == null || oldJson.isEmpty()) {
                return newJson;
            }

            var oldNode = objectMapper.readTree(oldJson);
            var newNode = objectMapper.readTree(newJson);
            var merged = objectMapper.readerForUpdating(oldNode).readValue(newNode);

            return objectMapper.writeValueAsString(merged);
        } catch (Exception e) {
            log.error("合并JSON数据失败: oldJson={}, newJson={}", oldJson, newJson, e);
            return newJson;
        }
    }
}
