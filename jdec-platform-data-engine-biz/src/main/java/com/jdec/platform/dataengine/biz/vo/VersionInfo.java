package com.jdec.platform.dataengine.biz.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/** 版本列表信息模型类 */
@Data
@Builder
public class VersionInfo {

    @Schema(description = "业务表主键ID")
    private Long recordId;

    @Schema(description = "状态值")
    private Integer statusValue;

    @Schema(description = "单据版本")
    private Integer versionNo;

    /** 重构使用，单据业务编号 */
    @Schema(description = "业务编号")
    private String businessNo;

    /** 重构使用，标识是否历史版本，历史版本统一从归档的历史版本快照表查询页面所需显示数据 */
    @Schema(description = "是否历史版本标识")
    private Boolean history;
}
