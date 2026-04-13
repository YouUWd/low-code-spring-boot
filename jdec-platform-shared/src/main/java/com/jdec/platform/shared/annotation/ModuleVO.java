package com.jdec.platform.shared.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 模块 VO 注解 - 用于标记需要自动注册到模块管理的 VO
 *
 * <p>使用此注解的 Record 类会在应用启动时自动注册到模块管理系统， 并根据 Record 的字段自动创建模块字段配置。
 *
 * <p>示例:
 *
 * <pre>
 * @ModuleVO(moduleCode = "user_base", moduleName = "用户基本信息")
 * record UserBaseEntry(
 *     @Schema(description = "用户ID") Integer userId,
 *     @Schema(description = "用户名") String userName
 * ) {}
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ModuleVO {

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
     * 是否启用 - 默认启用
     *
     * @return 是否启用
     */
    boolean enabled() default true;
}
