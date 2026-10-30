package com.jdec.platform.common.biz.controller;

import com.jdec.platform.config.api.SysConfigCategoryApi;
import com.jdec.platform.config.api.dto.response.SysConfigItemResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 通用配置项查询控制器（公共模块） */
@Slf4j
@RestController("commonConfigController")
@RequestMapping("/api/common/config")
@RequiredArgsConstructor
@Tag(name = "通用配置项接口", description = "按配置分类别名 + 配置项值获取配置项")
public class CommonConfigController {

    private final SysConfigCategoryApi sysConfigCategoryApi;

    @GetMapping("/item")
    @Operation(
            summary = "根据配置分类别名和配置项值获取配置项",
            description =
                    "按 categoryAlias + itemValue 查询当前项目 + 主体下的配置项（等同 sys_config_item 中该分类下 value 匹配的一条记录，重复时取第一条）；"
                            + "分类或配置项不存在时返回 data 为 null，不报错")
    public ApiResponse<SysConfigItemResp> getConfigItem(
            @Parameter(description = "配置分类英文标识", example = "employee_status") @RequestParam
                    String categoryAlias,
            @Parameter(description = "配置项存储值", example = "1") @RequestParam String itemValue) {
        try {
            return ApiResponse.success(
                    sysConfigCategoryApi.getConfigItemsByValue(categoryAlias, itemValue));
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
