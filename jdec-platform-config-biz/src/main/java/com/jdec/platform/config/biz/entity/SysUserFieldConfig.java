package com.jdec.platform.config.biz.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户字段配置 Entity
 *
 * <p>对应数据库表: sys_user_field_config
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SysUserFieldConfig {

    /** 用户字段配置 ID */
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 模块 ID */
    private Long moduleId;

    /** 字段编码 */
    private String fieldCode;

    /** 是否可见 */
    private Boolean visible;

    /** 展示顺序 */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
