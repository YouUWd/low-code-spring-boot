package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.response.TableInfoResp;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
}
