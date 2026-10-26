package com.jdec.platform.auth.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 主体信息（含该主体下当前用户的角色列表） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "主体信息")
public class SubjectResp implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主体名称")
    private String subjectName;

    @Schema(description = "公司名称")
    private String companyName;

    @Schema(description = "主体别名")
    private String alias;

    @Schema(description = "主体ID")
    private Long subjectId;

    @Schema(description = "该主体下当前用户的角色列表")
    private List<RoleResp> roleRespList;
}
