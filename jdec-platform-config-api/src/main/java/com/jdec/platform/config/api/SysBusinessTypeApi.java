package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.response.BusinessTypeOptionResp;
import com.jdec.platform.config.api.dto.response.SearchConfigItemResp;
import java.util.List;

/** 业务类型 API 接口 */
public interface SysBusinessTypeApi {

    /**
     * 查询业务类型下拉选项
     *
     * @return 业务类型下拉列表（按ID升序）
     */
    List<BusinessTypeOptionResp> listBusinessTypeOptions();

    /**
     * 根据业务类型查询搜索配置项
     *
     * @param businessTypeCode 业务类型标识，如 descriptionText
     * @return 该业务类型支持的搜索配置项集合
     */
    List<SearchConfigItemResp> listSearchConfigByBusinessType(String businessTypeCode);
}
