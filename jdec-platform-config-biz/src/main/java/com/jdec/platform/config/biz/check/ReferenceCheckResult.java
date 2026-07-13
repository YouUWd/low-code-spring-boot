package com.jdec.platform.config.biz.check;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReferenceCheckResult {
    /** 是否存在引用 */
    private boolean referenced;

    /** 引用数量 */
    private Long count;

    /** 提示信息 */
    private String message;

    public static ReferenceCheckResult empty() {
        return ReferenceCheckResult.builder().referenced(false).build();
    }
}
