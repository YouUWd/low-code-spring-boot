package com.jdec.platform.monitorlog.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 监控日志基类 */
@Data
public class JdecMonitorLogBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "主题ID")
    private Long subjectId;

    @Schema(description = "监控类型")
    private String monitorType;

    @Schema(description = "日志内容")
    private String content;

    @Schema(description = "日志备注")
    private String remark;

    @Schema(description = "登录用户ID")
    private Long loginUserId;

    @Schema(description = "模拟用户ID")
    private Long mockUserId;

    @Schema(description = "是否模拟 0:否 1:是")
    private Integer mockFlag;

    @Schema(description = "登录角色ID")
    private String loginRole;

    @Schema(description = "模拟角色ID")
    private String mockRole;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "用户信息")
    private String userInfo;

    @Schema(description = "IP地址")
    private String ipAddr;

    @Schema(description = "设备信息")
    private String device;

    @Schema(description = "平台信息")
    private String platForm;

    @Schema(description = "日志日期")
    private LocalDateTime logDate;

    @Schema(description = "创建人ID")
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @Schema(description = "创建人名称")
    @TableField(fill = FieldFill.INSERT)
    private String createdName;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdDate;

    @Schema(description = "更新人ID")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @Schema(description = "更新人名称")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedName;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedDate;

    @Schema(description = "是否删除")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Integer deleted = 0;
}
