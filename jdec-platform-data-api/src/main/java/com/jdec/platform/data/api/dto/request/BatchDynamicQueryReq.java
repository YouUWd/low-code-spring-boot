package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 多模块批量并发查询请求模型 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "多模块批量查询请求模型")
public class BatchDynamicQueryReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(
            description = "批量查询映射字典，Key 为业务自定义别名 (如 student, courses, awards)，Value 为各模块独立查询请求",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<String, DynamicQueryReq> queries;
}
