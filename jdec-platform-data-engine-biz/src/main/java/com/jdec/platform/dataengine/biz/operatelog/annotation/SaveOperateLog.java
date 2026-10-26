package com.jdec.platform.dataengine.biz.operatelog.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 保存操作日志注解。
 *
 * <p>标注在动态保存接口上，由 {@code SaveOperateLogAspect} 拦截：保存成功后对比保存前后的数据，生成操作日志
 * （仅无审批链的普通保存）。日志与业务无事务关系，生成失败不影响保存。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SaveOperateLog {

    /** 是否启用，默认启用 */
    boolean enabled() default true;
}
