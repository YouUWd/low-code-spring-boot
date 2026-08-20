package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 数据权限类型列表响应 */
@Data
@Schema(description = "数据权限类型列表响应")
public class SysDataPermissionResp {

    @Schema(description = "类型ID")
    private Long id;

    @Schema(description = "类型编码")
    private String code;

    @Schema(description = "类型名称")
    private String name;

    @Schema(description = "类型描述")
    private String description;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "状态 1:启用 0:禁用")
    private Integer status;

    @Schema(description = "关联的数据权限列表")
    private List<String> bizNames;
}
