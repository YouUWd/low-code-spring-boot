package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.model.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 按钮分页查询条件 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "按钮分页查询条件")
public class SysButtonPageReq extends PageRequest {

    @Schema(description = "按钮名称")
    private String title;

    @Schema(description = "别名")
    private String alias;

    @Schema(description = "是否启用(1:启用。0：不启用)")
    private Integer enabled;

    @Schema(description = "1显示 0隐藏")
    private Integer showed;
}
