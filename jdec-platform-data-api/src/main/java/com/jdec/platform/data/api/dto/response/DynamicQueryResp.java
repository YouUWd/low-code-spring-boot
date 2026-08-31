package com.jdec.platform.data.api.dto.response;

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
@Schema(description = "动态一体化列表分页响应模型 (含表头元数据、分页信息与表维度行记录)")
public class DynamicQueryResp implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "表头元数据 (包含 moduleId, primaryTable, tableHeader 列表及取数路径)")
    private Map<String, Object> meta;

    @Schema(description = "分页信息 (包含 pageIndex, pageSize, total, totalPages)")
    private Map<String, Object> pagination;

    @Schema(
            description =
                    "行记录列表 (每行为以表名为 Key 的结构化记录字典: 1:1 为 Map<String, Object>, 1:N 为 List<Map<String, Object>>)")
    private List<Map<String, Object>> records;
}
