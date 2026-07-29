package com.jdec.platform.shared.audit.annotation;

import com.jdec.platform.shared.audit.enums.OperationType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 数据审计注解 - 方法级 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataAudit {

    /** 业务模块（如：系统设置） */
    String module();

    /** 业务子模块（如：审批链配置） */
    String subModule() default "";

    /** 操作类型（如：修改） */
    OperationType operation();

    /** 表名 */
    String tableName();

    /** 数据ID字段名（SpEL表达式，如：#id, #request.id）。如果为空则不进行差异对比，只记录操作 */
    String dataIdField() default "";

    /**
     * 删除操作时显示的字段名（如：title, name）
     *
     * <p>用于在删除时从快照中提取该字段的值，显示在操作日志的 columns 中
     *
     * <p>例如：deleteDisplayField = "title"，则在删除时会从快照中提取 title 字段，显示为"删除了XX状态"
     */
    String deleteDisplayField() default "";

    /**
     * 实体类（如：SysStatus.class）
     *
     * <p>用于解析字段元数据（中文名、类型等）。DELETE操作必须指定此属性以解析 deleteDisplayField
     */
    Class<?> entityClass() default void.class;

    /** 是否启用 */
    boolean enabled() default true;

    /** 是否强制对比差异（仅当dataIdField不为空时生效）。false：无ID时跳过对比，true：无ID时也对比（新增场景） */
    boolean forceCompare() default false;
}
