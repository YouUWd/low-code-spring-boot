package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SystemInitApi;
import com.jdec.platform.config.api.dto.response.SystemInitResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 系统初始化 REST 控制器 */
@RestController
@RequestMapping("/api/config/init")
@RequiredArgsConstructor
@Tag(name = "系统初始化", description = "读取初始化配置 JSON，逐个项目主体初始化模块/菜单/通用配置/特殊权限并创建超级管理员")
public class SystemInitController {

    private final SystemInitApi systemInitApi;

    @PostMapping("/system")
    @Operation(
            summary = "执行系统初始化",
            description =
                    "以 init/project_subject.json 的项目主体清单为驱动，逐个主体初始化模块、菜单、通用配置、特殊权限，"
                            + "并创建超级管理员角色、绑定全部菜单、模块字段权限与特殊权限。已存在的数据按步骤跳过，可重复调用。")
    public ApiResponse<SystemInitResp> initSystem() {
        return ApiResponse.success(systemInitApi.initSystem());
    }
}
