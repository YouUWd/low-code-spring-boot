package com.jdec.platform.common.biz.postprocess.enhance.context;

import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import lombok.Builder;
import lombok.Getter;

/**
 * 数据行增强上下文
 *
 * <p>封装单次查询结果集增强所需的基础元数据
 */
@Getter
@Builder
public class DataRowEnhanceContext {

    /** 根模块 ID */
    private final Long rootModuleId;

    /** 主表物理表名 */
    private final String primaryTable;

    /** 菜单多级列头树（可选） */
    private final MenuHeaderColumnResp headerRoot;
}
