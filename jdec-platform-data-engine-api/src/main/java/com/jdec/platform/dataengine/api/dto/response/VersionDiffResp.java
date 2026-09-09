package com.jdec.platform.dataengine.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "版本间差异比对响应模型")
public class VersionDiffResp implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "模块 ID", example = "134")
    private Long moduleId;

    @Schema(description = "业务数据 ID", example = "1001")
    private Long dataId;

    @Schema(description = "源版本号", example = "1")
    private Integer fromVersion;

    @Schema(description = "目标版本号", example = "2")
    private Integer toVersion;

    @Schema(description = "结构化变更项列表")
    private List<Map<String, Object>> diffList;
}
