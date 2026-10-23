package com.jdec.platform.common.biz.custom;

/**
 * 行级定制操作上下文
 *
 * @param projectNo 项目编码 (如 "company")
 * @param rootModuleId 根模块 ID
 * @param moduleCode 模块编码 (如 "COMPANY-ROOT")
 * @param primaryTable 主表物理表名 (如 "info")
 * @param enableActive 是否开启启停功能 (0-否, 1-是)
 * @param changeModuleId 变更申请模块ID
 * @param userId 当前用户ID
 * @param roleId 当前角色ID
 * @param hasEditPermission 是否具备编辑权限
 */
public record CustomActionContext(
        String projectNo,
        Long rootModuleId,
        String moduleCode,
        String primaryTable,
        Integer enableActive,
        Long changeModuleId,
        Long userId,
        Long roleId,
        boolean hasEditPermission) {}
