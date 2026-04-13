package com.jdec.platform.config.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户字段配置响应 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFieldConfigResp {
    /** 字段编码 */
    private String fieldCode;

    /** 是否可见 */
    private Boolean visible;

    /** 展示顺序 */
    private Integer sortOrder;
}
