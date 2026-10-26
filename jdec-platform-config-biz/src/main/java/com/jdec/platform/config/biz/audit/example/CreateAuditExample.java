package com.jdec.platform.config.biz.audit.example;

import com.jdec.platform.config.biz.util.AuditLogHelper;
import com.jdec.platform.shared.audit.annotation.AuditField;
import com.jdec.platform.shared.audit.annotation.DataAudit;
import com.jdec.platform.shared.audit.enums.FieldType;
import com.jdec.platform.shared.audit.enums.OperationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 新增操作审计使用示例
 *
 * <p>演示如何记录新增操作的审计日志，新增操作的diff列表中oldValue为空，newValue为前端传入的对象的每个字段
 */
@Service
@RequiredArgsConstructor
public class CreateAuditExample {

    private final AuditLogHelper auditLogHelper;

    // ==================== 方式1：使用注解式审计 ====================

    /**
     * 示例1：使用 @DataAudit 注解记录新增操作
     *
     * <p>优点：声明式，代码简洁 <br>
     * 注意：operation 设置为 OperationType.CREATE
     */
    @DataAudit(
            module = "系统设置",
            subModule = "角色管理",
            operation = OperationType.CREATE,
            tableName = "sys_role",
            dataIdField = "#result", // 方法返回值是新创建的ID
            enabled = true)
    @Transactional(rollbackFor = Exception.class)
    public Long createRole(RoleCreateReq request) {
        // 1. 执行新增逻辑
        // SysRole role = BeanUtil.toBean(request, SysRole.class);
        // roleMapper.insert(role);
        // Long newId = role.getId();

        Long newId = 123L; // 模拟返回的新ID

        // 2. 审计日志自动记录（AOP切面自动处理）
        // - 获取方法参数（request）
        // - 将request转为JSON
        // - 构建diff列表：所有字段的oldValue=null, newValue=request的字段值
        // - 发送审计日志
        // - 创建快照（tableName + dataId + jsonData）

        return newId;
    }

    // ==================== 方式2：使用编程式审计 ====================

    /**
     * 示例2：使用 AuditLogHelper.logCreate 记录新增操作（带diff对比）
     *
     * <p>优点：灵活，适合复杂业务逻辑 <br>
     * 场景：无法通过注解获取dataId、需要自定义审计逻辑
     */
    @Transactional(rollbackFor = Exception.class)
    public void createRoleWithHelper(RoleCreateReq request) {
        // 1. 执行新增逻辑
        // SysRole role = BeanUtil.toBean(request, SysRole.class);
        // roleMapper.insert(role);
        // Long newId = role.getId();

        Long newId = 456L; // 模拟返回的新ID

        // 2. 手动记录审计日志（带diff对比）
        auditLogHelper.logCreate(
                "角色管理", // subModule
                "sys_role", // tableName
                "新增", // operation（可选，默认"新增"）
                newId, // dataId
                request // newData（用于生成diff）
                );

        // diff列表生成规则：
        // - 遍历request对象的所有字段（带@AuditField注解的）
        // - 每个字段生成一个FieldDiff：
        //   - fieldName: @AuditField.name
        //   - fieldCode: 字段名
        //   - oldValue: null
        //   - newValue: request字段值
        //   - fieldType: @AuditField.type
    }

    /**
     * 示例3：使用 AuditLogHelper.logWithoutCompare 记录新增操作（不带diff）
     *
     * <p>适用场景：只记录操作，不需要详细的字段变更信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void createRoleWithoutCompare(RoleCreateReq request) {
        // 1. 执行新增逻辑
        Long newId = 789L; // 模拟返回的新ID

        // 2. 只记录操作，不生成diff列表
        auditLogHelper.logWithoutCompare(
                "角色管理", // subModule
                "sys_role", // tableName
                "新增了" + request.getRoleName() + "角色", // operation（完整描述）
                newId // dataId
                );
    }

    // ==================== 方式3：批量新增 ====================

    /**
     * 示例4：批量新增操作
     *
     * <p>方案A：为每个新增记录单独记录审计日志
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchCreateRolesIndividually(java.util.List<RoleCreateReq> requests) {
        for (RoleCreateReq request : requests) {
            // 1. 执行新增
            Long newId = 100L; // 模拟

            // 2. 为每条记录生成审计日志
            auditLogHelper.logCreate("角色管理", "sys_role", "新增", newId, request);
        }
    }

    /**
     * 示例5：批量新增操作 - 只记录一条汇总日志
     *
     * <p>方案B：只记录一条汇总的审计日志，不记录详细diff
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchCreateRolesSummary(java.util.List<RoleCreateReq> requests) {
        // 1. 执行批量新增
        // batchInsert(requests);

        // 2. 只记录一条汇总日志
        String roleNames =
                requests.stream()
                        .map(RoleCreateReq::getRoleName)
                        .collect(java.util.stream.Collectors.joining("、"));

        // 汇总日志为多条记录的聚合，无单一主键，dataId 传 null
        auditLogHelper.logWithoutCompare(
                "角色管理", "sys_role", "批量新增了" + roleNames + "等" + requests.size() + "个角色", null);
    }

    // ==================== DTO定义 ====================

    /** 角色新增请求 */
    @Data
    public static class RoleCreateReq {

        @AuditField(name = "角色名称")
        @Schema(description = "角色名称")
        private String roleName;

        @AuditField(name = "角色编码")
        @Schema(description = "角色编码")
        private String roleCode;

        @AuditField(
                name = "所属部门",
                type = FieldType.RELATION,
                target = "sys_department",
                idField = "id",
                nameField = "dept_name")
        @Schema(description = "部门ID")
        private Long deptId;

        @AuditField(name = "备注")
        @Schema(description = "备注")
        private String remark;
    }
}
