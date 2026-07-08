package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 模块列表响应 DTO 返回模块的基本信息，不包含树形结构，前端自行构建树 */
@Data
@Schema(description = "模块列表项")
public class SysModuleListResp {

    @Schema(description = "模块ID", example = "1")
    private Long id;

    @Schema(description = "项目编码", example = "1")
    private String projectNo;

    @Schema(description = "主体ID", example = "1")
    private Long subjectId;

    @Schema(description = "模块编码", example = "sys_user")
    private String moduleCode;

    @Schema(description = "模块名称", example = "用户管理")
    private String moduleName;

    @Schema(description = "模块描述", example = "系统用户管理模块")
    private String moduleDesc;

    @Schema(description = "父模块ID（NULL表示根级）", example = "1")
    private Long parentId;

    @Schema(description = "详情模块ID", example = "2")
    private Long detailModuleId;

    @Schema(description = "主表名", example = "sys_user")
    private String primaryTable;

    @Schema(
            description = "模块类型",
            example = "LIST",
            allowableValues = {"LIST", "DETAIL"})
    private String moduleType;

    @Schema(description = "是否启用审批", example = "0")
    private Integer approvalRequired;

    @Schema(description = "是否模块业务定义", example = "0")
    private Integer bizDefFlag;

    @Schema(description = "模块类别 (1=业务模块, 2=系统模块)", example = "1")
    private Integer category;

    @Schema(description = "排序顺序", example = "1")
    private Integer sortOrder;

    @Schema(description = "创建人ID", example = "1")
    private Long createdBy;

    @Schema(description = "创建人名称", example = "admin")
    private String createdName;

    @Schema(description = "创建时间", example = "2024-01-01T00:00:00")
    private String createdDate;

    @Schema(description = "更新人ID", example = "1")
    private Long updatedBy;

    @Schema(description = "更新人名称", example = "admin")
    private String updatedName;

    @Schema(description = "更新时间", example = "2024-01-01T00:00:00")
    private String updatedDate;

    @Schema(description = "主表外键关联字段", example = "user_id")
    private String relateSearchField;
}
