package com.jdec.platform.data.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 菜单待办/已办/全部综合数量统计响应模型 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "菜单待办/已办/全部综合数量统计响应模型")
public class MenuApprovalCountResp implements Serializable {

    @Schema(description = "待办数量 (todoCount)", example = "5")
    private Long todoCount;

    @Schema(description = "已办数量 (doneCount)", example = "12")
    private Long doneCount;

    @Schema(description = "全部记录数量 (totalCount)", example = "20")
    private Long totalCount;
}
