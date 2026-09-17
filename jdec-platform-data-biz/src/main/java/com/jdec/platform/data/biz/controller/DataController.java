package com.jdec.platform.data.biz.controller;

import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
import com.jdec.platform.config.api.dto.response.SysMenuHeaderResp;
import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.MonitorLogApi;
import com.jdec.platform.data.api.UserMenuHeaderApi;
import com.jdec.platform.data.api.dto.request.MonitorLogQueryReq;
import com.jdec.platform.data.api.dto.request.MonitorLogSaveReq;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.request.SaveReq;
import com.jdec.platform.data.api.dto.request.SaveUserMenuHeaderReq;
import com.jdec.platform.data.api.dto.response.DataDetail;
import com.jdec.platform.data.api.dto.response.DataOption;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.MonitorLogResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 通用低代码数据引擎控制器 提供跨业务模块的通用动态读写能力 */
@Slf4j
@RestController
@RequestMapping("/api/data/engine")
@RequiredArgsConstructor
@Tag(name = "通用低代码数据引擎接口", description = "提供通用的动态查询、详情加载与自相似树形同构保存")
public class DataController {

    private final DataApi dataApi;
    private final UserMenuHeaderApi userMenuHeaderApi;
    private final MonitorLogApi monitorLogApi;

    @PostMapping("/query")
    @Operation(summary = "通用动态分页查询", description = "支持任意自相似模块树的分页、排序与组合过滤")
    public ApiResponse<DataPage<Map<String, Object>>> query(@RequestBody QueryReq req) {
        try {
            DataPage<Map<String, Object>> result = dataApi.query(req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Data engine query failed: moduleId={}, err={}",
                    req.getModuleId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/options")
    @Operation(summary = "通用字段搜索下拉候选项", description = "获取当前模块内指定表与字段的去重候选值列表，返回 label-value 结构")
    public ApiResponse<List<DataOption>> getOptions(@RequestBody OptionReq req) {
        try {
            List<DataOption> result = dataApi.getOptions(req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Data engine getOptions failed: moduleId={}, fieldId={}, err={}",
                    req.getModuleId(),
                    req.getFieldId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @GetMapping("/{moduleId}/{id}")
    @Operation(summary = "通用单条详情查询", description = "精准根据模块 ID 与单据 ID 加载单条业务对象详情，支持通过 fields 指定投影字段")
    public ApiResponse<DataDetail<Map<String, Object>>> getDetail(
            @PathVariable Long moduleId,
            @PathVariable Long id,
            @RequestParam(required = false) List<Long> fields) {
        try {
            DataDetail<Map<String, Object>> result = dataApi.getDetail(moduleId, id, fields);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Data engine getDetail failed: moduleId={}, id={}, fields={}, err={}",
                    moduleId,
                    id,
                    fields,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/save")
    @Operation(
            summary = "通用同构原子保存",
            description = "与 detail 响应的 data 结构 100% 读写同构，直接提交 detail.data 载荷，返回各模块主键映射")
    public ApiResponse<Map<String, Object>> save(@RequestBody Map<String, Object> data) {
        try {
            SaveReq req = SaveReq.builder().records(List.of(data)).build();
            Map<String, Object> ids = dataApi.saveAndCollectIds(req);
            return ApiResponse.success(ids);
        } catch (Exception ex) {
            log.error("Data engine save failed: data={}, err={}", data, ex.getMessage(), ex);
            throw ex;
        }
    }

    @GetMapping("/menu-header")
    @Operation(
            summary = "用户个性化表头配置查询",
            description =
                    "按用户 + 列表菜单返回表头配置树：menuId 为列表菜单ID，内部转换为详情菜单ID 后查询用户配置；"
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
                    "Data engine getUserMenuHeader failed: userId={}, menuId={}, roleId={}, err={}",
                    userId,
                    menuId,
                    roleId,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/menu-header/save")
    @Operation(
            summary = "用户个性化表头配置保存",
            description =
                    "整棵用户个性化表头节点树提交（节点结构与菜单表头节点一致，额外带 userId）：树按深度优先压平后逐节点"
                            + "判断，带主键 id（或库中已存在同 virId 记录）的节点按主键更新，其余新增；"
                            + "落库归属菜单为入参 menuId 对应的详情菜单ID，数据隔离按 userId + menuId + subjectId + projectNo")
    public ApiResponse<Void> saveUserMenuHeader(@RequestBody SaveUserMenuHeaderReq req) {
        try {
            userMenuHeaderApi.saveUserMenuHeader(req);
            return ApiResponse.success();
        } catch (Exception ex) {
            log.error(
                    "Data engine saveUserMenuHeader failed: userId={}, menuId={}, err={}",
                    req == null ? null : req.getUserId(),
                    req == null ? null : req.getMenuId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @GetMapping("/menu-header/display")
    @Operation(
            summary = "用户个性化表头展示",
            description =
                    "按用户 + 列表菜单纯读取 user_menu_header 已保存的个性化配置，按多级列头展示格式返回：只输出已勾选（checked=1）的列，"
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
                    "Data engine getUserMenuHeaderDisplay failed: userId={}, menuId={}, roleId={}, err={}",
                    userId,
                    menuId,
                    roleId,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/monitor-log/save")
    @Operation(
            summary = "监控日志保存",
            description =
                    "按日志类型（0-操作日志、1-消息日志、2-系统日志）分发到对应保存处理器；approve、content 等字段"
                            + "由调用方以对象提交，落库前序列化为 JSON 字符串；数据源按 X-Project-No 请求头动态切换")
    public ApiResponse<Void> saveMonitorLog(@RequestBody MonitorLogSaveReq req) {
        try {
            monitorLogApi.saveLog(req);
            return ApiResponse.success();
        } catch (Exception ex) {
            log.error(
                    "Data engine saveMonitorLog failed: type={}, err={}",
                    req == null ? null : req.getType(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/monitor-log/page")
    @Operation(
            summary = "监控日志分页查询",
            description =
                    "支持记录时间范围（record_time）、日志类型（type）与关键词模糊（同时作用于 content 与 remark）筛选，"
                            + "按记录时间倒序返回；approve、content 等字段已由 JSON 字符串反序列化为对象")
    public ApiResponse<DataPage<MonitorLogResp>> queryMonitorLogPage(
            @RequestBody MonitorLogQueryReq req) {
        try {
            DataPage<MonitorLogResp> result = monitorLogApi.queryLogPage(req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error("Data engine queryMonitorLogPage failed: err={}", ex.getMessage(), ex);
            throw ex;
        }
    }
}
