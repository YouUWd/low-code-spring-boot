package com.jdec.platform.shared.annotation;

import com.jdec.platform.shared.enums.PermissionType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 系统模块注解 - 用于标记需要自动注册到模块管理的数据模型
 *
 * <p>使用此注解的类会在应用启动时自动注册到模块管理系统，并根据类的字段自动创建模块字段配置。
 *
 * <p>支持模块分类：moduleCode 作为主分组，operationType 作为子分类。
 *
 * <p>示例 - 简单模块（不区分操作类型）:
 *
 * <pre>
 * @SysModule(
 *     moduleCode = "employee", 
 *     moduleName = "员工管理", 
 *     permissionTypes = {PermissionType.READ, PermissionType.WRITE}
 * )
 * public class EmployeeVO {
 *     @Schema(description = "员工ID") 
 *     private Integer employeeId;
 * }
 * </pre>
 *
 * <p>示例 - 分类模块（区分操作类型）:
 *
 * <pre>
 * // 查看权限
 * @SysModule(
 *     moduleCode = "userBase",
 *     moduleName = "用户基本信息",
 *     operationType = "read",
 *     operationName = "查看",
 *     sortOrder = 1,
 *     permissionTypes = {PermissionType.READ}
 * )
 * public class UserBaseResp {
 *     @Schema(description = "用户ID") 
 *     private Integer userId;
 * }
 *
 * // 编辑权限
 * @SysModule(
 *     moduleCode = "userBase",
 *     moduleName = "用户基本信息",
 *     operationType = "update",
 *     operationName = "编辑",
 *     sortOrder = 2,
 *     permissionTypes = {PermissionType.UPDATE}
 * )
 * public class UpdateUserBaseReq {
 *     @Schema(description = "用户ID") 
 *     private Integer userId;
 * }
 *
 * // 删除权限
 * @SysModule(
 *     moduleCode = "userBase",
 *     moduleName = "用户基本信息",
 *     operationType = "delete",
 *     operationName = "删除",
 *     sortOrder = 3,
 *     permissionTypes = {PermissionType.DELETE}
 * )
 * public class DeleteUserBaseReq {
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiModule {

    /**
     * 模块编码 (主分组) - 唯一标识一个业务模块
     *
     * <p>示例: userBase、role、permission、employee 等
     * <p>同一个 moduleCode 下可以有多个操作类型（read、update、delete）
     *
     * @return 模块编码
     */
    String moduleCode();

    /**
     * 模块名称 - 主分组的显示名称
     *
     * <p>示例: "用户基本信息"、"角色管理"、"权限管理"
     *
     * @return 模块名称
     */
    String moduleName();

    /**
     * 模块描述 - 可选
     *
     * @return 模块描述
     */
    String description() default "";

    /**
     * 权限类型 - 用于自动权限初始化（必填）
     *
     * <p>支持的权限类型:
     * <ul>
     *   <li>{@link PermissionType#READ} - 读取权限（GET 请求，Response 对象）</li>
     *   <li>{@link PermissionType#WRITE} - 写入权限（POST 请求，Request 对象）</li>
     *   <li>{@link PermissionType#UPDATE} - 更新权限（PUT 请求，Request 对象）</li>
     *   <li>{@link PermissionType#DELETE} - 删除权限（DELETE 请求）</li>
     * </ul>
     *
     * <p>示例:
     * <ul>
     *   <li>单个权限: {@code permissionTypes = {PermissionType.READ}}</li>
     *   <li>多个权限: {@code permissionTypes = {PermissionType.READ, PermissionType.WRITE}}</li>
     * </ul>
     *
     * @return 权限类型数组
     */
    PermissionType[] permissionTypes();

    /**
     * 是否启用 - 默认启用
     *
     * @return 是否启用
     */
    boolean enabled() default true;
}
