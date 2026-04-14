package com.jdec.platform.config.biz.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统模块 Entity
 *
 * <p>对应数据库表: sys_module
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SysModule {

    /** 模块 ID */
    private Long id;

    /** 模块编码 (唯一) */
    private String moduleCode;

    /** 模块名称 */
    private String moduleName;

    /** 模块描述 */
    private String description;

    /** 是否启用 */
    private Boolean enabled;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
