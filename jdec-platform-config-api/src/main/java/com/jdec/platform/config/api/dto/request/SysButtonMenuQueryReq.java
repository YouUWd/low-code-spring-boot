package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 按钮按菜单 + 类型查询条件 */
@Data
@Schema(description = "按钮按菜单+类型查询条件")
public class SysButtonMenuQueryReq {

    @Schema(description = "菜单ID，不传或为空表示仅按类型查询")
    private String menuId;

    @Schema(description = "按钮类型列表，如 add、reject")
    private List<String> type;
}
