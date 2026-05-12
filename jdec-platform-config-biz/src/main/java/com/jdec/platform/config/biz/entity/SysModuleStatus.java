package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 模块基本信息实体 */
@Data
@TableName("sys_module_status")
public class SysModuleStatus {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模块ID */
    private Long moduleId;

    /** 状态父ID */
    private Long statusPid;

    /** 状态 ID */
    private Long statusId;

    /** 创建人 ID */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdDate;

    /** 创建人姓名 */
    private String createdName;

    /** 更新人 ID */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedDate;

    /** 更新人姓名 */
    private String updatedName;
}
