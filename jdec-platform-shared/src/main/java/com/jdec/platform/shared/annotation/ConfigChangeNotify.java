package com.jdec.platform.shared.annotation;

import com.jdec.platform.shared.enums.ChangeType;
import java.lang.annotation.*;

/**
 * 配置变更通知注解
 *
 * <p>标注在 Service 方法上，方法执行成功后自动发布 Redis 消息
 *
 * <p>使用示例：
 *
 * <pre>
 * // 示例1：角色菜单变更（从参数中提取 roleId）
 * {@literal @}ConfigChangeNotify(
 *     changeType = ChangeType.ROLE_MENU_CHANGED,
 *     roleIdExpr = "#request.roleId"
 * )
 * public void saveRoleMenu(SaveRoleMenuReq request) { ... }
 *
 * // 示例2：角色模块权限变更（提取 roleId 和 moduleId）
 * {@literal @}ConfigChangeNotify(
 *     changeType = ChangeType.ROLE_MODULE_PERMISSION_CHANGED,
 *     roleIdExpr = "#request.roleId",
 *     moduleIdExpr = "#request.moduleId"
 * )
 * public void saveRoleModuleFieldPermission(SaveRoleModuleFieldPermissionReq request) { ... }
 *
 * // 示例3：模块变更（从返回值中提取 moduleId）
 * {@literal @}ConfigChangeNotify(
 *     changeType = ChangeType.MODULE_CHANGED,
 *     moduleIdExpr = "#result"
 * )
 * public Long saveModule(String projectNo, Long subjectId, SaveModuleReq request) { ... }
 *
 * // 示例4：角色配置变更（直接传参数）
 * {@literal @}ConfigChangeNotify(
 *     changeType = ChangeType.ROLE_CONFIG_CHANGED,
 *     roleIdExpr = "#roleId"
 * )
 * public void deleteRole(Long roleId) { ... }
 * </pre>
 *
 * @author JDEC Platform
 * @since 2026-06-22
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConfigChangeNotify {

    /** 变更类型 */
    ChangeType changeType();

    /**
     * 提取 roleId 的 SpEL 表达式
     *
     * <p>支持从参数或返回值中提取：
     *
     * <ul>
     *   <li>#request.roleId - 从参数对象的属性中提取
     *   <li>#roleId - 直接从参数中提取
     *   <li>#result - 从方法返回值中提取（需确保方法已执行）
     * </ul>
     *
     * <p><b>重要</b>：如果表达式求值结果为 null，切面将<b>不会发布消息</b>。 这适用于新增/编辑共用方法的场景（新增时 id 为 null，不发送消息；编辑时 id
     * 存在，发送消息）。
     */
    String roleIdExpr() default "";

    /**
     * 提取 moduleId 的 SpEL 表达式
     *
     * <p><b>重要</b>：如果表达式求值结果为 null，且 roleId 也为 null，切面将<b>不会发布消息</b>。
     */
    String moduleIdExpr() default "";

    /** 是否在方法抛异常时也发送消息（默认 false，只在成功时发送） */
    boolean notifyOnException() default false;

    /**
     * 是否要求 payload 至少有一个非 null 字段才发布消息（默认 true）
     *
     * <p>设置为 true 时，如果所有 SpEL 表达式求值结果都是 null，将不发布消息。
     *
     * <p>这对于新增/编辑共用的方法特别有用：新增时 id 为 null，自动跳过消息发布。
     */
    boolean requireNonEmptyPayload() default true;
}
