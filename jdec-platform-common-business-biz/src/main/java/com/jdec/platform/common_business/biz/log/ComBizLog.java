package com.jdec.platform.common_business.biz.log;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ComBizLog {
    String name() default "";
}
