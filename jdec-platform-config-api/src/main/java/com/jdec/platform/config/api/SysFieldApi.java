package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.SysFieldSaveReq;
import com.jdec.platform.config.api.dto.response.ColumnInfoResp;
import com.jdec.platform.config.api.dto.response.DatabaseSchemaResp;
import com.jdec.platform.config.api.dto.response.SysFieldResp;
import com.jdec.platform.config.api.dto.response.TableInfoResp;
import java.util.List;

/** 字段配置 API 接口 */
public interface SysFieldApi {

    /**
     * 获取数据源中所有表的基本信息
     *
     * @param tenantId 租户ID（对应 TABLE_SCHEMA）
     * @return 表信息列表
     */
    List<TableInfoResp> getAllTables(String tenantId);

    /**
     * 获取表的所有列信息（包含配置增强）
     *
     * @param tenantId 租户ID
     * @param tableName 表名
     * @return 列信息列表
     */
    List<ColumnInfoResp> getTableColumns(String tenantId, String tableName);

    /**
     * 获取数据库的完整 Schema 信息
     *
     * @param tenantId 租户ID
     * @return 完整的数据库 Schema 信息
     */
    DatabaseSchemaResp getDatabaseSchema(String tenantId);

    /**
     * 查询字段配置列表
     *
     * @param tenantId 租户ID
     * @param tableName 表名
     * @return 字段配置列表
     */
    List<SysFieldResp> querySysFields(String tenantId, String tableName);

    /**
     * 保存或更新字段配置
     *
     * @param tenantId  租户ID
     * @param tableName 表名
     * @param req       保存请求
     */
    void saveSysField(String tenantId, String tableName, SysFieldSaveReq req);
}
