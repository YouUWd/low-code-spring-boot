package com.jdec.platform.common_business.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "数据权限数据查询请求参数")
public class DataRightAccessReq {

    @Schema(description = "表名称")
    private String tableName;

    @Schema(description = "列名称")
    private String column;

    @Schema(description = "主体ID列表")
    private List<Long> subjectIds;
}
