package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.jdec.platform.config.biz.reference.Reference;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 低代码业务模块定义实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "sys_module", autoResultMap = true)
public class SysModule {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目编号 */
    private String projectNo;

    /** 所属主体 ID */
    private Long subjectId;

    /** 模块唯一编码 */
    private String moduleCode;

    /** 模块名称 */
    private String moduleName;

    /** 模块描述 */
    private String moduleDesc;

    /** 模块类别: 1-业务模块, 2-系统模块 */
    private Integer category;

    /** 数据来源主体ID集合 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> sourceSubjects;

    /** 父模块 ID (0 表示根模块) */
    @Reference(target = SysModule.class, name = "子模块", displayField = "moduleName")
    private Long parentId;

    /** 排序顺序 */
    private Integer sortOrder;

    /** 主表名称 */
    private String primaryTable;

    /** 创建人 ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdDate;

    /** 更新人 ID */
    private Long updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedDate;
}
