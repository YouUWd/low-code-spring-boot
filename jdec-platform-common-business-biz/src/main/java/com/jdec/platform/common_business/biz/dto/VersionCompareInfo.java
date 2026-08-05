package com.jdec.platform.common_business.biz.dto;

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

    private Map<String, VersionCompareItemInfo> changeItem;
}
