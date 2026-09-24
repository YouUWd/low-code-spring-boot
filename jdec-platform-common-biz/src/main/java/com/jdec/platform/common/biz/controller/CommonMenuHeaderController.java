package com.jdec.platform.common.biz.controller;

import com.jdec.platform.common.api.UserMenuHeaderApi;
import com.jdec.platform.common.api.dto.request.SaveUserMenuHeaderReq;
import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
import com.jdec.platform.config.api.dto.response.SysMenuHeaderResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 用户个性化表头控制器（公共模块） */
@Slf4j
@RestController("commonMenuHeaderController")
@RequestMapping("/api/common/menu-header")
@RequiredArgsConstructor
@Tag(name = "用户个性化表头接口", description = "按用户 + 列表菜单维护个性化表头配置树与多级列头展示")
public class CommonMenuHeaderController {

    private final UserMenuHeaderApi userMenuHeaderApi;

    @GetMapping
    @Operation(
            summary = "用户个性化表头配置查询",
            description =
                    "按用户 + 列表菜单返回表头配置树：menuId 为列表菜单ID，菜单类型为「页面」且关联详情菜单时内部转换为详情菜单ID，"
                            + "「表」等其它类型直接以该菜单ID 查询用户配置；"
                            + "以菜单/字段 + 系统表头配置生成的默认表头为骨架，按 virId 叠加该用户在 user_menu_header 中的个性化配置"
                            + "（两边都有取用户记录，用户没有的取默认值，用户表多出的记录丢弃）；"
                            + "主键id 仅 user_menu_header 中的记录保留，来自默认表头/sys_menu_header 的节点不带 id；"
                            + "roleId 非空时按角色权限过滤菜单与字段（字段仅保留 view=1）")
    public ApiResponse<SysMenuHeaderResp> getUserMenuHeader(
            @RequestParam Long userId,
            @RequestParam Long menuId,
            @RequestParam(required = false) Long roleId) {
        try {
            SysMenuHeaderResp result =
                    userMenuHeaderApi.getUserMenuHeaderTree(userId, menuId, roleId);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Common menu-header getUserMenuHeader failed: userId={}, menuId={}, roleId={}, err={}",
                    userId,
                    menuId,
                    roleId,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/save")
    @Operation(
            summary = "用户个性化表头配置保存",
            description =
                    "整棵用户个性化表头节点树提交（节点结构与菜单表头节点一致，额外带 userId）：树按深度优先压平后逐节点"
                            + "判断，带主键 id（或库中已存在同 virId 记录）的节点按主键更新，其余新增；"
                            + "落库归属菜单为入参 menuId 对应的表头归属菜单（页面类型取其关联的详情菜单ID，表等其它类型取 menuId 自身），"
                            + "数据隔离按 userId + menuId + subjectId + projectNo")
    public ApiResponse<Void> saveUserMenuHeader(@RequestBody SaveUserMenuHeaderReq req) {
        try {
            userMenuHeaderApi.saveUserMenuHeader(req);
            return ApiResponse.success();
        } catch (Exception ex) {
            log.error(
                    "Common menu-header saveUserMenuHeader failed: userId={}, menuId={}, err={}",
                    req == null ? null : req.getUserId(),
                    req == null ? null : req.getMenuId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @GetMapping("/display")
    @Operation(
            summary = "用户个性化表头展示",
            description =
                    "按用户 + 列表菜单纯读取 user_menu_header 已保存的个性化配置（菜单类型为「页面」且关联详情菜单时按详情菜单ID 查询，否则按 menuId 本身），"
                            + "按多级列头展示格式返回：只输出已勾选（checked=1）的列，"
                            + "其祖先菜单节点一并保留用于组装多级列头；无已勾选配置时 header 为空")
    public ApiResponse<MenuHeaderDisplayResp> getUserMenuHeaderDisplay(
            @RequestParam Long userId,
            @RequestParam Long menuId,
            @RequestParam(required = false) Long roleId) {
        try {
            MenuHeaderDisplayResp result =
                    userMenuHeaderApi.getUserMenuHeaderDisplay(userId, menuId, roleId);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Common menu-header getUserMenuHeaderDisplay failed: userId={}, menuId={}, roleId={}, err={}",
                    userId,
                    menuId,
                    roleId,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }
}
