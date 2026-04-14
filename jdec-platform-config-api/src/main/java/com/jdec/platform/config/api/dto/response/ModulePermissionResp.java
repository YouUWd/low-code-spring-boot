package com.jdec.platform.config.api.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块权限响应 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModulePermissionResp {
    /** 权限 ID */
    private Long id;

    /** 模块 ID */
    private Long moduleId;

    /** 字段编码 */
    private String fieldCode;

    /** 权限类型 */
    private String permissionType;

    /** 权限编码 */
    private String permissionCode;

    /** 权限名称 */
    private String permissionName;

    /** 权限描述 */
    private String description;

    /** 是否启用 */
    private Boolean enabled;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
