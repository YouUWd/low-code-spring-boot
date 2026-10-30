package com.jdec.platform.common.biz.controller;

import com.jdec.platform.config.api.SysConfigCategoryApi;
import com.jdec.platform.config.api.dto.response.SysConfigItemResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 通用配置项查询控制器（公共模块） */
@Slf4j
@RestController("commonConfigController")
@RequestMapping("/api/common/config")
@RequiredArgsConstructor
@Tag(name = "通用配置项接口", description = "按配置分类别名获取配置项列表（传配置项值时过滤单条，不传取全部）")
public class CommonConfigController {

    private final SysConfigCategoryApi sysConfigCategoryApi;

    @GetMapping("/item")
    @Operation(
            summary = "根据配置分类别名获取配置项列表",
            description =
                    "按 categoryAlias 查询当前项目 + 主体下的配置项，data 恒为列表：传 itemValue 时返回该分类下 value 匹配的配置项"
                            + "（最多一条，重复取第一条，不存在为空列表）；不传 itemValue 时返回该分类下全部配置项，"
                            + "按 sort、id 升序（包含启用与禁用项，status 原样返回）；均不报错")
    public ApiResponse<List<SysConfigItemResp>> getConfigItem(
            @Parameter(description = "配置分类英文标识", example = "employee_status") @RequestParam
                    String categoryAlias,
            @Parameter(description = "配置项存储值，不传则返回该分类下的全部配置项", example = "1")
                    @RequestParam(required = false)
                    String itemValue) {
        try {
            if (StringUtils.hasText(itemValue)) {
                SysConfigItemResp item =
                        sysConfigCategoryApi.getConfigItemsByValue(categoryAlias, itemValue);
                return ApiResponse.success(
                        item == null ? Collections.emptyList() : Collections.singletonList(item));
            }
            return ApiResponse.success(
                    sysConfigCategoryApi.getConfigItemsByCategoryAlias(categoryAlias));
        } catch (Exception ex) {
            log.error(
                    "Common config getConfigItem failed: categoryAlias={}, itemValue={}, err={}",
                    categoryAlias,
                    itemValue,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }
}
