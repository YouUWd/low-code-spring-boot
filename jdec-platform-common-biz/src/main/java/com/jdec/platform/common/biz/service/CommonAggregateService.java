package com.jdec.platform.common.biz.service;

import com.jdec.platform.common.api.dto.request.MenuActiveToggleReq;
import com.jdec.platform.common.api.dto.request.MenuChangeQueryReq;
import com.jdec.platform.common.api.dto.request.MenuListReq;
import com.jdec.platform.common.api.dto.request.MenuOptionReq;
import com.jdec.platform.common.api.dto.request.MenuQueryReq;
import com.jdec.platform.common.api.dto.response.MenuApprovalCountResp;
import com.jdec.platform.common.api.dto.response.MenuChangeCountResp;
import com.jdec.platform.common.api.dto.response.MenuDataResp;
import com.jdec.platform.common.api.dto.response.MenuDetailResp;
import com.jdec.platform.common.api.dto.response.ModuleDataNode;
import com.jdec.platform.common.api.dto.response.StatusSimpleResp;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
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
     * 基于原单菜单 ID 的变更申请单列表动态查询 (${primaryTable}_change)
     *
     * @param menuId 原单菜单 ID
     * @param req 面向 UI 的变更单查询过滤、排序与分页请求体 (可选，支持 readStatus 0-未读, 1-已读, 2-全部)
     * @return 包含原单元数据与变更合成结果的数据分页复合响应
     */
    MenuDataResp queryChangeListByMenu(Long menuId, MenuChangeQueryReq req);

    /**
     * 动态数据列表全量查询 (不分页，支持自定义物理投影字段与平铺过滤，获取最新启用 business_status=99 的最大版本)
     *
     * <p>根据传入物理表 specs 中携带的 menuId 解析对应的 moduleId，在项目的全量模块树中定位公共根模块。
     *
     * @param req 面向物理表名与列名的查询过滤与排序请求体 (可选)
     * @return 根模块自相似数据节点 (ModuleDataNode)
     */
    ModuleDataNode queryList(MenuListReq req);

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
    List<DataOptionResp> getOptionsByMenu(Long menuId, MenuOptionReq req);

    /**
     * 基于菜单 ID 一次性统计待办、已办和全部的记录数量
     *
     * @param menuId 菜单 ID
     * @return 审批统计响应列表: 1(待办)、2(已办)、3(全部)
     */
    List<MenuApprovalCountResp> getApprovalCountByMenu(Long menuId);

    /**
     * 基于菜单 ID 查询是否启用变更申请及未读变更申请单数量
     *
     * @param menuId 菜单 ID
     * @return 变更启用状态 (enableChange) 与未读变更申请单数量 (unreadCount)
     */
    MenuChangeCountResp getChangeCountByMenu(Long menuId);

    /**
     * 基于菜单 ID 将指定变更申请单设置为已读 (read_status = 1)
     *
     * <p>仅当当前用户为原单据创建人时更新单据级 read_status；非原单据创建人查看时直接返回 true，不更新已读状态。
     *
     * @param menuId 菜单 ID
     * @param changeId 变更单 ID
     * @return 是否处理成功
     */
    Boolean markChangeAsRead(Long menuId, Long changeId);

    /**
     * 基于菜单 ID 批量更新单据启停业务状态
     *
     * <p>根据 business_no 批量更新：启用是将 11 改为 99，停用是将 99 改为 11，不更改其他状态的数据。
     *
     * @param menuId 菜单 ID
     * @param req 批量启停请求体
     * @return 目标状态增强后的简要状态对象 (包含 title, statusValue, statusBackground, statusFontColor)
     */
    StatusSimpleResp toggleActiveStatusByMenu(Long menuId, MenuActiveToggleReq req);
}
