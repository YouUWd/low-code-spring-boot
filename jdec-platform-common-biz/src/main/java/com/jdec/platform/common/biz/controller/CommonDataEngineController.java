package com.jdec.platform.common.biz.controller;

import com.jdec.platform.common.biz.dto.request.MenuDetailQueryReq;
import com.jdec.platform.common.biz.dto.request.MenuVersionListQueryReq;
import com.jdec.platform.common.biz.dto.response.DetailDataResp;
import com.jdec.platform.common.biz.service.CommonDataEngineService;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.response.VersionListResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公共业务数据聚合控制器 (基于菜单的数据隔离、动态列表与详情聚合) */
@Slf4j
@Tag(name = "公共业务数据查询接口", description = "提供基于菜单维度单据详情查询")
@RestController("commonDataEngineController")
@RequestMapping("/api/common/data-engine")
@RequiredArgsConstructor
public class CommonDataEngineController {

    private final CommonDataEngineService commonDataEngineService;

    @PostMapping("/menu/detail")
    @Operation(
            summary = "基于菜单 ID 的单据详情查询",
            description = "仅需传入 menuId 和单据 id，内部自动解析根模块 ID 及勾选字段，返回单据元数据定义与同构详情树")
    public ApiResponse<DetailDataResp<NestedModuleDataDTO>> getDetailByMenu(
            @RequestBody MenuDetailQueryReq menuDetailQueryReq) {
        try {
            DetailDataResp<NestedModuleDataDTO> result =
                    commonDataEngineService.getDetailByMenu(menuDetailQueryReq);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Common DataEngine getDetailByMenu failed: menuId={}, id={}, err={}",
                    menuDetailQueryReq.getMenuIds(),
                    menuDetailQueryReq.getId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/menu/get-version-list")
    @Operation(
            summary = "基于菜单 ID 的单据版本列表查询",
            description = "传入 menuId 和单据 id，返回该条数据的版本列表，模块无审批链时返回空。")
    public ApiResponse<List<VersionListResp>> getVersionListByMenu(
            @RequestBody MenuVersionListQueryReq menuDetailQueryReq) {
        try {
            List<VersionListResp> result =
                    commonDataEngineService.getVersionList(menuDetailQueryReq);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Common DataEngine getVersionListByMenu failed: menuId={}, id={}, err={}",
                    menuDetailQueryReq.getMenuId(),
                    menuDetailQueryReq.getId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }
}
