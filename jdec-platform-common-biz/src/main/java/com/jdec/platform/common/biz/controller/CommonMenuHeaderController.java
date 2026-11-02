package com.jdec.platform.common.biz.controller;

import com.jdec.platform.common.api.UserMenuHeaderApi;
import com.jdec.platform.common.api.dto.request.SaveUserMenuHeaderReq;
import com.jdec.platform.common.api.dto.request.SaveUserMenuHeaderWidthReq;
import com.jdec.platform.config.api.SysBusinessTypeApi;
import com.jdec.platform.config.api.dto.response.SearchConfigItemResp;
import com.jdec.platform.config.api.dto.response.SysMenuHeaderResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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

    private final SysBusinessTypeApi sysBusinessTypeApi;

    @Operation(
            summary = "根据业务类型查询搜索配置项",
            description = "返回 sys_business_type.search_type 中配置的搜索类型集合；业务类型为空或不存在时返回空集合")
    @GetMapping("/search-config")
    public ApiResponse<List<SearchConfigItemResp>> listSearchConfigByBusinessType(
            @Parameter(description = "业务类型标识", example = "descriptionText")
                    @RequestParam(required = false)
                    String businessTypeCode) {
        log.info("查询业务类型 {} 的搜索配置项", businessTypeCode);
        return ApiResponse.success(
                sysBusinessTypeApi.listSearchConfigByBusinessType(businessTypeCode));
    }

    @GetMapping
    @Operation(
            summary = "用户个性化表头配置查询",
            description =
                    "按用户 + 列表菜单返回表头配置树：menuId 为列表菜单ID，菜单类型为「页面」且关联详情菜单时内部转换为详情菜单ID，"
                            + "「表」等其它类型直接以该菜单ID 查询用户配置；"
                            + "以菜单/字段 + 系统表头配置生成的默认表头为骨架，按 virId 叠加该用户在 user_menu_header 中的个性化配置"
                            + "（两边都有取用户记录，用户没有的取默认值；模块侧已不存在的菜单/字段、以及用户表多出的记录一律丢弃）；"
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
                            + "库中已保存、但本次提交未包含的节点一并删除（整棵树以本次提交为准）；"
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

    @PostMapping("/width")
    @Operation(
            summary = "保存列表列宽（拖动列宽）",
            description =
                    "单列定位：按 nodeType + sourceId 拼虚id（菜单 MENU_菜单ID / 字段 Field_字段ID，与表头节点返回的 sourceId 同源），"
                            + "dataIndex 仅在虚id 未命中时做兜底比对；用户与角色取当前登录上下文，无需前端传递。"
                            + "该用户在此菜单下的 user_menu_header 没有任何记录时，先按表头查询接口同一口径生成用户表头树，"
                            + "校验目标列为字段且 checked=1 后整棵树落库（首次用户个性化），同时将宽度写到该列记录；"
                            + "已有记录且命中该列记录时，以库中记录为准校验（仅 nodeType 为字段且 checked=1 可设宽），只更新该记录的 width；"
                            + "已有记录但该列无记录（模块侧新增、或系统表头后来才勾选）时，按合并后的表头树取值校验，仅补插该列一条记录并写入宽度；"
                            + "落库归属菜单与数据隔离口径同保存/查询接口（menu_id 取表头归属菜单ID，按 userId + menuId + subjectId + projectNo）")
    public ApiResponse<Void> saveUserMenuHeaderWidth(@RequestBody SaveUserMenuHeaderWidthReq req) {
        try {
            userMenuHeaderApi.saveUserMenuHeaderWidth(req);
            return ApiResponse.success();
        } catch (Exception ex) {
            log.error(
                    "Common menu-header saveUserMenuHeaderWidth failed: menuId={}, nodeType={}, sourceId={}, err={}",
                    req == null ? null : req.getMenuId(),
                    req == null ? null : req.getNodeType(),
                    req == null ? null : req.getSourceId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/reset")
    @Operation(
            summary = "恢复默认表头",
            description =
                    "删除该用户在指定列表菜单下的个性化表头配置（user_menu_header 中 userId + 表头归属菜单ID + 当前主体 + 当前项目编码 的全部节点），"
                            + "删除后查询/展示接口自然回退到系统默认表头配置；表头归属菜单与保存/查询同一套转换规则，"
                            + "主体与项目编码取当前请求上下文，无需前端传递")
    public ApiResponse<Void> resetUserMenuHeader(
            @RequestParam Long userId, @RequestParam Long menuId) {
        try {
            userMenuHeaderApi.resetUserMenuHeader(userId, menuId);
            return ApiResponse.success();
        } catch (Exception ex) {
            log.error(
                    "Common menu-header resetUserMenuHeader failed: userId={}, menuId={}, err={}",
                    userId,
                    menuId,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }
}
