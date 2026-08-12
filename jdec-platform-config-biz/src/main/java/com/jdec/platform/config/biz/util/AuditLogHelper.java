package com.jdec.platform.config.biz.util;

import com.jdec.platform.config.biz.audit.dto.FieldDiff;
import com.jdec.platform.config.biz.audit.service.DataAuditService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通用审计日志工具类
 *
 * <p>用于各个管理模块的审计日志记录，支持两种模式：
 *
 * <ul>
 *   <li>不带diff比较：用于记录简单操作，如"新增了xxx"、"xxx新增了xxx用户"
 *   <li>带diff比较：用于记录需要对比的操作，如名称变更、有效期变更等
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>
 * // 1. 不带diff比较
 * auditLogHelper.logWithoutCompare("角色管理", "sys_role", "新增了管理员角色", 123L);
 *
 * // 2. 带diff比较
 * auditLogHelper.logWithCompare("角色管理", "sys_role", "修改", 123L, newRoleData);
 *
 * // 3. 删除操作
 * auditLogHelper.logDelete("角色管理", "sys_role", 123L);
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogHelper {

    private final DataAuditService dataAuditService;

    /** 模块名称（固定值） */
    private static final String MODULE = "系统设置";

    /**
     * 记录操作（不带差异对比）
     *
     * <p>适用场景：
     *
     * <ul>
     *   <li>新增操作：operation = "新增了xxx"
     *   <li>删除操作：operation = "删除了xxx"
     *   <li>批量操作：operation = "xxx新增了xxx,xxx,xxx"
     * </ul>
     *
     * @param subModule 子模块名称（如："角色管理"、"权限管理"）
     * @param tableName 表名（如："sys_role"、"sys_permission"）
     * @param operation 操作内容
     * @param dataId 数据ID
     */
    public void logWithoutCompare(
            String subModule, String tableName, String operation, Long dataId) {
        try {
            dataAuditService.auditWithoutCompare(MODULE, subModule, operation, tableName, dataId);
            log.info("审计日志（无diff）: subModule={}, operation={}", subModule, operation);
        } catch (Exception e) {
            log.error("记录审计日志失败（无diff）: subModule={}, operation={}", subModule, operation, e);
        }
    }

    /**
     * 记录操作（带差异对比）
     *
     * <p>适用场景：
     *
     * <ul>
     *   <li>名称变更：对比旧名称和新名称
     *   <li>有效期变更：对比时效信息的变化
     *   <li>配置变更：对比配置项的变化
     * </ul>
     *
     * @param subModule 子模块名称（如："角色管理"、"权限管理"）
     * @param tableName 表名（如："sys_role"、"sys_permission"）
     * @param operation 操作类型（如："修改"）
     * @param dataId 数据ID
     * @param newData 新数据对象（用于diff比较）
     */
    public void logWithCompare(
            String subModule, String tableName, String operation, Long dataId, Object newData) {
        try {
            dataAuditService.auditWithCompare(
                    MODULE, subModule, operation, tableName, dataId, newData);
            log.info(
                    "审计日志（带diff）: subModule={}, operation={}, dataId={}",
                    subModule,
                    operation,
                    dataId);
        } catch (Exception e) {
            log.error(
                    "记录审计日志失败（带diff）: subModule={}, operation={}, dataId={}",
                    subModule,
                    operation,
                    dataId,
                    e);
        }
    }

    /**
     * 记录操作（自定义差异）
     *
     * <p>适用场景：复杂业务逻辑需要手动构建diff数组
     *
     * @param subModule 子模块名称（如："角色管理"、"权限管理"）
     * @param tableName 表名（如："sys_role"、"sys_permission"）
     * @param operation 操作内容（已包含完整描述，如："修改了test角色的邓由由用户的有效期"）
     * @param dataId 数据ID
     * @param diffs 手动构建的字段差异列表
     */
    public void logWithCustomDiff(
            String subModule,
            String tableName,
            String operation,
            Long dataId,
            List<FieldDiff> diffs) {
        try {
            dataAuditService.auditWithCustomDiff(
                    MODULE, subModule, operation, tableName, dataId, diffs);
            log.info("审计日志（自定义diff）: subModule={}, operation={}", subModule, operation);
        } catch (Exception e) {
            log.error("记录审计日志失败（自定义diff）: subModule={}, operation={}", subModule, operation, e);
        }
    }

    /**
     * 记录操作（自定义remark JSON）
     *
     * <p>适用场景：需要自定义 logRemark 格式的复杂审计场景，如权限变更审计。
     *
     * @param subModule 子模块名称（如："角色菜单权限"、"角色交互权限"）
     * @param tableName 表名（如："sys_role_menu"、"sys_role_interaction_permission"）
     * @param operation 操作描述（如："新增权限"、"取消权限"）
     * @param dataId 数据ID
     * @param remarkJson 自定义的 logRemark JSON 字符串
     */
    public void logWithCustomRemark(
            String subModule, String tableName, String operation, Long dataId, String remarkJson) {
        try {
            dataAuditService.auditWithCustomRemark(
                    MODULE, subModule, operation, tableName, dataId, remarkJson);
            log.info("审计日志（自定义remark）: subModule={}, operation={}", subModule, operation);
        } catch (Exception e) {
            log.error("记录审计日志失败（自定义remark）: subModule={}, operation={}", subModule, operation, e);
        }
    }

    /**
     * 记录新增操作（带差异对比，oldValue为空）
     *
     * <p>适用场景：新增数据时，需要记录所有字段的新值
     *
     * @param subModule 子模块名称（如："角色管理"、"权限管理"）
     * @param tableName 表名（如："sys_role"、"sys_permission"）
     * @param operation 操作类型（如："新增"）
     * @param dataId 数据ID
     * @param newData 新增的数据对象
     */
    public void logCreate(
            String subModule, String tableName, String operation, Long dataId, Object newData) {
        try {
            dataAuditService.auditCreate(MODULE, subModule, operation, tableName, dataId, newData);
            log.info("审计日志（新增）: subModule={}, operation={}", subModule, operation);
        } catch (Exception e) {
            log.error("记录新增审计日志失败: subModule={}, operation={}", subModule, operation, e);
        }
    }

    /**
     * 记录删除操作
     *
     * @param subModule 子模块名称（如:"角色管理"、"权限管理"）
     * @param tableName 表名（如："sys_role"、"sys_permission"）
     * @param dataId 数据ID
     */
    public void logDelete(String subModule, String tableName, Long dataId) {
        try {
            dataAuditService.auditDelete(MODULE, subModule, tableName, dataId);
            log.info("审计日志（删除）: subModule={}, dataId={}", subModule, dataId);
        } catch (Exception e) {
            log.error("记录删除审计日志失败: subModule={}, dataId={}", subModule, dataId, e);
        }
    }

    /**
     * 记录删除操作
     *
     * @param subModule 子模块名称（如:"角色管理"、"权限管理"）
     * @param tableName 表名（如："sys_role"、"sys_permission"）
     * @param dataId 数据ID
     * @param entityClass 实体类（用于从快照中提取字段中文名和记录名称）
     */
    public void logDelete(String subModule, String tableName, Long dataId, Class<?> entityClass) {
        try {
            dataAuditService.auditDelete(MODULE, subModule, tableName, dataId, entityClass);
            log.info("审计日志（删除）: subModule={}, dataId={}", subModule, dataId);
        } catch (Exception e) {
            log.error("记录删除审计日志失败: subModule={}, dataId={}", subModule, dataId, e);
        }
    }

    /**
     * 记录批量删除操作
     *
     * @param subModule 子模块名称（如："角色管理"、"权限管理"）
     * @param tableName 表名（如："sys_role"、"sys_permission"）
     * @param dataIds 数据ID列表
     */
    public void logBatchDelete(String subModule, String tableName, List<Long> dataIds) {
        try {
            dataAuditService.auditBatchDelete(MODULE, subModule, tableName, dataIds);
            log.info("审计日志（批量删除）: subModule={}, count={}", subModule, dataIds.size());
        } catch (Exception e) {
            log.error("记录批量删除审计日志失败: subModule={}, count={}", subModule, dataIds.size(), e);
        }
    }
}
