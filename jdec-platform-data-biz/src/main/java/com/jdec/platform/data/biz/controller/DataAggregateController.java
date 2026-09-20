package com.jdec.platform.data.biz.controller;

import com.jdec.platform.data.api.dto.request.MenuQueryReq;
import com.jdec.platform.data.api.dto.response.MenuDataResp;
import com.jdec.platform.data.api.dto.response.MenuDetailResp;
import com.jdec.platform.data.biz.service.DataAggregateService;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 动态数据聚合控制器 (面向前端 UI 提供基于菜单的数据隔离与自相似复合数据服务) */
@Slf4j
@RestController
@RequestMapping("/api/data/aggregate")
@RequiredArgsConstructor
@Tag(name = "动态数据聚合接口", description = "提供面向前端 UI 的数据隔离与自相似复合数据聚合服务")
public class DataAggregateController {

    private final DataAggregateService dataAggregateService;

    @PostMapping("/menu/{menuId}/query")
    @Operation(
            summary = "基于菜单 ID 的动态数据列表查询",
            description =
                    "仅需传入 menuId，后端自动调用 UserMenuHeaderApi 解析根模块 ID 与用户勾选的 fieldId 列表，并组装返回复合数据集")
    public ApiResponse<MenuDataResp> queryByMenu(
            @PathVariable Long menuId, @RequestBody(required = false) MenuQueryReq req) {
        try {
            MenuDataResp result = dataAggregateService.queryByMenu(menuId, req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Data aggregate queryByMenu failed: menuId={}, err={}",
                    menuId,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @GetMapping("/menu/{menuId}/{id}")
    @Operation(
            summary = "基于菜单 ID 的单据详情及元数据聚合查询",
            description = "仅需传入 menuId 与单据 ID，后端自动推导绑定的根模块及所有子模块元数据与字段，组装返回 meta 字典与数据载荷")
    public ApiResponse<MenuDetailResp> getDetailByMenu(
            @PathVariable Long menuId, @PathVariable Long id) {
        try {
            MenuDetailResp result = dataAggregateService.getDetailByMenu(menuId, id);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Data aggregate getDetailByMenu failed: menuId={}, id={}, err={}",
                    menuId,
                    id,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }
}
