package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.SysFieldSaveReq;
import com.jdec.platform.config.api.dto.response.ColumnInfoResp;
import com.jdec.platform.config.api.dto.response.SysFieldResp;
import com.jdec.platform.config.api.dto.response.TableInfoResp;
import java.util.List;

/** 字段配置 API 接口 */
public interface SysFieldApi {

    /**
     * 获取数据源中所有表的基本信息
     *
     * @param projectNo 项目编码
     * @return 表信息列表
     */
    List<TableInfoResp> getAllTables(String projectNo);

    /**
     * 获取数据源中所有表及其列信息（包含配置增强）
     *
     * @param projectNo 项目编码
     * @param subjectId 主体ID
     * @return 表及列信息列表
     */
    List<TableInfoResp> getAllTablesWithColumns(String projectNo, Long subjectId);

    /**
     * 获取表的所有列信息（包含配置增强）
     *
     * @param projectNo 项目编码
     * @param subjectId 主体ID
     * @param tableName 表名
     * @return 列信息列表
     */
    List<ColumnInfoResp> getTableColumns(String projectNo, Long subjectId, String tableName);

    /**
     * 查询字段配置列表
     *
     * @param projectNo 项目编码
     * @param subjectId 主体ID
     * @param tableName 表名
     * @param columnName 列名（可选）
     * @return 字段配置列表
     */
    List<SysFieldResp> querySysFields(
            String projectNo, Long subjectId, String tableName, String columnName);

    /**
     * 查询简版字段配置列表（不调用词云 Api）
     *
     * @param projectNo 项目编码
     * @param subjectId 主体ID
     * @param tableName 表名
     * @param columnName 列名（可选）
     * @return 字段配置列表
     */
    List<SysFieldResp> querySimpleSysFields(
            String projectNo, Long subjectId, String tableName, String columnName);

    /**
     * 保存或更新字段配置
     *
     * @param projectNo 项目编码
     * @param subjectId 主体ID
     * @param tableName 表名
     * @param req 保存请求
     * @return 保存后的字段配置
     */
    SysFieldResp saveSysField(
            String projectNo, Long subjectId, String tableName, SysFieldSaveReq req);

    /**
     * 查询被设置为生成数据权限节点的字段列表
     *
     * @param projectNo 项目编码
     * @param subjectId 主体ID
     * @return 数据权限字段列表（dataRightFlag=1）
     */
    List<SysFieldResp> getDataPermissionFields(String projectNo, Long subjectId);
}
