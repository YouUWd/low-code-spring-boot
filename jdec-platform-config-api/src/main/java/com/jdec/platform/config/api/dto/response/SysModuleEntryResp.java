package com.jdec.platform.config.api.dto.response;

import com.jdec.platform.config.api.enums.ModuleTypeEnum;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 模块条目响应 DTO
 * 用于列表展示，包含基本信息
 */
@Data
public class SysModuleEntryResp {

    /**
     * 模块 ID
     */
    private Long id;

    /**
     * 租户 ID
     */
    private String tenantId;

    /**
     * 模块编码
     */
    private String moduleCode;

    /**
     * 模块名称
     */
    private String moduleName;

    /**
     * 模块类型: LIST-列表, DETAIL-详情
     */
    private ModuleTypeEnum moduleType;

    /**
     * 模块状态: active-启用, inactive-禁用, archived-归档
     */
    private String moduleStatus;

    /**
     * 模块层级
     */
    private Integer moduleLevel;

    /**
     * 是否为叶子节点: 0-否, 1-是
     */
    private Integer leafFlag;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

    /**
     * 创建时间
     */
    private LocalDateTime createdDate;

    /**
     * 更新时间
     */
    private LocalDateTime updatedDate;
}
