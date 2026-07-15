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

    /** 数据ID字段名（SpEL表达式，如：#id, #request.id） */
    String dataIdField();

    /** 是否启用 */
    boolean enabled() default true;
}
