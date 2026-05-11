package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 字段配置保存请求 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字段配置保存请求")
public class SysFieldSaveReq {

    @Schema(description = "主键ID（更新时必填）")
    private Long id;

    @Schema(description = "列名")
    private String columnName;

    @Schema(description = "前端显示名称")
    private String displayName;

    @Schema(description = "关联关系业务编号")
    private String relationBusinessNo;

    @Schema(description = "关联关系名称")
    private String relationName;

    @Schema(description = "是否加密存储: 0-否, 1-是")
    private Integer encrypted;

    @Schema(description = "字段业务配置值列表（级联保存）")
    private List<SysFieldValueSaveReq> fieldValues;
}
