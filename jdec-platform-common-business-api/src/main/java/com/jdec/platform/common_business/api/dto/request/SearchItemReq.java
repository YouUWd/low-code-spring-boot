package com.jdec.platform.common_business.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "筛选项")
public class SearchItemReq implements Serializable {

    @Schema(description = "表名称")
    private String table;

    @Schema(description = "字段名")
    private List<String> field;

    @Schema(description = "条件类型")
    private String op;

    @Schema(description = "字段值")
    private Object value;

    @Schema(description = "搜索类型")
    private String searchType;

    @Schema(description = "列ID")
    private Long columnId;

    @Schema(description = "搜索关联")
    private String searchRelation;

    @Schema(description = "搜索表")
    private String searchTable;
}
