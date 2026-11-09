package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.response.TableInfoResp;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 物理数据表及架构元数据查询接口（纯净底层元数据服务） */
public interface SysSchemaApi {

    /**
     * 获取指定项目数据源下的所有物理表基本信息（表名与表注释）
     *
     * @param projectNo 项目编码
     * @return 物理表列表
     */
    List<TableInfoResp> getAllTables(String projectNo);

    /**
     * 批量查询指定项目数据源下若干物理表的注释映射
     *
     * @param projectNo 项目编码
     * @param tableNames 待查询的物理表名集合
     * @return 键为表名(全小写)，值为表注释(若无则为空或等于表名)的映射
     */
    Map<String, String> getTableComments(String projectNo, Collection<String> tableNames);

    /**
     * 获取指定项目数据源下物理表的实际列名集合 (从 INFORMATION_SCHEMA.COLUMNS 查询)
     *
     * @param projectNo 项目编码
     * @param tableName 物理表名
     * @return 实际物理列名集合 (全小写)
     */
    Set<String> getTableColumnNames(String projectNo, String tableName);

    /**
     * 获取指定项目数据源下物理表类型为 JSON 的列名集合 (从 INFORMATION_SCHEMA.COLUMNS 查询)
     *
     * @param projectNo 项目编码
     * @param tableName 物理表名
     * @return 实际物理 JSON 列名集合 (全小写)
     */
    Set<String> getTableJsonColumnNames(String projectNo, String tableName);
}
