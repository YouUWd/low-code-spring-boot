package com.jdec.platform.common_business.biz.change;

import com.jdec.platform.common_business.api.dto.common.RowChangeDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Schema(description = "表数据数据变更对象")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableDataChangeDTO implements Serializable {

    @Schema(description = "表名")
    private String tableName;

    @Schema(description = "插入的行")
    private List<RowChangeDTO> insertRows;

    @Schema(description = "更新的行")
    private List<RowChangeDTO> updateRows;

    @Schema(description = "删除的行")
    private List<RowChangeDTO> deleteRows;
}
