package com.jdec.platform.config.api.dto.request;

import lombok.Data;

/** 更新权限配置请求 DTO */
@Data
public class UpdateSysPermissionConfigReq {

    /** 权限配置 ID */
    private Long id;

    /** 权限值: 0-7 (READ:1, CREATE:2, UPDATE:4) */
    private Integer permission;

    /** 权限状态: active-启用, inactive-禁用 */
    private String permissionStatus;

    /** 权限描述 */
    private String description;

    /** 更新人 ID */
    private String updatedBy;

    /** 更新人姓名 */
    private String updatedName;
}
