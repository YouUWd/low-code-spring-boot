package com.jdec.platform.shared.audit.annotation;

import com.jdec.platform.shared.audit.enums.FieldType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 数据审计注解 - 字段级 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditField {

    /** 字段中文名 */
    String name();

    /** 字段类型 */
    FieldType type() default FieldType.SIMPLE;

    /** 关联表名（type=RELATION时必填） */
    String target() default "";

    /** ID字段名 */
    String idField() default "id";

    /** 名称字段名 */
    String nameField() default "name";

    /** 枚举类（type=ENUM时必填，必须实现EnumDescribable接口） */
    Class<? extends Enum<?>> enumClass() default DefaultEnum.class;

    /** 字典分类别名（type=DICT时必填，对应sys_config_category.category_alias） */
    String dictCategoryAlias() default "";

    /** 是否为唯一标识字段（用于审计日志中的记录名称） */
    boolean uniqueIdentifier() default false;

    /** 是否忽略 */
    boolean ignore() default false;

    /** 默认枚举类型（用于判断是否配置了enumClass） */
    enum DefaultEnum {}
}
