package com.jdec.platform.config.api.dto.request;

import lombok.Data;

/**
 * 创建模块字段请求 DTO
 */
@Data
public class CreateSysModuleFieldReq {

    /**
     * 所属模块 ID
     */
    private Long moduleId;

    /**
     * 字段唯一编码
     */
    private String fieldCode;

    /**
     * 字段显示名称
     */
    private String displayName;

    /**
     * 逻辑字段名（驼峰格式）
     */
    private String logicalField;

    /**
     * 物理字段映射，JSON 格式
     */
    private String sourceMapping;

    /**
     * 转换表达式
     */
    private String transformer;

    /**
     * 转换环境: database-数据库级, frontend-前端级, none-无转换
     */
    private String transformerEnv;

    /**
     * 渲染图标
     */
    private String renderIcon;

    /**
     * 渲染类型: text, number, date, select 等
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
     * 默认值
     */
    private String defaultValue;

    /**
     * 验证规则
     */
    private String validationRule;

    /**
     * 创建人 ID
     */
    private String createdBy;

    /**
     * 创建人姓名
     */
    private String createdName;
}
