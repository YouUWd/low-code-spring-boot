package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 菜单下拉选项响应（树形，供按钮关联菜单多选使用） */
@Data
@Schema(description = "菜单下拉选项（树形）")
public class SysMenuOptionResp {

    @Schema(description = "节点唯一标识：分组如 category-1，叶子如 menu-19")
    private String key;

    @Schema(description = "菜单ID（叶子节点有效，分组节点为空）")
    private Long value;

    @Schema(description = "节点名称")
    private String label;

    @Schema(description = "子节点列表")
    private List<SysMenuOptionResp> children;
}
