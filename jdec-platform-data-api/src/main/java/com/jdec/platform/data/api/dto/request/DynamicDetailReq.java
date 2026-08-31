package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 动态数据集/详情查询请求模型 支持基于主键 ID 或特定业务过滤条件获取主子表结构化数据 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "动态数据集/详情查询请求")
public class DynamicDetailReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "模块 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "128")
    private Long moduleId;

    @Schema(description = "主表主键 ID（优先匹配单行精准主子表详情）", example = "1001")
    private Long id;

    @Schema(description = "特殊过滤条件（支持外键或特定业务字段多行查询）")
    private Map<String, Object> filters;
}
