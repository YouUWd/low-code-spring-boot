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

    @Schema(description = "根模块的ID")
    private Long rootModuleId;

    @Schema(description = "保存结构中的根模块ID")
    private Long structureRootModuleId;

    @Schema(description = "根模块主表的业务编码")
    private String mainTableBusinessNo;

    @Schema(description = "跟模块主表的版本号")
    private Integer mainTableVersionNo;

    @Schema(description = "根模块主表的ID")
    private Long mainTableDataId;

    @Schema(description = "变更保存时，根模块主表的原始版本号")
    private Integer mainTableSourceVersionNo;

    @Schema(description = "变更保存时,根模块主表原始ID")
    private Long mainTableDataSourceId;
}
