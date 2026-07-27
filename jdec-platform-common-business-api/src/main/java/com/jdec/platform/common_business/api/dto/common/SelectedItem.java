package com.jdec.platform.common_business.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "待获取数据筛选项")
public class SelectedItem {
    @Schema(description = "表名称")
    private String table;

    @Schema(description = "字段名")
    private String field;
}
