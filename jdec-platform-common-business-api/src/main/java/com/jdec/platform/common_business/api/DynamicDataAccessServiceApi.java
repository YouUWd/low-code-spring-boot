package com.jdec.platform.common_business.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jdec.platform.common_business.api.dto.common.DataRightValue;
import com.jdec.platform.common_business.api.dto.common.ModuleFilterInfo;
import com.jdec.platform.common_business.api.dto.request.DataRightAccessReq;
import com.jdec.platform.common_business.api.dto.request.ListQueryReq;
import com.jdec.platform.common_business.api.dto.response.MenuListResp;
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
            ModuleFilterInfo moduleFilterInfo);

    /** 动态查询模块列表数据 * */
    Map<String, Object> dynamicQueryListData(
            Long subjectId,
            Long roleId,
            Long moduleId,
            Long id,
            Page page,
            ModuleFilterInfo moduleFilterInfo,
            List<Long> moduleSkip,
            boolean columnFilter,
            List<Object> primaryTableFilterIds);

    Map<Object, Object> dynamicQueryAllModuleDetailData(
            Long subject, Long roleId, Long menuId, Long id, Long relateSearchId);

    List<MenuListResp> findTargetAndAllChildrenIterate(List<MenuListResp> flatList, Long targetId);

    ApiResponse<Map<Object, Object>> getModuleApplyFieldListByMenuId(
            Long subjectId, Long roleId, Long menuId, String btn_type);

    List<DataRightValue> getDataRightData(Long subjectId, List<DataRightAccessReq> req);

    Map<String, Object> dynamicQueryListDataByMenuId(
            Long subjectId,
            Long roleId,
            Long menuId,
            Long id,
            Page pageObj,
            ModuleFilterInfo moduleFilterInfo);

    /** 动态查询模块列表数据 带搜索条件 * */
    Map<String, Object> dynamicQueryListDataWithSearchByMenuId(
            Long subjectId,
            Long roleId,
            ListQueryReq req,
            Page page,
            ModuleFilterInfo moduleFilterInfo);
}
