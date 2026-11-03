package com.jdec.platform.common.biz.postprocess.enhance.context;

import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import lombok.Builder;
import lombok.Getter;

/**
 * 候选项增强上下文
 *
 * <p>封装下拉框、候选项增强所需的字段与业务元数据
 */
@Getter
@Builder
public class OptionEnhanceContext {

    /** 原始列名（可能包含表前缀，如 info.created_by） */
    private final String columnName;

    /** 规整化后的列名（已去除表前缀并转为小写，如 created_by） */
    private final String normalizedColumn;

    /** 根模块 ID */
    private final Long rootModuleId;

    /** 业务类型编码（如 user, status, industry, word_cloud 等） */
    private final String businessTypeCode;

    /** 菜单多级列头树（可选） */
    private final MenuHeaderColumnResp headerRoot;

    /** 字段元数据 ID（可选） */
    private final Long fieldId;

    /** 匹配到的当前字段列头配置节点（可选） */
    private final MenuHeaderColumnResp targetHeaderNode;
}
