package com.jdec.platform.common.biz.controller;

import com.jdec.platform.common.api.MonitorLogApi;
import com.jdec.platform.common.api.dto.request.MonitorLogQueryReq;
import com.jdec.platform.common.api.dto.request.MonitorLogSaveReq;
import com.jdec.platform.common.api.dto.response.MonitorLogResp;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 监控日志控制器（公共模块） */
@Slf4j
@RestController("commonMonitorLogController")
@RequestMapping("/api/common/monitor-log")
@RequiredArgsConstructor
@Tag(name = "监控日志接口", description = "操作日志/消息日志/系统日志的保存与查询")
public class CommonMonitorLogController {

    private final MonitorLogApi monitorLogApi;

    @PostMapping("/save")
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
                    "Common monitor-log saveMonitorLog failed: type={}, err={}",
                    req == null ? null : req.getType(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @PostMapping("/page")
    @Operation(
            summary = "监控日志分页查询",
            description =
                    "支持记录时间范围（record_time）、日志类型（type）与关键词模糊（同时作用于 content 与 remark）筛选，"
                            + "按记录时间倒序返回；approve、content 等字段已由 JSON 字符串反序列化为对象")
    public ApiResponse<PageResult<MonitorLogResp>> queryMonitorLogPage(
            @RequestBody MonitorLogQueryReq req) {
        try {
            PageResult<MonitorLogResp> result = monitorLogApi.queryLogPage(req);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error("Common monitor-log queryMonitorLogPage failed: err={}", ex.getMessage(), ex);
            throw ex;
        }
    }

    @GetMapping("/operate")
    @Operation(
            summary = "单据操作日志查询",
            description =
                    "按菜单id + 单据id查询该单据的操作日志：先由菜单id反查模块id，再查操作日志索引表 monitor_operate_index "
                            + "取出日志id，据此回 monitor_log 查明细汇总返回，按记录时间倒序；"
                            + "并按当前登录角色的字段权限过滤每条日志 remark 中的字段明细；索引无记录时返回空列表")
    public ApiResponse<List<MonitorLogResp>> queryOperateLog(
            @RequestParam Long menuId, @RequestParam Long dataId) {
        try {
            List<MonitorLogResp> result = monitorLogApi.queryOperateLog(menuId, dataId);
            return ApiResponse.success(result);
        } catch (Exception ex) {
            log.error(
                    "Common monitor-log queryOperateLog failed: menuId={}, dataId={}, err={}",
                    menuId,
                    dataId,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }
}
