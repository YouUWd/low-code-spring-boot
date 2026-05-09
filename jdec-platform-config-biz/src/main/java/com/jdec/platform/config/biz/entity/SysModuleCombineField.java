package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 模块组合字段配置实体 用于多表字段组合映射 */
@Data
@TableName("sys_module_combine_field")
public class SysModuleCombineField {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模块 ID */
    private Long moduleId;

    /** 逻辑字段名（驼峰格式） */
    private String logicalField;

    /** 字段显示名称 */
    private String displayName;

    /** 物理字段映射，JSON格式: [{"entity":"student","field":"student_no","sort_order":1}] */
    private String sourceMapping;

    /** 转换表达式 */
    private String transformer;

    /** 转换环境: database-数据库级, frontend-前端级, none-无转换 */
    private String transformerEnv;

    /** 是否启用 */
    private Integer enabled;

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

    /** 是否删除: 0-未删除, 1-已删除 */
    private Integer deleted;
}
