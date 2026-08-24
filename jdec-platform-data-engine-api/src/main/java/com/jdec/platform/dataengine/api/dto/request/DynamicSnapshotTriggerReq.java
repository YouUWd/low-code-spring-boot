package com.jdec.platform.dataengine.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "审批终结触发生成业务库全局快照请求模型")
public class DynamicSnapshotTriggerReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "模块 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "134")
    private Long moduleId;

    @Schema(
            description = "业务主表主键 ID (聚合根 ID)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "1001")
    private Long dataId;

    @Schema(description = "业务单据编号 (可选)", example = "STU202608200001")
    private String businessNo;

    @Schema(
            description = "终结状态值: 99-审批通过生效归档, -99-驳回/作废终止",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "99")
    private Integer finalStatusValue;

    @Schema(description = "变更说明/归档备注", example = "期末成绩调整审批通过并正式归档")
    private String remark;
}
