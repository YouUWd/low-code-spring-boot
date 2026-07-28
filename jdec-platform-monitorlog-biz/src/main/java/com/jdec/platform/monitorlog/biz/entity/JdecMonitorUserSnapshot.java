package com.jdec.platform.monitorlog.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sys_monitor_user_snapshot")
@Schema(description = "用户信息快照")
public class JdecMonitorUserSnapshot {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "用户ID")
    @TableField("userId")
    private Long userId;

    @Schema(description = "用户头像")
    @TableField("userAvatar")
    private String userAvatar;

    @Schema(description = "用户名称")
    @TableField("userName")
    private String userName;

    @Schema(description = "工号")
    @TableField("workNumber")
    private String workNumber;

    @Schema(description = "快照日期（YYYY-MM-DD）")
    @TableField("snapshotDate")
    private LocalDate snapshotDate;

    @Schema(description = "创建时间")
    @TableField("createdTime")
    private LocalDateTime createdTime;
}
