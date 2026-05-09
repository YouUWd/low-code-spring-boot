package com.jdec.platform.config.api.dto.response;

import com.jdec.platform.config.api.enums.ModuleTypeEnum;
import java.util.List;
import lombok.Data;

/** 模块树形响应 DTO 用于树形结构展示 */
@Data
public class SysModuleTreeResp {

    /** 模块 ID */
    private Long id;

    /** 模块编码 */
    private String moduleCode;

    /** 模块名称 */
    private String moduleName;

    /** 模块类型: LIST-列表, DETAIL-详情 */
    private ModuleTypeEnum moduleType;

    /** 模块状态: active-启用, inactive-禁用, archived-归档 */
    private String moduleStatus;

    /** 是否为叶子节点: 0-否, 1-是 */
    private Integer leafFlag;

    /** 排序顺序 */
    private Integer sortOrder;

    /** 子模块列表 */
    private List<SysModuleTreeResp> children;
}
