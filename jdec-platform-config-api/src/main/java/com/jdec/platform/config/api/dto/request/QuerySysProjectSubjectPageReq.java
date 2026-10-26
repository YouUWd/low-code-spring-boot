package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.model.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 项目主体分页查询条件 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "项目主体分页查询条件")
public class QuerySysProjectSubjectPageReq extends PageRequest {

    @Schema(description = "主体名称")
    private String subjectName;

    @Schema(description = "公司名称")
    private String companyName;

    @Schema(description = "别名")
    private String alias;

    @Schema(description = "主体ID")
    private Long subjectId;

    @Schema(description = "项目编码")
    private String projectNo;
}
