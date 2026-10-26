package com.jdec.platform.common.biz.service;

import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import java.util.List;

/**
 * 菜单查询结果与候选项数据增强服务接口
 *
 * <p>核心职责：将底层低代码数据引擎返回的原始物理数据行及候选项，根据字段业务类型（businessTypeCode）
 * 与系统元数据（创建人、业务状态、文件/图片等）批量增强装配为领域结构化对象。
 */
public interface QueryResultEnhanceService {

    /**
     * 批量增强物理数据行（用户信息、业务状态、文件/图片等对象转换）
     *
     * @param records 数据行列表
     * @param rootModuleId 根模块 ID
     * @param primaryTable 主表表名
     */
    void enhanceDataRows(List<DataRow> records, Long rootModuleId, String primaryTable);

    /**
     * 批量增强物理数据行（含表头业务类型映射解析）
     *
     * @param records 数据行列表
     * @param rootModuleId 根模块 ID
     * @param primaryTable 主表表名
     * @param headerRoot 菜单表头多级列配置树
     */
    void enhanceDataRows(
            List<DataRow> records,
            Long rootModuleId,
            String primaryTable,
            MenuHeaderColumnResp headerRoot);

    /**
     * 增强下拉候选项展示（用户信息、业务状态等对象转换）
     *
     * @param rawOptions 底层数据引擎返回的原始候选项
     * @param columnName 字段列名
     * @param rootModuleId 根模块 ID
     * @return 增强后的候选项列表
     */
    List<DataOptionResp> enhanceOptions(
            List<DataOptionResp> rawOptions, String columnName, Long rootModuleId);

    /**
     * 增强下拉候选项展示（指定业务类型编码）
     *
     * @param rawOptions 底层数据引擎返回的原始候选项
     * @param columnName 字段列名
     * @param rootModuleId 根模块 ID
     * @param businessTypeCode 业务类型编码（如 image, attachment 等）
     * @return 增强后的候选项列表
     */
    List<DataOptionResp> enhanceOptions(
            List<DataOptionResp> rawOptions,
            String columnName,
            Long rootModuleId,
            String businessTypeCode);

    /**
     * 增强下拉候选项展示（通过表头和字段ID动态解析业务类型编码）
     *
     * @param rawOptions 底层数据引擎返回的原始候选项
     * @param columnName 字段列名
     * @param rootModuleId 根模块 ID
     * @param headerRoot 菜单表头多级列配置树
     * @param fieldId 目标字段 ID
     * @return 增强后的候选项列表
     */
    List<DataOptionResp> enhanceOptions(
            List<DataOptionResp> rawOptions,
            String columnName,
            Long rootModuleId,
            MenuHeaderColumnResp headerRoot,
            Long fieldId);
}
