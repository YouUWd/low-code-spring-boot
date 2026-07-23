package com.jdec.platform.common_business.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.*;

@Data
@EqualsAndHashCode
@Schema(description = "数据操作变更对象")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationChangeDTO implements Serializable {

    //    @Schema(description = "插入的行")
    //    private List<RowChangeDTO> insertRows;
    //
    //    @Schema(description = "更新的行")
    //    private List<RowChangeDTO> updateRows;
    //
    //    @Schema(description = "删除的行")
    //    private List<RowChangeDTO> deleteRows;

    List<ModuleDataChangeDTO> modifyModules;
}
