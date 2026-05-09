package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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

    /** 租户 ID */
    private String tenantId;

    /** 模块唯一编码 */
    private String moduleCode;

    /** 模块名称 */
    private String moduleName;

    /** 模块描述 */
    private String moduleDesc;

    /** 父模块 ID */
    private Long parentId;

    /** 模块层级 */
    private Integer moduleLevel;

    /** 模块路径 */
    private String modulePath;

    /** 主表名称 */
    private String primaryTable;

    /** 模块类型 */
    private ModuleTypeEnum moduleType;

    /** 模块状态 */
    private String moduleStatus;

    /** 是否叶子节点 */
    private Integer leafFlag;

    /** 排序顺序 */
    private Integer sortOrder;

    /** 列表表头配置（JSON格式） */
    private String tableHeader;

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

    /** 是否删除 */
    private Integer deleted;
}
