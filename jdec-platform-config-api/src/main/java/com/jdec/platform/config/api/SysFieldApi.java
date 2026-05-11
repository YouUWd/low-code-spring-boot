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
     * 获取表的所有列信息（包含配置增强）
     *
     * @param projectNo 项目编码
     * @param tableName 表名
     * @return 列信息列表
     */
    List<ColumnInfoResp> getTableColumns(String projectNo, String tableName);

    /**
     * 查询字段配置列表
     *
     * @param projectNo 项目编码
     * @param tableName 表名
     * @return 字段配置列表
     */
    List<SysFieldResp> querySysFields(String projectNo, String tableName);

    /**
     * 保存或更新字段配置
     *
     * @param projectNo 项目编码
     * @param tableName 表名
     * @param req 保存请求
     */
    void saveSysField(String projectNo, String tableName, SysFieldSaveReq req);
}
