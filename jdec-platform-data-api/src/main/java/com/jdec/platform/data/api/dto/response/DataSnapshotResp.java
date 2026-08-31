package com.jdec.platform.data.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "业务库聚合快照响应模型")
public class DataSnapshotResp implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "快照主键 ID", example = "58")
    private Long id;

    @Schema(description = "所属模块 ID", example = "134")
    private Long moduleId;

    @Schema(description = "业务主表主键 ID (聚合根 ID)", example = "1001")
    private Long dataId;

    @Schema(description = "快照版本号 (1, 2, 3...)", example = "1")
    private Integer versionNo;

    @Schema(description = "业务单据编号", example = "STU202608200001")
    private String businessNo;

    @Schema(description = "终结状态值 (99 或 -99)", example = "99")
    private Integer finalStatusValue;

    @Schema(description = "完整的聚合 JSON 快照数据")
    private String jsonData;

    @Schema(description = "与上一版本对比的字段级变更差异 (JSON 格式)")
    private String changeDiff;

    @Schema(description = "归档备注", example = "期末成绩调整审批通过并正式归档")
    private String remark;

    @Schema(description = "创建人 ID", example = "147")
    private Long createdBy;

    @Schema(description = "创建人姓名", example = "李审核员")
    private String createdName;

    @Schema(description = "快照生成时间")
    private LocalDateTime createdDate;
}
