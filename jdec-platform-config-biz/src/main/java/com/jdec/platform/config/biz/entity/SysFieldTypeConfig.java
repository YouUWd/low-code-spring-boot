package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/** 字段类型业务配置实体，平台级字典表，不区分主体与项目 */
@Data
@TableName("sys_field_type_config")
@Schema(description = "字段类型业务配置")
public class SysFieldTypeConfig implements Serializable {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "数据库字段类型，如 char、varchar、datetime、date")
    private String dataType;

    @Schema(description = "业务类型，如 STRING、DATETIME")
    private String businessType;

    @Schema(description = "业务类型名称，如 文本、日期时间")
    private String businessTypeName;
}
