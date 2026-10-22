package com.jdec.platform.common.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 低代码字段视图元数据响应模型 (面向 UI 展示与前端权限管控) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字段视图元数据")
public class FieldMetaDTO implements Serializable {

    @Schema(description = "字段 ID", example = "101")
    private Long id;

    @Schema(description = "字段显示名称 / 标签", example = "行业类型")
    private String displayName;

    @Schema(description = "是否可申请 / 提交", example = "true")
    private Boolean apply;

    @Schema(description = "是否可查看", example = "true")
    private Boolean view;

    @Schema(description = "是否可编辑", example = "true")
    private Boolean edit;

    @Schema(description = "词云关系ID", example = "true")
    private String businessNo;

    /** 业务类型名称 */
    @Schema(description = "业务类型名称", example = "图片")
    private String businessTypeName;
}
