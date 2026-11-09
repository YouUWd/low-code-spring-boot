package com.jdec.platform.config.biz.reference;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 引用声明注解。
 *
 * <p>加在「引用方」实体的外键字段上，指向「被引用」实体。框架启动时扫描本注解并反转建图 （{@code 目标表.目标列 -> [引用方表.引用方列,
 * ...]}），删除目标数据前即可自动检查是否存在引用。
 *
 * <pre>{@code
 * @TableName("b")
 * public class B {
 *     @Reference(target = A.class, name = "订单", displayField = "name")
 *     private Long aId;
 * }
 * }</pre>
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Reference {

    /** 被引用的实体类（需带 {@code @TableName}）。 */
    Class<?> target();

    /** 被引用实体中作为被引用列的字段，默认主键 {@code id}。 */
    String targetField() default "id";

    /** 当前实体中作为外键的列名，留空则由属性名推导（驼峰转下划线，兼容 {@code @TableField}）。 */
    String sourceField() default "";

    /** 引用方的展示字段（属性名，如 title/name），用于错误提示；留空则只统计条数。 */
    String displayField() default "";

    /**
     * 关联展示：源记录中另一个外键属性（如 {@code menuId}），其值指向 {@link #displayViaTarget()} 的一条记录， 错误提示中将展示该记录的
     * {@link #displayViaColumn()} 列值。适用于无名称列的关联表（如 sys_role_menu）。
     */
    String displayViaField() default "";

    /** 关联展示实体（如 {@code SysMenu.class}），需带 {@code @TableName} 与主键；默认 {@code Void} 表示未启用。 */
    Class<?> displayViaTarget() default Void.class;

    /** 关联展示实体中作为名称的属性（如 {@code title}）。 */
    String displayViaColumn() default "";

    /** 引用方业务名称（如「菜单」），用于错误提示；留空则使用表名。 */
    String name() default "";

    /** 是否参与删除前引用检查，默认参与。 */
    boolean check() default true;
}
