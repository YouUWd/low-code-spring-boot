package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.response.SearchConfigItemResp;
import java.util.List;

/** 下拉搜索配置 API 接口 */
public interface SysSearchConfigApi {

    /**
     * 按字段索引查询可用的搜索配置
     *
     * <p>字段索引形如「模块ID.表名.字段名」，如 1001.info.company_name。后端拆出表名与字段名后，从当前项目数据源的 information_schema
     * 查询该字段的 MySQL 数据类型，再转小写与 search_config.json 中的 dataType 匹配。
     *
     * @param dataIndex 字段索引（模块ID.表名.字段名），如 1001.info.company_name
     * @return 匹配到的搜索配置列表，字段不存在或未匹配到类型时返回空列表
     */
    List<SearchConfigItemResp> listSearchConfig(String dataIndex);
}
