package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 系统初始化结果响应 */
@Data
@Schema(description = "系统初始化结果")
public class SystemInitResp {

    @Schema(description = "项目主体总数")
    private Integer totalCount;

    @Schema(description = "初始化成功数量")
    private Integer successCount;

    @Schema(description = "各项目主体初始化明细")
    private List<ProjectInitResult> projects;

    /** 单个项目主体的初始化明细 */
    @Data
    @Schema(description = "项目主体初始化明细")
    public static class ProjectInitResult {

        @Schema(description = "主体ID")
        private Long subjectId;

        @Schema(description = "项目编码")
        private String projectNo;

        @Schema(description = "主体名称")
        private String subjectName;

        @Schema(description = "是否初始化成功")
        private Boolean success;

        @Schema(description = "执行过程说明（各步骤结果为 新增/已存在跳过）")
        private List<String> steps;

        @Schema(description = "失败原因（成功时为空）")
        private String errorMsg;
    }
}
