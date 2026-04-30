package com.jdec.platform.config.api.dto.request;

import lombok.Data;

/**
 * 创建模块请求 DTO
 */
@Data
public class CreateSysModuleReq {

    /**
     * 租户 ID
     */
    private String tenantId;

    /**
     * 模块唯一编码，如 MOD-STUDENT-FULL
     */
    private String moduleCode;

    /**
     * 模块名称
     */
    private String moduleName;

    /**
     * 模块描述
     */
    private String moduleDesc;

    /**
     * 父模块 ID，NULL 表示根模块
     */
    private Long parentId;

    /**
     * 主表名称，folder 类型可为空
     */
    private String primaryTable;

    /**
     * 模块类型: query-查询模块, folder-文件夹, custom-自定义
     */
    private String moduleType;

    /**
     * 同级排序顺序，数字越小越靠前
     */
    private Integer sortOrder;

    /**
     * 创建人 ID
     */
    private String createdBy;

    /**
     * 创建人姓名
     */
    private String createdName;
}
