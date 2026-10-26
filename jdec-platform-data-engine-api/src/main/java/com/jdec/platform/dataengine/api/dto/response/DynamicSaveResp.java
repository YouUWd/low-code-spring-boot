package com.jdec.platform.dataengine.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "动态一体化数据保存响应模型 (含根模块ID,根模块数据ID,根模块数据版本,保存数据)")
public class DynamicSaveResp {
    @Schema(description = "根模块ID")
    private Long moduleId;

    @Schema(description = "根模块数据ID")
    private Long dataId;

    @Schema(description = "根模块数据业务编号")
    private String businessNo;

    @Schema(description = "根模块数据版本")
    private Integer versionNo;

    @Schema(description = "实时新增时子模块数据ID")
    private Long childDataId;

    @Schema(description = "保存数据")
    private Object data;
}
