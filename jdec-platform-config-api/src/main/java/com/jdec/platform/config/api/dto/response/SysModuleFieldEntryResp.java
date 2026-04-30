package com.jdec.platform.config.api.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 模块字段条目响应 DTO
 * 用于列表展示
 */
@Data
public class SysModuleFieldEntryResp {

    /**
     * 字段 ID
     */
    private Long id;

    /**
     * 模块 ID
     */
    private Long moduleId;

    /**
     * 字段编码
     */
    private String fieldCode;

    /**
     * 字段显示名称
     */
    private String displayName;

    /**
     * 逻辑字段名
     */
    private String logicalField;

    /**
     * 渲染类型
     */
    private String renderType;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

    /**
     * 是否可见: 0-不可见, 1-可见
     */
    private Integer visible;

    /**
     * 是否必填: 0-非必填, 1-必填
     */
    private Integer required;

    /**
     * 是否可搜索: 0-不可搜索, 1-可搜索
     */
    private Integer searchable;

    /**
     * 是否可排序: 0-不可排序, 1-可排序
     */
    private Integer sortable;

    /**
     * 创建时间
     */
    private LocalDateTime createdDate;

    /**
     * 更新时间
     */
    private LocalDateTime updatedDate;
}
