package com.jdec.platform.common_business.biz.aduit;

import com.jdec.platform.common_business.biz.enums.DataTrackTypeEnum;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataTrack {

    DataTrackTypeEnum type();
}
