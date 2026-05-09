package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 数据权限类型列表响应 */
@Data
@Schema(description = "数据权限类型列表响应")
public class SysDataPermissionTypeResp {

    @Schema(description = "类型ID")
    private Long id;

    @Schema(description = "类型编码")
    private String code;

    @Schema(description = "类型名称")
    private String name;

    @Schema(description = "类型描述")
    private String description;

    @Schema(description = "是否需要指定目标 1:是 0:否")
    private Integer hasTarget;

    @Schema(description = "目标类型 user/dept/company 等")
    private String targetType;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "状态 1:启用 0:禁用")
    private Integer status;

    @Schema(description = "目标用户列表（hasTarget为1时返回）")
    private List<DataPermissionUserResp> children = new ArrayList<>();
}
