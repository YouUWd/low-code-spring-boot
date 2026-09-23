package com.jdec.platform.common.biz.service;

import java.util.Map;

/** 审批与流程公共聚合服务接口 */
public interface CommonApprovalService {

    /**
     * 基于菜单 ID 一次性统计待办、已办和全部的全量记录总数
     *
     * @param menuId 菜单 ID
     * @return 映射字典：1 -> 待办数量(todo), 2 -> 已办数量(done), 3 -> 全部数量(total)
     */
    Map<Integer, Long> getApprovalCountByMenu(Long menuId);
}
