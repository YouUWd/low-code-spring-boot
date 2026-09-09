package com.jdec.platform.dataengine.api.dto.model;

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

    private Map<String, Object> row; // 可以是 TableRowDataDTO 或者 List<TableRowDataDTO>

    private List<RecordDataDTO> subModule;
}
