package com.jdec.platform.dataengine.api.dto.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "记录数据")
public class RecordDataDTO implements java.io.Serializable {

    @Schema(description = "主表，关联表，组合的行记录，主表只有一行")
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    private Map<String, Object> row; // 可以是 SingleTableRowDataDTO 或者 List<SingleTableRowDataDTO>

    private List<NestedModuleDataDTO> children;

    @Schema(description = "版本对比数据")
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    private Map<String, Map<String, Object>> changeList;

    @Schema(description = "新增或修改标识")
    private String changeType;
}
