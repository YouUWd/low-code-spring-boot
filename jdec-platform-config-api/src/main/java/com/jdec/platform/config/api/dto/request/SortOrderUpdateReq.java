package com.jdec.platform.config.api.dto.request;

import lombok.Data;

/** 排序顺序更新请求 DTO 用于批量更新模块排序 */
@Data
public class SortOrderUpdateReq {

    /** 模块 ID */
    private Long moduleId;

    /** 新的排序顺序 */
    private Integer sortOrder;
}
