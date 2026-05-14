package com.jdec.platform.config.api.dto.response;

import com.jdec.platform.config.api.enums.ModuleTypeEnum;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** 模块详情响应 DTO 用于详情页面，包含完整信息 */
@Data
public class SysModuleDetailResp {

    /** 模块 ID */
    private Long id;

    /** 项目编号 */
    private String projectNo;

    /** 主体ID */
    private Long subjectId;

    /** 模块编码 */
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

    /** 模块类型: LIST-列表, DETAIL-详情 */
    private ModuleTypeEnum moduleType;

    /** 是否启用审批: 0-否, 1-是 */
    private Integer approvalRequired;

    /** 模块状态: active-启用, inactive-禁用, archived-归档 */
    private String moduleStatus;

    /** 是否为叶子节点: 0-否, 1-是 */
    private Integer leafFlag;

    /** 排序顺序 */
    private Integer sortOrder;

    /** 列表表头配置（JSON格式） */
    private List<SysModuleCompleteResp.TableHeaderColumnInfo> tableHeader;

    /** 版本号 */
    private Integer version;

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
