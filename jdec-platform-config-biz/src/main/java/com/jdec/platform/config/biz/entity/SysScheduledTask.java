package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 定时任务表 实体类 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_scheduled_task")
@Schema(description = "定时任务")
public class SysScheduledTask implements Serializable {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "项目编号")
    private String projectNo;

    @Schema(description = "主体 ID")
    private Long subjectId;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "请求地址")
    private String requestUrl;

    @Schema(description = "请求方式: GET, POST, PUT, DELETE")
    private String requestMethod;

    @Schema(description = "请求头(JSON 字符串, Map 格式)")
    private String requestHeaders;

    @Schema(description = "请求体")
    private String requestBody;

    @Schema(description = "请求参数(JSON 字符串, Map 格式, 拼接到 URL)")
    private String requestParams;

    @Schema(description = "计划执行时间")
    private LocalDateTime executeTime;

    @Schema(description = "任务状态: 0-新建, 1-执行成功, 2-执行失败")
    private Integer taskStatus;

    @Schema(description = "已执行次数")
    private Integer executeCount;

    @Schema(description = "HTTP 响应内容")
    private String executeResult;

    @Schema(description = "失败原因")
    private String errorMsg;

    @Schema(description = "实际开始执行时间")
    private LocalDateTime executeStartTime;

    @Schema(description = "实际结束执行时间")
    private LocalDateTime executeEndTime;

    @Schema(description = "创建人 ID")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

    @Schema(description = "创建人姓名")
    private String createdName;

    @Schema(description = "更新人 ID")
    private String updatedBy;

    @Schema(description = "更新时间")
    private LocalDateTime updatedDate;

    @Schema(description = "更新人姓名")
    private String updatedName;

    @Schema(description = "是否删除: 0-未删除, 1-已删除")
    @TableLogic
    private Integer deleted;
}
