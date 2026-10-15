package com.jdec.platform.common.biz.service;

import com.jdec.platform.common.api.dto.request.MenuDetailQueryReq;
import com.jdec.platform.common.api.dto.request.MenuVersionListQueryReq;
import com.jdec.platform.common.api.dto.response.DetailDataResp;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.response.CreatedUserInfoResp;
import com.jdec.platform.dataengine.api.dto.response.VersionListResp;
import java.util.List;

/** 公共业务聚合服务接口 (详情查询) */
public interface CommonDataEngineService {

    DetailDataResp<NestedModuleDataDTO> getDetailByMenu(MenuDetailQueryReq menuDetailQueryReq);

    List<VersionListResp> getVersionList(MenuVersionListQueryReq menuDetailQueryReq);

    CreatedUserInfoResp getCreatedUserInfo(Long menuId, Long id, String businessNo);
}
