package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/** 项目主体关联表，由项目管理同步 */
@Data
@TableName("sys_project_subject")
@Schema(description = "项目主体关联")
public class SysProjectSubject implements Serializable {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "主体名称")
    private String subjectName;

    @Schema(description = "公司名称")
    private String companyName;

    @Schema(description = "别名")
    private String alias;

    @Schema(description = "主体ID")
    private Long subjectId;

    @Schema(description = "项目编码")
    private String projectNo;
}
