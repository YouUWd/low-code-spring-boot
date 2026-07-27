package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块字段配置统一实体（合并版） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_module_field")
public class SysModuleField {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模块 ID */
    private Long moduleId;

    /** 字段类型：SIMPLE - 简单物理字段, COMBINE - 复合组合字段 */
    private String fieldType;

    /** 字段通用编码（SIMPLE对应物理列名，COMBINE对应逻辑驼峰字段） */
    private String fieldCode;

    /** 物理表名（SIMPLE 存物理表名，COMBINE 统一固定存 "*"） */
    private String tableName;

    /** 显示名称（COMBINE专属，SIMPLE由物理注释或逻辑字典翻译） */
    private String displayName;

    /** 物理多表映射，JSON 格式（COMBINE专属） */
    private String sourceMapping;

    /** 转换表达式 */
    private String transformer;

    /** 转换环境: database-数据库级, frontend-前端级, none-无转换 */
    private String transformerEnv;

    /** 是否启用：0-禁用, 1-启用（COMBINE专属） */
    private Integer enabled;

    /** 业务唯一标识顺序：大于0表示是业务唯一标识字段及其顺序，null或0表示否 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer bizKeyOrder;
}
