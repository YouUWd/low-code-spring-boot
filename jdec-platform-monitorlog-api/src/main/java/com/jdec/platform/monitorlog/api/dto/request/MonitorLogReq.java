package com.jdec.platform.monitorlog.api.dto.request;

import cn.hutool.core.date.DateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 记录兼容日志请求 */
@Data
@Schema(description = "记录兼容日志请求")
public class MonitorLogReq {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "主题ID")
    private Long subjectId;

    /** 监控类型 requiredMode = Schema.RequiredMode.REQUIRED 标记此字段为必填项 */
    @Schema(description = "监控类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "监控类型不能为空")
    private String monitorType;

    @Schema(description = "日志内容", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "日志内容不能为空")
    private String logContent;

    @Schema(description = "日志备注", requiredMode = Schema.RequiredMode.REQUIRED)
    private String logRemark;

    @Schema(description = "登录用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long loginUserId;

    @Schema(description = "登录用户名称")
    private String loginUserName;

    @Schema(description = "模拟用户ID")
    private Long mockUserId;

    @Schema(description = "模拟用户名称")
    private String mockUserName;

    @Schema(description = "是否mock标记：0，否；1 是")
    private Integer mockFlag;

    @Schema(description = "登录角色")
    private Long loginRole;

    @Schema(description = "模拟角色ID")
    private Long mockRole;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "用户信息", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户信息不能为空")
    private String userInfo;

    @Schema(description = "IP地址", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "IP地址不能为空")
    private String ip;

    @Schema(description = "浏览器", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "浏览器不能为空")
    private String browser;

    @Schema(description = "平台信息", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "平台信息不能为空")
    private String platForm;

    @Schema(description = "日志时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "日志时间不能为空")
    private DateTime logDate;

    @Schema(description = "创建人ID")
    private Long createdBy;

    @Schema(description = "创建人名称")
    private String createdName;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "创建时间不能为空")
    private DateTime createdDate;

    @Schema(description = "更新人ID")
    private Long updatedBy;

    @Schema(description = "更新人名称")
    private String updatedName;

    @Schema(description = "更新时间")
    private DateTime updatedDate;

    @Schema(description = "日志状态：1，删除；0，正常。")
    private Integer deleted;
}
