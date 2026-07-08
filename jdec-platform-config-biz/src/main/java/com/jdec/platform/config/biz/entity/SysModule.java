package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.jdec.platform.config.api.enums.ModuleTypeEnum;
import java.time.LocalDateTime;
import lombok.Data;

/** 模块基本信息实体 */
@Data
@TableName("sys_module")
public class SysModule {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目编号 */
    private String projectNo;

    private Long subjectId;

    /** 模块唯一编码 */
    private String moduleCode;

    /** 模块名称 */
    private String moduleName;

    /** 模块描述 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String moduleDesc;

    /** 父模块 ID */
    private Long parentId;

    /** 详情模块 ID */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long detailModuleId;

    /** 主表名称 */
    private String primaryTable;

    /** 主表外键关联字段 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String relateSearchField;

    /** 模块类型 */
    private ModuleTypeEnum moduleType;

    /** 是否启用审批 * */
    private Integer approvalRequired;

    /** 排序顺序 */
    private Integer sortOrder;

    /** 列表表头配置（JSON格式） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String tableHeader;

    /** 数据来源主体，内容为主体名字的数组（JSON格式） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceSubjects;

    /** 是否模块业务定义 */
    private Integer bizDefFlag;

    /** 模块类别: 1-业务模块, 2-系统模块 */
    private Integer category;

    /** 创建人 ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdDate;

    /** 创建人姓名 */
    private String createdName;

    /** 更新人 ID */
    private Long updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedDate;

    /** 更新人姓名 */
    private String updatedName;
}
