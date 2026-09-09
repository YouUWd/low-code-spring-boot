package com.jdec.platform.dataengine.biz.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionCompareInfo {
    private Integer currentVersion;

    private Integer lastVersion;

    /** key为字段名称tableName.tableField */
    @Schema(description = "版本对比信息")
    private Map<String, VersionCompareItemInfo> changeItem;
}
