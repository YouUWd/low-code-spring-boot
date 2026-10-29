package com.jdec.platform.common.biz.action;

import lombok.Builder;
import lombok.Getter;

/**
 * 菜单按钮集中处理上下文 (MenuActionContext)
 *
 * <p>统一汇集定制操作 (启停等) 与角色互动权限所需的全部上下文信息，避免在主业务中分散维护多个上下文对象。
 */
@Getter
@Builder
public class MenuActionContext {

    /** 当前应用项目代号 (例如: "company") */
    private final String projectNo;

    /** 根模块 ID */
    private final Long rootModuleId;

    /** 根模块编码 (例如: "COMPANY-ROOT") */
    private final String moduleCode;

    /** 业务主表名称 (例如: "info") */
    private final String primaryTable;

    /** 模块是否开启启停功能 (1-开启, 0-关闭) */
    private final Integer enableActive;

    /** 模块是否启用变更申请 (1-开启, 0-关闭) */
    private final Integer enableChange;

    /** 当前请求操作的用户 ID */
    private final Long userId;

    /** 当前用户的角色 ID */
    private final Long roleId;

    /** 当前角色对根模块是否具备编辑权限 (hasEditPermission) */
    private final boolean hasEditPermission;
}
