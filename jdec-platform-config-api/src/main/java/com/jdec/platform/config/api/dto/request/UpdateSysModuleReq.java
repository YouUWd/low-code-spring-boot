package com.jdec.platform.config.api.dto.request;

import lombok.Data;

/** 更新模块请求 DTO */
@Data
public class UpdateSysModuleReq {

    /** 模块 ID */
    private Long id;

    /** 详情模块 ID */
    private Long detailModuleId;

    /** 模块名称 */
    private String moduleName;

    /** 模块描述 */
    private String moduleDesc;

    /** 主表名称 */
    private String primaryTable;

    /** 模块类型: query-查询模块, folder-文件夹, custom-自定义 */
    private String moduleType;

    /** 是否启用审批: 0-否, 1-是 */
    private Integer approvalRequired;

    /** 是否模块业务定义: 0-否, 1-是 */
    private Integer bizDefFlag;

    /** 同级排序顺序，数字越小越靠前 */
    private Integer sortOrder;

    /** 版本号，用于乐观锁 */
    private Integer version;

    /** 更新人 ID */
    private String updatedBy;

    /** 更新人姓名 */
    private String updatedName;
}
