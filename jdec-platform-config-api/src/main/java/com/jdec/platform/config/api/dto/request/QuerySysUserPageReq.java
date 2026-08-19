package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.model.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户分页查询条件 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户分页查询条件")
public class QuerySysUserPageReq extends PageRequest {

    @Schema(description = "用户名称")
    private String userName;

    @Schema(description = "工号")
    private String workNumber;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "用户类型 1内部 2外部")
    private Integer userType;

    @Schema(description = "状态 1启用 2禁用")
    private Integer statusFlag;

    @Schema(description = "公司名称")
    private String companyName;

    @Schema(description = "部门名称")
    private String departName;

    @Schema(description = "主体简称")
    private String subjectName;

    @Schema(description = "员工状态")
    private Integer employedStatus;
}
