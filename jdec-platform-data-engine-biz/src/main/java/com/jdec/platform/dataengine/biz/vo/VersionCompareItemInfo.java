package com.jdec.platform.dataengine.biz.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionCompareItemInfo {
    // 0:无版本变更;1:无上一版本，有版本内对比;2:有上一版本，无版本内;3:有上一版本，有版本内对比;
    private Integer type;

    // 上一启用版本值
    private Object lastEnableVersionVal;

    // 当前版本值
    private Object currentVersionVal;

    // 版本内上一版本值
    private Object preVersionVal;
}
