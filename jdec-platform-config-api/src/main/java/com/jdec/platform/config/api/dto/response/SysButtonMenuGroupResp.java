package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 按菜单分组的按钮信息 */
@Data
@Schema(description = "按菜单分组的按钮信息")
public class SysButtonMenuGroupResp {

    @Schema(description = "菜单ID")
    private Long menuId;

    @Schema(description = "该菜单下匹配的按钮列表")
    private List<SysButtonResp> buttons;
}
