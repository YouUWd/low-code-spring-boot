package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块状态机绑定实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_module_status")
public class SysModuleStatus {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模块ID */
    private Long moduleId;

    /** 状态父ID (状态类型) */
    private Long statusPid;

    /** 状态 ID */
    private Long statusId;

    /** 创建人 ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdDate;

    /** 更新人 ID */
    private Long updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedDate;

    /** 逻辑删除: 0-未删除, 1-已删除 */
    @TableLogic private Integer deleted;
}
