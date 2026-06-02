package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/** 微信模板参数配置实体 */
@Data
@TableName("sys_wechat_template_param")
public class SysWechatTemplateParam {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目编号 */
    private String projectNo;

    /** 主体 ID */
    private Long subjectId;

    /** 参数名称 */
    private String templateParamName;

    /** 参数标识 */
    private String templateParamSlug;

    /** 描述 */
    private String templateParamDesc;

    /** 关联模块 ID */
    private Long moduleId;

    /** 关联字段 ID */
    private Long fieldId;

    /** 是否需要转换: 0-否, 1-是 */
    private Integer convertFlag;

    /** 转换方法 */
    private String templateParamConvertMethod;

    /** 创建人 ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdDate;

    /** 创建人姓名 */
    @TableField(fill = FieldFill.INSERT)
    private String createdName;

    /** 更新人 ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedDate;

    /** 更新人姓名 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedName;

    /** 是否删除: 0-未删除, 1-已删除 */
    @TableLogic private Integer deleted;
}
