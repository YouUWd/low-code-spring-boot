package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysMenuHeaderNodeApi;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 菜单表头节点 REST 控制器
 *
 * <p>提供菜单表头节点的管理接口。
 */
@RestController
@RequestMapping("/api/config/menu-header-node")
@RequiredArgsConstructor
@Tag(name = "菜单表头节点", description = "菜单表头节点管理接口")
public class SysMenuHeaderNodeController {

    private final SysMenuHeaderNodeApi sysMenuHeaderNodeApi;
}
