package com.jdec.platform.data.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块元数据实体 (精简版，用于 Data 引擎自主读取 config_engine) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_module")
public class DataSysModule {

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

    /** 物理主表名 */
    private String primaryTable;

    /** 父模块 ID (0 表示根模块) */
    private Long parentId;

    /** 排序顺序 */
    private Integer sortOrder;

    /** 创建人 ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdDate;

    /** 更新人 ID */
    private Long updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedDate;
}
