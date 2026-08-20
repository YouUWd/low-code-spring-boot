package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 创建企业微信消息通知模板请求 DTO */
@Data
@Schema(description = "创建企业微信消息通知模板请求")
public class CreateWechatTemplateReq {

    @Schema(description = "消息标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "审批提醒")
    private String templateTitle;

    @Schema(description = "发送类型(配置ID)", example = "186")
    private Long templateSendType;

    @Schema(description = "接收消息类型(配置ID数组)", example = "[179, 176]")
    private List<Long> templateAcceptType;

    @Schema(description = "触发类型(配置ID)", example = "181")
    private Long templateTriggerType;

    @Schema(description = "消息内容")
    private String templateContent;

    @Schema(description = "额外接收人 ID 数组", example = "[58, 59]")
    private List<Long> extraReceiverIds;

    @Schema(description = "额外接收人姓名(逗号分隔)", example = "张三,李四")
    private String extraReceiverNames;

    @Schema(description = "消息参数配置 ID 数组", example = "[10, 20]")
    private List<Long> configParamIds;

    @Schema(description = "模块 ID", example = "27")
    private Long moduleId;

    @Schema(description = "模块名称", example = "部门管理")
    private String moduleName;

    @Schema(description = "是否审批链: 0-否, 1-是", example = "1")
    private Integer approvalFlag;
}
