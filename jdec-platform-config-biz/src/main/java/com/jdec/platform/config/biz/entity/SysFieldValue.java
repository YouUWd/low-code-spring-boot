package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 系统表-字段值配置表 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_field_value")
public class SysFieldValue {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字段配置ID */
    private Long fieldId;

    /** 所属模块ID */
    private Long moduleId;

    /** 模块名称（冗余） */
    private String moduleName;

    /** 字段特定配置值 */
    private String fieldValue;

    /** 关联审批链类型ID */
    private Long approvalChainTypeId;

    /** 显示排序 */
    private Integer sortOrder;

    /** 是否启用: 0-禁用, 1-启用 */
    private Integer enabled;

    /** 创建人ID */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdDate;

    /** 创建人姓名 */
    private String createdName;

    /** 更新人ID */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedDate;

    /** 更新人姓名 */
    private String updatedName;

    /** 是否删除: 0-未删除, 1-已删除 */
    private Integer deleted;
}
