package com.jdec.platform.data.api.dto.response;

import com.jdec.platform.data.api.dto.model.AbstractDynamicData;
import com.jdec.platform.data.api.dto.model.DetailMeta;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "动态详情响应模型 (包含模块标识、元数据与物理表业务数据)")
public class DynamicDetailResp extends AbstractDynamicData<DynamicDetailResp> {

    private static final long serialVersionUID = 1L;

    @Schema(description = "模块编码", example = "STUDENT-DETAIL")
    private String moduleCode;

    @Schema(description = "模块名称", example = "学生详情")
    private String moduleName;

    @Schema(description = "模块类型", example = "DETAIL")
    private String moduleType;

    @Schema(description = "主表名称", example = "student")
    private String primaryTable;

    @Schema(description = "字段显示名称与 view/apply/edit 权限元数据")
    private DetailMeta meta;
}
