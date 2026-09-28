package com.jdec.platform.common.biz.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "基于菜单的详情数据查询请求")
public class MenuDetailQueryReq {

    @Schema(description = "菜单ID", example = "20")
    private Long menuId;

    @Schema(description = "详情数据ID", example = "20")
    private Long id;

    @Schema(description = "是否返回版本对比", example = "false")
    private Boolean showVersionCompare;

    @Schema(description = "单据版本号", example = "1")
    private Integer versionNo;

    @Schema(description = "历史版本查询还是当前查询", example = "false")
    private Boolean history;

    @Schema(description = "查看类型", example = "view")
    private String btnType;

    @Schema(description = "查询当前模块还是递归查询", example = "view")
    private Boolean flatQuery;
}
