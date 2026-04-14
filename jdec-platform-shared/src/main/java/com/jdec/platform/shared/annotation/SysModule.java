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
 * <p>示例:
 *
 * <pre>
 * @SysModule(moduleCode = "user_base", moduleName = "用户基本信息", permissionType = "read")
 * public class UserBaseVO {
 *     @Schema(description = "用户ID") 
 *     private Integer userId;
 *     
 *     @Schema(description = "用户名") 
 *     private String userName;
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SysModule {

    /**
     * 模块编码 - 唯一标识
     *
     * @return 模块编码
     */
    String moduleCode();

    /**
     * 模块名称 - 中文显示名称
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
