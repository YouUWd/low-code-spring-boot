package com.jdec.platform.dataengine.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "动态详情页查询请求模型")
public class DynamicDetailQueryReq implements Serializable {

    @Schema(description = "模块ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    private Long moduleId;

    @Schema(
            description = "模块查看字段列表",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            example = "[10001 , 10002 , 10003]")
    private List<Long> filterFields;

    @Schema(description = "详情数据ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long id;

    @Schema(description = "版本 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "V2")
    private Integer version;

    @Schema(
            description = "是否拼接版本对比数据",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            example = "V2")
    private Boolean showVersionCompare;

    @Schema(description = "是否历史版本", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "V2")
    private Boolean history;

    private String btnType;
}
