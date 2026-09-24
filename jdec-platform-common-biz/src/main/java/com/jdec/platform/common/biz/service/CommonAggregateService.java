package com.jdec.platform.common.biz.service;

import com.jdec.platform.common.biz.dto.request.MenuOptionReq;
import com.jdec.platform.common.biz.dto.request.MenuQueryReq;
import com.jdec.platform.common.biz.dto.response.MenuDataResp;
import com.jdec.platform.common.biz.dto.response.MenuDetailResp;
import com.jdec.platform.data.api.dto.response.DataOption;
import java.util.List;

/** 公共业务聚合服务接口 (基于菜单的数据隔离、动态列表与详情聚合) */
public interface CommonAggregateService {

    /**
     * 基于菜单 ID 的动态数据列表查询 (自动通过 UserMenuHeaderApi 解析勾选的 fieldId 列表与 rootModuleId)
     *
     * @param menuId 菜单 ID
     * @param req 面向 UI 的查询过滤、排序与分页请求体 (可选)
     * @return 包含顶层多级列头、权限汇总、审批链数据与自相似数据树的复合响应
     */
    MenuDataResp queryByMenu(Long menuId, MenuQueryReq req);

    /**
     * 基于菜单 ID 的单据详情及元数据聚合查询 (仅需 menuId 和 id)
     *
     * @param menuId 菜单 ID
     * @param id 单据 ID
     * @return 包含 List<ModuleMetaDTO> meta 与 ModuleDataNode data 的复合响应
     */
    MenuDetailResp getDetailByMenu(Long menuId, Long id);

    /**
     * 基于菜单 ID 的字段搜索下拉候选项查询 (自动解析根模块上下文，支持单层 AND 组合过滤条件)
     *
     * @param menuId 菜单 ID
     * @param req 包含目标 fieldId 和平铺单层 filters 的请求体
     * @return 下拉候选项列表 (label-value 结构)
     */
    List<DataOption> getOptionsByMenu(Long menuId, MenuOptionReq req);
}
