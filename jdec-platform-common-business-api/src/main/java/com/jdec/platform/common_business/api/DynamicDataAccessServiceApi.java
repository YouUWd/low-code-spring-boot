package com.jdec.platform.common_business.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jdec.platform.common_business.api.dto.common.DataRightValue;
import com.jdec.platform.common_business.api.dto.common.ModuleFilterInfo;
import com.jdec.platform.common_business.api.dto.request.DataRightAccessReq;
import com.jdec.platform.common_business.api.dto.request.ListQueryReq;
import com.jdec.platform.common_business.api.dto.request.OrderItemReq;
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
    /**
     * @param subjectId 主体ID
     * @param roleId 角色ID
     * @param moduleId 模块ID
     * @param id 关联表筛选ID
     * @param page 分页对象
     * @param moduleFilterInfo 模块权限信息
     * @param moduleSkip 判断按钮需跳过的模块
     * @param columnFilter 是否列表筛选模式
     * @param primaryTableDataList 主表提前过滤数据集合
     * @param orderItems 排序信息
     * @param type 带审批链的查询，查询种类 单据类型（待办=1，已办=2，全部=3）
     * @return
     */
    Map<String, Object> dynamicQueryListData(
            Long subjectId,
            Long roleId,
            Long moduleId,
            Long id,
            Page page,
            ModuleFilterInfo moduleFilterInfo,
            List<Long> moduleSkip,
            boolean columnFilter,
            List<Object> primaryTableDataList,
            List<OrderItemReq> orderItems,
            Integer type);

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
