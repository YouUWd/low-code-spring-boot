package com.jdec.platform.common_business.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@Schema(description = "编辑或新增时的模块表单数据")
public class MenuSaveReq {

    @Schema(description = "菜单id")
    private Long menuId;

    @Schema(description = "模块用到的表")
    private List<String> moduleTableList;

    @Schema(description = "模块更新数据")
    private List<Map<String, Object>> moduleData;
}
