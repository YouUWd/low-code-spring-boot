package com.jdec.platform.common_business.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jdec.platform.common_business.api.dto.common.DataRightValue;
import com.jdec.platform.common_business.api.dto.common.ListQueryParam;
import com.jdec.platform.common_business.api.dto.common.ModuleFilterInfo;
import com.jdec.platform.common_business.api.dto.request.*;
import com.jdec.platform.shared.model.ApiResponse;
import java.util.List;
import java.util.Map;

/** 动态查询模块列表和详情数据 * */
public interface DynamicDataAccessServiceApi {
    /** 动态查询模块详情数据 * */
    Map<String, Object> dynamicQueryDetailData(
            Long subjectId,
            Long roleId,
            Long moduleId,
            Long id,
            Long relateSearchId,
            ModuleFilterInfo moduleFilterInfo,
            Boolean hasApproval,
            Map<String, Object> approvalInfo);

    /** 动态查询模块列表数据 * */
    /**
     * @param queryParam 查询参数
     * @param page 分页查询对象
     * @return
     */
    Map<String, Object> dynamicQueryListData(ListQueryParam queryParam, Page page);

    Map<Object, Object> dynamicQueryAllModuleDetailData(
            Long subject, Long roleId, Long menuId, Long id, Long relateSearchId);

    ApiResponse<Map<Object, Object>> getModuleApplyFieldListByMenuId(
            Long subjectId, Long roleId, Long menuId, String btn_type);

    List<DataRightValue> getDataRightData(Long subjectId, List<DataRightAccessReq> req);

    /** 动态查询模块列表数据 带搜索条件 * */
    Map<String, Object> dynamicQueryListDataWithSearchByMenuId(
            Long subjectId,
            Long roleId,
            ListQueryReq req,
            Page page,
            ModuleFilterInfo moduleFilterInfo);

    /** 动态查询模块列表数据 带搜索条件 * */
    Map<String, Object> dynamicQueryListSelectDataWithSearchByMenuId(
            Long subjectId, Long roleId, ListSelectQueryReq req, ModuleFilterInfo moduleFilterInfo);

    Map<Object, Object> dynamicQueryAllModuleDetailDataWithApproval(
            Long subjectId, Long roleId, Long menuId, Long id, Long relateSearchId, String btnType);

    Map<String, Object> dynamicQuerygetListNumWithApproval(
            Long subjectId, Long roleId, Long menuId);

    List<SearchItemReq> dynamicQueryFilterSelectedDataWithSearchByMenuId(
            Long subjectId, Long roleId, ListFilterSelectedDataReq queryReq);

    Object dynamicQuerygetApprovalLogs(Long subjectId, Long roleId, Long menuId, Long id);

    List<Map<String, Object>> dynamicQuerygetApprovalStep(
            Long subjectId, Long roleId, Long menuId, Long id);

    Long getApprovalToDoId(Long subjectId, Long roleId, Long menuId);
}
