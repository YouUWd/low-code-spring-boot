package com.jdec.platform.config.biz.audit.example;

import com.jdec.platform.shared.audit.annotation.AuditField;
import com.jdec.platform.shared.audit.annotation.DataAudit;
import com.jdec.platform.shared.audit.enums.FieldType;
import com.jdec.platform.shared.audit.enums.OperationType;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SaveOrUpdate 接口审计示例
 *
 * <p>演示如何为新增和修改共用的接口添加审计日志
 *
 * <p>核心原理：使用 OperationType.UPDATE，系统会自动判断：
 *
 * <ul>
 *   <li>如果快照不存在（oldSnapshot == null）→ 识别为新增，生成新增类型的 diff（oldValue 全为 null）
 *   <li>如果快照存在 → 识别为修改，生成修改类型的 diff（对比新旧值）
 * </ul>
 */
@Service
public class SaveOrUpdateAuditExample {

    // ==================== 方案1：注解式（推荐） ====================

    /**
     * 方案1：使用 UPDATE 操作类型，自动识别新增或修改
     *
     * <p>优点：
     *
     * <ul>
     *   <li>代码简洁，只需一个注解
     *   <li>自动判断新增/修改，无需手动判断
     *   <li>审计日志自动记录 diff
     * </ul>
     *
     * <p>适用场景：
     *
     * <ul>
     *   <li>根据 ID 是否存在判断新增或修改
     *   <li>需要记录完整的字段变更信息
     * </ul>
     */
    @DataAudit(
            module = "角色管理",
            subModule = "角色信息",
            operation = OperationType.UPDATE, // 使用 UPDATE，系统会自动判断
            tableName = "sys_role",
            dataIdField = "#request.id" // 可能为 null（新增）或有值（修改）
            )
    @Transactional
    public void saveOrUpdateRole(RoleSaveOrUpdateReq request) {
        // 业务逻辑：根据 ID 判断新增或修改
        if (request.getId() == null) {
            // 新增逻辑
            // roleMapper.insert(role);
            // 系统会识别为新增，生成 diff：oldValue 全为 null
        } else {
            // 修改逻辑
            // roleMapper.updateById(role);
            // 系统会识别为修改，生成 diff：对比新旧值
        }
    }

    /**
     * 方案2：从返回值获取 ID（适用于新增后返回 ID 的场景）
     *
     * <p>使用场景：
     *
     * <ul>
     *   <li>新增时 ID 由数据库自动生成
     *   <li>返回值包含生成的 ID
     * </ul>
     */
    @DataAudit(
            module = "角色管理",
            subModule = "角色信息",
            operation = OperationType.UPDATE,
            tableName = "sys_role",
            dataIdField = "#result.id" // 从返回值获取 ID
            )
    @Transactional
    public RoleSaveResult saveOrUpdateRoleWithResult(RoleSaveOrUpdateReq request) {
        Long id;
        if (request.getId() == null) {
            // 新增
            // roleMapper.insert(role);
            id = 1L; // role.getId();
        } else {
            // 修改
            // roleMapper.updateById(role);
            id = request.getId();
        }

        return RoleSaveResult.builder().id(id).build();
    }

    /**
     * 方案3：根据数据库查询结果判断
     *
     * <p>使用场景：根据唯一键（如手机号、邮箱）判断是否存在
     */
    @DataAudit(
            module = "用户管理",
            subModule = "用户信息",
            operation = OperationType.UPDATE,
            tableName = "sys_user",
            dataIdField = "#result.userId")
    @Transactional
    public UserSaveResult saveOrUpdateUserByPhone(UserSaveReq request) {
        // 根据手机号查询是否存在
        // SysUser existUser = userMapper.selectByPhone(request.getPhone());

        Long userId;
        // if (existUser == null) {
        //     // 新增
        //     userMapper.insert(user);
        //     userId = user.getUserId();
        // } else {
        //     // 修改
        //     user.setUserId(existUser.getUserId());
        //     userMapper.updateById(user);
        //     userId = existUser.getUserId();
        // }

        userId = 1L; // 示例
        return UserSaveResult.builder().userId(userId).build();
    }

    // ==================== 方案2：编程式 ====================

    /**
     * 编程式：手动判断并记录审计
     *
     * <p>优点：
     *
     * <ul>
     *   <li>灵活控制审计逻辑
     *   <li>可以自定义 operation 描述
     * </ul>
     *
     * <p>缺点：需要手动编写判断逻辑
     */
    @Transactional
    public void saveOrUpdateRoleManual(RoleSaveOrUpdateReq request) {
        // 示例：使用 DataAuditService
        // if (request.getId() == null) {
        //     // 新增
        //     roleMapper.insert(role);
        //     dataAuditService.auditCreate(
        //         "系统设置", "角色管理", "sys_role", role.getId(), request
        //     );
        // } else {
        //     // 修改
        //     roleMapper.updateById(role);
        //     dataAuditService.auditWithCompare(
        //         "系统设置", "角色管理", "修改", "sys_role", request.getId(), request
        //     );
        // }
    }

    // ==================== 审计日志效果对比 ====================

    /**
     * 新增时的审计日志效果：
     *
     * <pre>
     * {
     *   "module": "角色管理",
     *   "operation": "新增",  // 自动识别为新增
     *   "diffs": [
     *     {
     *       "fieldName": "角色名称",
     *       "oldValue": null,      // 新增时 oldValue 为 null
     *       "newValue": "管理员"
     *     },
     *     {
     *       "fieldName": "所属部门",
     *       "oldValue": null,
     *       "newValue": "研发部"
     *     }
     *   ]
     * }
     * </pre>
     *
     * 修改时的审计日志效果：
     *
     * <pre>
     * {
     *   "module": "角色管理",
     *   "operation": "修改",  // 自动识别为修改
     *   "diffs": [
     *     {
     *       "fieldName": "角色名称",
     *       "oldValue": "管理员",   // 修改时对比新旧值
     *       "newValue": "超级管理员"
     *     }
     *   ]
     * }
     * </pre>
     */

    // ==================== DTO 定义 ====================

    /** 角色保存或更新请求 */
    @Data
    public static class RoleSaveOrUpdateReq {

        @AuditField(name = "角色ID", ignore = true)
        private Long id; // null 表示新增，有值表示修改

        @AuditField(name = "角色名称")
        private String roleName;

        @AuditField(
                name = "所属部门",
                type = FieldType.RELATION,
                target = "department",
                idField = "id",
                nameField = "dept_name")
        private Long deptId;

        @AuditField(name = "状态")
        private Integer status;
    }

    /** 角色保存结果 */
    @Data
    @lombok.Builder
    public static class RoleSaveResult {
        private Long id;
    }

    /** 用户保存请求 */
    @Data
    public static class UserSaveReq {

        @AuditField(name = "用户名")
        private String userName;

        @AuditField(name = "手机号")
        private String phone;

        @AuditField(name = "邮箱")
        private String email;
    }

    /** 用户保存结果 */
    @Data
    @lombok.Builder
    public static class UserSaveResult {
        private Long userId;
    }
}
