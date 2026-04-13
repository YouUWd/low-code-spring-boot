package com.jdec.platform.config.biz.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模块字段配置 Entity
 *
 * <p>对应数据库表: sys_module_field_config
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SysModuleFieldConfig {

    /** 字段配置 ID */
    private Long id;

    /** 模块 ID */
    private Long moduleId;

    /** 字段编码 */
    private String fieldCode;

    /** 字段中文名称 */
    private String fieldName;

    /** 字段类型 */
    private String fieldType;

    /** 是否可见 */
    private Boolean visible;

    /** 展示顺序 */
    private Integer sortOrder;

    /** 字段宽度 */
    private Integer width;

    /** 对齐方式 */
    private String align;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
