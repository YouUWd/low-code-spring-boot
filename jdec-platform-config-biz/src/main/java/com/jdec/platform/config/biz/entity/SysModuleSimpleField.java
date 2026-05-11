package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 模块简单字段配置实体 用于单表字段映射 */
@Data
@TableName("sys_module_simple_field")
public class SysModuleSimpleField {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模块 ID */
    private Long moduleId;

    /** 表名 */
    private String tableName;

    /** 列名 */
    private String columnName;

    /** 转换表达式 */
    private String transformer;

    /** 转换环境: database-数据库级, frontend-前端级, none-无转换 */
    private String transformerEnv;

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
