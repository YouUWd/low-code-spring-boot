package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 数据库字段配置表 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_field")
public class SysField {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目编号 */
    private String projectNo;

    /** 主体ID */
    private Long subjectId;

    /** 表名 */
    private String tableName;

    /** 列名 */
    private String columnName;

    /** 前端显示名称 */
    private String displayName;

    /** 关联关系业务编号 */
    private String relationBusinessNo;

    /** 关联关系名称 */
    private String relationName;

    /** 是否生成权限节点.0--否,1--是 */
    private Integer dataRightFlag;

    /** 是否加密存储: 0-否, 1-是 */
    private Integer encrypted;

    /** 组合配置信息（JSON 格式：包含 sourceMapping 与 transformer） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String combineInfo;

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
