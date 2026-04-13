package com.jdec.platform.config.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户字段配置请求 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserFieldConfigReq {
    /** 字段编码 */
    private String fieldCode;

    /** 是否可见 */
    private Boolean visible;

    /** 展示顺序 */
    private Integer sortOrder;
}
