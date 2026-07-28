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
@TableName("sys_monitor_role_snapshot")
@Schema(description = "角色信息快照")
public class JdecMonitorRoleSnapshot {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "角色ID")
    @TableField("roleId")
    private Long roleId;

    @Schema(description = "角色名称")
    @TableField("roleName")
    private String roleName;

    @Schema(description = "快照日期（YYYY-MM-DD）")
    @TableField("snapshotDate")
    private LocalDate snapshotDate;

    @Schema(description = "创建时间")
    @TableField("createTime")
    private LocalDateTime createTime;
}
