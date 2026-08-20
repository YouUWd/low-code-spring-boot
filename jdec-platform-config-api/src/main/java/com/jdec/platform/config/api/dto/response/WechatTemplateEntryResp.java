package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** 企业微信消息通知模板列表项响应 DTO */
@Data
@Schema(description = "企业微信消息通知模板列表项响应")
public class WechatTemplateEntryResp {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "项目编号")
    private String projectNo;

    @Schema(description = "主体 ID")
    private Long subjectId;

    @Schema(description = "消息标题")
    private String templateTitle;

    @Schema(description = "发送类型(配置ID)")
    private Long templateSendType;

    @Schema(description = "接收消息类型(配置ID数组)")
    private List<Long> templateAcceptType;

    @Schema(description = "触发类型(配置ID)")
    private Long templateTriggerType;

    @Schema(description = "模块 ID")
    private Long moduleId;

    @Schema(description = "模块名称")
    private String moduleName;

    @Schema(description = "是否审批链: 0-否, 1-是")
    private Integer approvalFlag;

    @Schema(description = "消息内容")
    private String templateContent;

    @Schema(description = "额外接收人 ID 数组")
    private List<Long> extraReceiverIds;

    @Schema(description = "额外接收人姓名(逗号分隔)")
    private String extraReceiverNames;

    @Schema(description = "消息参数配置 ID 数组")
    private List<Long> configParamIds;

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
}
