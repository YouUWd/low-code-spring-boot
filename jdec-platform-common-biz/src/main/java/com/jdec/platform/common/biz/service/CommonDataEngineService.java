package com.jdec.platform.common.biz.service;

import com.jdec.platform.common.biz.dto.request.MenuDetailQueryReq;
import com.jdec.platform.common.biz.dto.response.DetailDataResp;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;

/** 公共业务聚合服务接口 (详情查询) */
public interface CommonDataEngineService {

    DetailDataResp<NestedModuleDataDTO> getDetailByMenu(MenuDetailQueryReq menuDetailQueryReq);
}
