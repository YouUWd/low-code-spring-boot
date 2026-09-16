package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/** 字段搜索条件配置实体，平台级字典表，不区分主体与项目 */
@Data
@TableName("sys_field_search_config")
@Schema(description = "字段搜索条件配置")
public class SysFieldSearchConfig implements Serializable {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "字段类型配置ID")
    private Long fieldTypeId;

    @Schema(description = "搜索操作编码，如 EQ、CONTAINS、BETWEEN")
    private String code;

    @Schema(description = "搜索操作名称，如 精确匹配、模糊匹配")
    private String name;

    @Schema(description = "前端组件，如 TEXT、DATE_TIME、DATE_TIME_RANGE")
    private String component;
}
