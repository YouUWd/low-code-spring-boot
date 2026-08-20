package com.jdec.platform.config.biz.check;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReferenceContext {
    /** 被删除对象类型 */
    private String targetType;

    /** 被删除对象Id */
    private Long targetId;

    /** 被删除对象名称 */
    private String targetName;
}
