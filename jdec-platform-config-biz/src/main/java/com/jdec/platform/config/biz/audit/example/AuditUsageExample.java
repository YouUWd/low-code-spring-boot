package com.jdec.platform.config.biz.audit.example;

import com.jdec.platform.shared.audit.annotation.AuditField;
import com.jdec.platform.shared.audit.annotation.DataAudit;
import com.jdec.platform.shared.audit.enums.FieldType;
import com.jdec.platform.shared.audit.enums.OperationType;
import lombok.Data;

/**
 * 审计功能使用示例
 *
 * <p>本类展示如何在实际项目中使用数据快照和审计功能
 *
 * <p>使用步骤：
 *
 * <ol>
 *   <li>创建Request DTO，在需要审计的字段上添加 @AuditField 注解
 *   <li>在Service方法上添加 @DataAudit 注解
 *   <li>确保方法有 @Transactional 注解
 *   <li>执行方法后自动生成审计日志
 * </ol>
 */
public class AuditUsageExample {

    // ==================== 示例1：审批链配置更新 ====================

    /** 审批链配置更新请求 DTO */
    @Data
    public static class ApprovalChainConfigUpdateReq {

        @AuditField(name = "配置ID", ignore = true) // ID字段通常不需要审计
        private Long id;

        @AuditField(name = "模块ID")
        private Long moduleId;

        @AuditField(name = "上一步步骤")
        private Integer upStep;

        @AuditField(name = "当前步骤")
        private Integer currentStep;

        @AuditField(name = "下一步步骤")
        private Integer nextStep;

        @AuditField(name = "审批链分类ID")
        private Long approvalChainTypeId;

        @AuditField(name = "审批规则")
        private String approvalRule;

        @AuditField(name = "驳回规则")
        private String rejectRule;

        @AuditField(name = "角色审批百分比")
        private Integer roleApprovalPercent;

        @AuditField(
                name = "审批角色",
                type = FieldType.RELATION,
                target = "sys_role",
                idField = "id",
                nameField = "role_name")
        private Long approveRoleId;

        @AuditField(
                name = "审批人",
                type = FieldType.RELATION,
                target = "sys_user",
                idField = "user_id",
                nameField = "user_name")
        private Long approverId;

        @AuditField(
                name = "移交人",
                type = FieldType.RELATION,
                target = "sys_user",
                idField = "user_id",
                nameField = "user_name")
        private Long delegateApproverId;

        @AuditField(name = "是否跳过")
        private Integer skipped;

        @AuditField(name = "是否显示")
        private Integer showed;

        @AuditField(name = "子模块集合")
        private String childModuleIds;

        @AuditField(name = "当前状态ID")
        private Long currentStatusId;

        @AuditField(name = "下一状态ID")
        private Long nextStatusId;

        @AuditField(name = "是否自动审批")
        private Integer autoApproved;

        @AuditField(name = "自动审批时间")
        private Integer autoApprovedTime;

        @AuditField(name = "最迟办理时间")
        private Integer deadlineTime;

        @AuditField(name = "是否协同办理")
        private Integer collaborated;

        @AuditField(name = "是否可重复提交")
        private Integer repeated;

        @AuditField(name = "消息模板ID集合")
        private String msgTemplateIds;

        @AuditField(name = "是否默认")
        private Integer defaultFlag;
    }

    /**
     * Service方法示例：更新审批链配置
     *
     * <p>添加 @DataAudit 注解后，方法执行时会自动：
     *
     * <ol>
     *   <li>从快照表查询旧数据
     *   <li>执行更新操作
     *   <li>对比新旧数据差异
     *   <li>生成审计日志
     *   <li>更新快照数据
     * </ol>
     */
    @DataAudit(
            module = "系统设置", // 业务模块
            subModule = "审批链配置", // 业务子模块
            operation = OperationType.UPDATE, // 操作类型
            tableName = "sys_approval_chain_config", // 表名
            dataIdField = "#request.id" // 数据ID的SpEL表达式，从request.id获取
            )
    // @Transactional(rollbackFor = Exception.class)  // 必须有事务注解
    public void updateApprovalChainConfig(ApprovalChainConfigUpdateReq request) {
        // 实际业务逻辑
        // sysApprovalChainConfigMapper.updateById(config);
    }

    /**
     * Service方法示例：删除审批链配置
     *
     * <p>DELETE操作会记录被删除的所有字段值
     */
    @DataAudit(
            module = "系统设置",
            subModule = "审批链配置",
            operation = OperationType.DELETE,
            tableName = "sys_approval_chain_config",
            dataIdField = "#id" // 直接从方法参数获取
            )
    // @Transactional(rollbackFor = Exception.class)
    public void deleteApprovalChainConfig(Long id) {
        // 实际业务逻辑
        // sysApprovalChainConfigMapper.deleteById(id);
    }

    // ==================== 示例2：角色管理 ====================

    /** 角色更新请求 DTO */
    @Data
    public static class RoleUpdateReq {

        @AuditField(name = "角色ID", ignore = true)
        private Long id;

        @AuditField(name = "角色名称")
        private String roleName;

        @AuditField(name = "角色标识")
        private String roleSlug;

        @AuditField(name = "描述")
        private String description;

        @AuditField(
                name = "创建人",
                type = FieldType.RELATION,
                target = "sys_user",
                idField = "user_id",
                nameField = "user_name")
        private Long createdBy;
    }

    @DataAudit(
            module = "权限管理",
            subModule = "角色管理",
            operation = OperationType.UPDATE,
            tableName = "sys_role",
            dataIdField = "#request.id")
    public void updateRole(RoleUpdateReq request) {
        // 业务逻辑
    }

    @DataAudit(
            module = "权限管理",
            subModule = "角色管理",
            operation = OperationType.DELETE,
            tableName = "sys_role",
            dataIdField = "#roleId")
    public void deleteRole(Long roleId) {
        // 业务逻辑
    }

    // ==================== 示例3：用户管理 ====================

    /** 用户更新请求 DTO */
    @Data
    public static class UserUpdateReq {

        @AuditField(name = "用户ID", ignore = true)
        private Long userId;

        @AuditField(name = "用户名")
        private String userName;

        @AuditField(name = "工号")
        private String workNumber;

        @AuditField(name = "手机号")
        private String phone;

        @AuditField(name = "邮箱")
        private String email;

        @AuditField(name = "状态")
        private Integer status;

        @AuditField(
                name = "所属主体",
                type = FieldType.RELATION,
                target = "subject",
                idField = "id",
                nameField = "subject_name")
        private Long subjectId;
    }

    @DataAudit(
            module = "用户管理",
            operation = OperationType.UPDATE,
            tableName = "sys_user",
            dataIdField = "#request.userId")
    public void updateUser(UserUpdateReq request) {
        // 业务逻辑
    }

    // ==================== 示例4：复杂嵌套对象 ====================

    /** 复杂请求包装类 */
    @Data
    public static class ComplexRequest {
        private DataWrapper data;
    }

    @Data
    public static class DataWrapper {
        @AuditField(name = "配置ID")
        private Long id;

        @AuditField(name = "配置名称")
        private String name;
    }

    /**
     * 处理嵌套对象的dataIdField配置
     *
     * <p>使用SpEL表达式访问嵌套对象的属性
     */
    @DataAudit(
            module = "配置管理",
            operation = OperationType.UPDATE,
            tableName = "sys_config",
            dataIdField = "#request.data.id" // 访问嵌套对象的id
            )
    public void updateComplexConfig(ComplexRequest request) {
        // 业务逻辑
    }

    // ==================== 示例5：多参数方法 ====================

    /**
     * 多参数方法的审计
     *
     * <p>SpEL表达式直接引用参数名
     */
    @DataAudit(
            module = "配置管理",
            operation = OperationType.UPDATE,
            tableName = "sys_config",
            dataIdField = "#configId" // 引用方法参数名
            )
    public void updateConfigByParams(Long configId, String name, Integer status) {
        // 业务逻辑
    }

    // ==================== 示例6：条件禁用审计 ====================

    /**
     * 临时禁用审计
     *
     * <p>通过 enabled = false 可以暂时关闭审计功能
     */
    @DataAudit(
            module = "测试",
            operation = OperationType.UPDATE,
            tableName = "test_table",
            dataIdField = "#id",
            enabled = false // 禁用审计
            )
    public void updateWithoutAudit(Long id) {
        // 业务逻辑，不会产生审计日志
    }

    // ==================== 审计结果示例 ====================

    /**
     * 审计结果JSON示例
     *
     * <pre>
     * {
     *   "module": "系统设置",
     *   "subModule": "审批链配置",
     *   "operation": "修改",
     *   "tableName": "sys_approval_chain_config",
     *   "dataId": 123,
     *   "operateTime": "2026-07-15T10:30:00",
     *   "diffs": [
     *     {
     *       "fieldName": "审批角色",
     *       "fieldCode": "approveRoleId",
     *       "oldValue": "管理员",        // 关联字段自动显示中文名
     *       "newValue": "审批员",
     *       "fieldType": "RELATION"
     *     },
     *     {
     *       "fieldName": "当前步骤",
     *       "fieldCode": "currentStep",
     *       "oldValue": 1,
     *       "newValue": 2,
     *       "fieldType": "SIMPLE"
     *     },
     *     {
     *       "fieldName": "是否跳过",
     *       "fieldCode": "skipped",
     *       "oldValue": 0,
     *       "newValue": 1,
     *       "fieldType": "SIMPLE"
     *     }
     *   ]
     * }
     * </pre>
     */
    public void auditResultExample() {
        // 仅用于文档说明
    }

    // ==================== 注意事项 ====================

    /**
     * 使用注意事项
     *
     * <ol>
     *   <li>被审计的方法必须添加 @Transactional 注解
     *   <li>Request DTO的所有字段都应添加 @AuditField 注解（ID字段可设置 ignore=true）
     *   <li>关联字段必须设置 type=FieldType.RELATION 并配置 target、idField、nameField
     *   <li>dataIdField使用SpEL表达式，支持访问方法参数和嵌套对象
     *   <li>首次使用前需要初始化快照数据
     *   <li>审计日志在事务提交后才会发送（当前为TODO，需要实现HTTP接口）
     *   <li>快照表使用版本号乐观锁，防止并发更新
     * </ol>
     */
    public void usageNotes() {
        // 仅用于文档说明
    }
}
