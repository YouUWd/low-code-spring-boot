package com.jdec.platform.config.biz.audit.service;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.biz.audit.dto.AuditLogContent;
import com.jdec.platform.config.biz.audit.dto.AuditLogRequest;
import com.jdec.platform.config.biz.audit.dto.FieldDiff;
import com.jdec.platform.config.biz.audit.util.JsonDiffUtil;
import com.jdec.platform.config.biz.entity.SysDataSnapshot;
import com.jdec.platform.config.biz.service.SysDataSnapshotService;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.security.SecurityUtils;
import com.jdec.platform.shared.utils.RequestContextUtils;
import java.util.Collections;
import java.util.List;
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

    /**
     * 记录数据变更审计（带差异对比）
     *
     * @param module 业务模块
     * @param subModule 业务子模块
     * @param operation 操作类型（如：修改、删除）
     * @param tableName 表名
     * @param dataId 数据ID
     * @param newData 新数据对象
     */
    public void auditWithCompare(
            String module,
            String subModule,
            String operation,
            String tableName,
            Long dataId,
            Object newData) {
        try {
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
            sendAuditLog(module, subModule, operation, tableName, dataId, diffs);

            // 更新快照
            updateSnapshot(tableName, dataId, newJson, oldSnapshot);

        } catch (Exception e) {
            log.error("数据审计失败（带对比）: module={}, table={}, dataId={}", module, tableName, dataId, e);
        }
    }

    /**
     * 记录数据新增审计（生成diff列表，oldValue为空，newValue为新增的值）
     *
     * @param module 业务模块
     * @param subModule 业务子模块
     * @param operation 操作类型（如：新增）
     * @param tableName 表名
     * @param dataId 数据ID（可选，新增时可能还没有生成ID）
     * @param newData 新数据对象
     */
    public void auditCreate(
            String module,
            String subModule,
            String operation,
            String tableName,
            Long dataId,
            Object newData) {
        try {
            // 转换为JSON
            String newJson = objectMapper.writeValueAsString(newData);

            // 构建新增操作的diff列表（oldValue为null，newValue为新增的值）
            List<FieldDiff> diffs = jsonDiffUtil.buildCreateDiffs(newJson, newData.getClass());

            // 发送审计日志
            sendAuditLog(module, subModule, operation, tableName, dataId, diffs);

            // 如果有dataId，创建初始快照
            if (dataId != null) {
                snapshotService.saveSnapshot(tableName, dataId, newJson);
            }

        } catch (Exception e) {
            log.error("数据审计失败（新增）: module={}, table={}, dataId={}", module, tableName, dataId, e);
        }
    }

    /**
     * 记录数据变更审计（不对比差异，仅记录操作）
     *
     * @param module 业务模块
     * @param subModule 业务子模块
     * @param operation 操作类型（如：修改、删除）
     * @param tableName 表名
     */
    public void auditWithoutCompare(
            String module, String subModule, String operation, String tableName) {
        try {
            // 发送审计日志（无差异）
            sendAuditLog(module, subModule, operation, tableName, null, Collections.emptyList());

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
            sendAuditLog(module, subModule, operation, tableName, dataId, diffs);

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
     * @param subModule 业务子模块
     * @param tableName 表名
     * @param dataId 数据ID
     */
    public void auditDelete(String module, String subModule, String tableName, Long dataId) {
        try {
            // 查询旧快照
            SysDataSnapshot oldSnapshot = snapshotService.getLatestSnapshot(tableName, dataId);
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

            // 发送审计日志（在事务提交后发送）
            sendAuditLog(module, subModule, "删除", tableName, dataId, Collections.emptyList());

        } catch (Exception e) {
            log.error("DELETE审计失败: module={}, table={}, dataId={}", module, tableName, dataId, e);
            // 抛出异常以触发事务回滚
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
                sendAuditLog(module, subModule, "删除", tableName, dataId, Collections.emptyList());
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
     * @param diffs 自定义的字段差异列表
     */
    public void auditWithCustomDiff(
            String module,
            String subModule,
            String operation,
            String tableName,
            List<FieldDiff> diffs) {
        try {
            // 发送审计日志
            sendAuditLog(module, subModule, operation, tableName, null, diffs);

        } catch (Exception e) {
            log.error("数据审计失败（自定义diff）: module={}, table={}", module, tableName, e);
        }
    }

    /** 发送审计日志 */
    private void sendAuditLog(
            String module,
            String subModule,
            String operation,
            String tableName,
            Long dataId,
            List<FieldDiff> diffs) {

        // 构建logContent
        String controlName;
        if (operation.contains("了")) {
            // 已经是完整描述（如："新增了test角色"），直接使用
            controlName = operation;
        } else {
            // 只是动词（如："修改"、"删除"），需要拼接
            controlName =
                    operation
                            + "了"
                            + (subModule != null && !subModule.isEmpty() ? subModule : "配置");
        }

        // 获取 hrefUrl
        String hrefUrl = SecurityUtils.getHrefUrl();

        AuditLogContent logContent =
                AuditLogContent.builder()
                        .module(module)
                        .orderId(subModule != null && !subModule.isEmpty() ? subModule : tableName)
                        .hrefUrl(hrefUrl != null ? hrefUrl : "")
                        .controlName(controlName)
                        .build();

        // 构建logRemark（字段差异JSON）
        String logRemark = "";
        if (diffs != null && !diffs.isEmpty()) {
            logRemark = JSONUtil.toJsonStr(diffs);
        }

        // 判断是否模拟登录
        boolean isMockLogin = SecurityUtils.isMockLogin();
        Long currentUserId = AppContext.getUserId();
        Long currentRoleId = SecurityUtils.getRoleId();

        // 构建HTTP请求体
        AuditLogRequest request =
                AuditLogRequest.builder()
                        .logType("operation-log")
                        .subjectId(AppContext.getSubjectId())
                        .loginUserId(isMockLogin ? SecurityUtils.getRealUserId() : currentUserId)
                        .loginRoleId(isMockLogin ? null : currentRoleId)
                        .mockFlag(isMockLogin ? 1 : 0)
                        .mockUserId(isMockLogin ? SecurityUtils.getMockUserId() : null)
                        .mockRoleId(isMockLogin ? currentRoleId : null)
                        .ip(RequestContextUtils.getClientIp())
                        .browser(RequestContextUtils.getBrowser())
                        .platForm(RequestContextUtils.getPlatform())
                        .logContent(JSONUtil.toJsonStr(logContent))
                        .logRemark(logRemark)
                        .build();

        // 在事务提交后发送
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            doSendAuditLog(request);
                        }
                    });
        } else {
            doSendAuditLog(request);
        }
    }

    /** 实际发送HTTP请求 */
    private void doSendAuditLog(AuditLogRequest request) {
        try {
            log.info("TODO: 发送审计日志到HTTP接口 - request={}", JSONUtil.toJsonStr(request));

            // TODO: 实现实际的HTTP调用
            // String auditServiceUrl = "http://audit-service/api/audit/log";
            // HttpHeaders headers = new HttpHeaders();
            // headers.setContentType(MediaType.APPLICATION_JSON);
            // HttpEntity<AuditLogRequest> httpRequest = new HttpEntity<>(request, headers);
            // ResponseEntity<Void> response = restTemplate.postForEntity(auditServiceUrl,
            // httpRequest, Void.class);

        } catch (Exception e) {
            log.error("发送审计日志失败: {}", request, e);
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
