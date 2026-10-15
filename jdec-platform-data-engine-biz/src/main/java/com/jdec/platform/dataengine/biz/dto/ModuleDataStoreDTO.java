package com.jdec.platform.dataengine.biz.dto;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.enums.SaveType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModuleDataStoreDTO {

    @Schema(description = "模块嵌套数据,前端传入")
    private NestedModuleDataDTO paramData;

    @Schema(description = "模块嵌套数据,数据库查询")
    private NestedModuleDataDTO detailData;

    @Schema(description = "保存类型")
    private SaveType saveType;

    @Schema(description = "是否审批")
    private Boolean isApproval;

    @Schema(description = "模块元数据")
    private Map<Long, SysModuleCombineInfo> moduleCacheMap;

    @Schema(description = "整体是否插入,还是新增")
    private Boolean isOverallInsert;

    private Long rootModuleId;

    private Long structureRootModuleId;

    private String mainTableBusinessNo;

    private Integer mainTableVersionNo;

    private Long mainTableDataId;

    private Integer mainTableSourceVersionNo;

    private Long mainTableDataSourceId;
}
