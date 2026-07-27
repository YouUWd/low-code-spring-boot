package com.jdec.platform.common_business.biz.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionCompareItem {
    private Integer versionNo;
    private Object fieldValue;
    private String type;
    private List<VersionCompareItem> rejectList;
}
