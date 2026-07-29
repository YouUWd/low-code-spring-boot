package com.jdec.platform.monitorlog.biz.controller;

import com.jdec.platform.monitorlog.api.dto.request.MonitorLogPageListReq;
import com.jdec.platform.monitorlog.api.dto.request.MonitorLogReq;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogPageListResp;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogResp;
import com.jdec.platform.monitorlog.biz.service.JdecMonitorLogService;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 监控日志 REST 控制器
 *
 * <p>提供监控日志的查询接口
 */
@RestController
@RequestMapping("/api/monitorlog")
@RequiredArgsConstructor
@Tag(name = "监控日志", description = "监控日志查询接口")
public class JdecMonitorLogController {

    private final JdecMonitorLogService jdecMonitorLogService;

    @PostMapping("/queryPage")
    @Operation(summary = "查询监控日志列表", description = "根据时间范围、日志类型、关键词查询监控日志")
    public ApiResponse<PageResult<MonitorLogPageListResp>> query(
            @Valid @RequestBody MonitorLogPageListReq request) {
        if (request.getPageNum() == null || request.getPageNum() < 1) {
            request.setPageNum(1L);
        }
        if (request.getPageSize() == null || request.getPageSize() < 1) {
            request.setPageSize(10L);
        }

        PageResult<MonitorLogPageListResp> records = jdecMonitorLogService.queryPageList(request);
        return ApiResponse.success(records);
    }

    @PostMapping("/addControlLog")
    @Operation(
            summary = "添加操作日志",
            description =
                    "记录操作日志"
                            + "     *形如：[系统设置-审批链配置]修改了配置"
                            + "      日志内容格式为：{"
                            + "            \"module\": \"模块名称/功能名称/页面名称\","
                            + "            \"orderId\": \"单据编号/订单编号/业务编号/任务编号\","
                            + "            \"hrefUrl\": \"单据详情页/订单详情页/业务详情页/任务详情页\","
                            + "            \"controlName\":\"操作名称/单据名称\""
                            + "        }"
                            + "     *带remark  如："
                            + "       [审批链分类] :PFD"
                            + "       [模块名称]:PFD->PFD-1"
                            + "     remark内容格式为：{"
                            + "       \"keyName\":\"keyValue\","
                            + "       \"changeList\":"
                            + "           {"
                            + "               \"age\":{\"oldVal\":22,\"newVal\":23,\"}"
                            + "               \"userName\":{\"oldVal\":\"李四\",\"newVal\":\"李四五\"}"
                            + "           }"
                            + "       }")
    public ApiResponse<Void> addControlLog(@Valid @RequestBody MonitorLogReq request) {
        if (request.getSubjectId() == null) {
            return ApiResponse.error("subjectId不能为空");
        }
        if (request.getLogContent() == null || request.getLogContent().isEmpty()) {
            return ApiResponse.error("logContent不能为空");
        }
        if (request.getRemark() == null || request.getRemark().isEmpty()) {
            return ApiResponse.error("remark不能为空");
        }
        jdecMonitorLogService.recordControlLog(request);
        return ApiResponse.success();
    }

    @PostMapping("/addBusinessChangeLog")
    @Operation(summary = "添加业务单据字段变化日志", description = "记录业务单据字段变化日志，logContent内容为单据字段变化HTML标记文本")
    public ApiResponse<Void> addBusinessChangeLog(@Valid @RequestBody MonitorLogReq request) {
        if (request.getLogContent() == null || request.getLogContent().isEmpty()) {
            return ApiResponse.error("logContent不能为空");
        }
        jdecMonitorLogService.recordBusinessLog(request);
        return ApiResponse.success();
    }

    @PostMapping("/addApprovalChainLog")
    @Operation(
            summary = "添加审批链操作日志",
            description =
                    "记录审批链日志"
                            + "     * 形如：[元件管理-FHSQ2409230955401 V2] 元件审核已驳回"
                            + "      日志内容格式为：{"
                            + "            \"module\": \"模块名称/功能名称/页面名称\","
                            + "            \"orderId\": \"单据编号/订单编号/业务编号/任务编号\","
                            + "            \"hrefUrl\": \"单据详情页/订单详情页/业务详情页/任务详情页\","
                            + "            \"orderVersion\": \"单据版本号/订单版本号/业务版本号/任务版本号\","
                            + "            \"controlName\":\"操作名称/单据名称\","
                            + "            \"operatorResult\": \"操作结果/执行结果/处理结果: 成功/失败\","
                            + "            \"fontColor\": \"字体颜色:默认为黑色\""
                            + "        }"
                            + "     *  带remark  如：驳回原因：审批不通过..")
    public ApiResponse<Void> addApprovalChainLog(@Valid @RequestBody MonitorLogReq request) {
        if (request.getLogContent() == null || request.getLogContent().isEmpty()) {
            return ApiResponse.error("logContent不能为空");
        }
        jdecMonitorLogService.recordApprovalChainLog(request);
        return ApiResponse.success();
    }

    @GetMapping("/addLoginLog")
    @Operation(
            summary = "添加登录日志",
            description =
                    "记录登录日志"
                            + "     * 形如：登录ipd系统 [ip：126.214.120.01]"
                            + "      日志内容格式为：{"
                            + "            \"userId\": \"用户ID\","
                            + "            \"systemName\": \"系统名称\","
                            + "            \"ip\": \"IP地址\","
                            + "        }")
    public ApiResponse<Void> addLoginLog(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestHeader("Login-User-Id") Long loginUserId,
            @RequestHeader("Login-Role-Id") Long loginRoleId,
            @RequestHeader("systemName") String systemName,
            @RequestHeader("ip") String ip) {
        systemName = systemName != null ? systemName.trim() : null;
        ip = ip != null ? ip.trim() : null;
        if (subjectId == null) {
            return ApiResponse.error("subjectId不能为空");
        }
        if (loginUserId == null || loginUserId == 0L) {
            return ApiResponse.error("loginUserId不能为空");
        }
        if (loginRoleId == null || loginRoleId == 0L) {
            return ApiResponse.error("loginRoleId不能为空");
        }
        if (systemName == null || systemName.isEmpty()) {
            return ApiResponse.error("登录系统的名称不能为空");
        }
        if (ip == null || ip.isEmpty()) {
            return ApiResponse.error("IP地址不能为空");
        }
        if (!ip.matches(
                "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$")) {
            return ApiResponse.error("IP地址格式不合法");
        }
        MonitorLogReq request = new MonitorLogReq();
        request.setSubjectId(subjectId);
        request.setLoginUserId(loginUserId);
        request.setLoginRoleId(loginRoleId);
        request.setIp(ip);
        request.setLogContent(String.format("登录%s系统[ip：%s]", systemName, ip));
        jdecMonitorLogService.recordLoginLog(request);
        return ApiResponse.success();
    }

    @GetMapping("/addLogoutLog")
    @Operation(
            summary = "添加退出日志",
            description =
                    "记录退出日志"
                            + "     * 形如：退出ipd系统 [ip：126.214.120.01]"
                            + "      日志内容格式为：{"
                            + "            \"userId\": \"用户ID\","
                            + "            \"systemName\": \"系统名称\","
                            + "            \"ip\": \"IP地址\","
                            + "        }")
    public ApiResponse<Void> addLogoutLog(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestHeader("Login-User-Id") Long loginUserId,
            @RequestHeader("Login-Role-Id") Long loginRoleId,
            @RequestHeader("systemName") String systemName,
            @RequestHeader("ip") String ip) {
        systemName = systemName != null ? systemName.trim() : null;
        ip = ip != null ? ip.trim() : null;
        if (subjectId == null) {
            return ApiResponse.error("subjectId不能为空");
        }
        if (loginUserId == null || loginUserId == 0L) {
            return ApiResponse.error("loginUserId不能为空");
        }
        if (loginRoleId == null || loginRoleId == 0L) {
            return ApiResponse.error("loginRoleId不能为空");
        }
        if (systemName == null || systemName.isEmpty()) {
            return ApiResponse.error("登录系统的名称不能为空");
        }
        if (ip == null || ip.isEmpty()) {
            return ApiResponse.error("IP地址不能为空");
        }
        if (!ip.matches(
                "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$")) {
            return ApiResponse.error("IP地址格式不合法");
        }
        MonitorLogReq request = new MonitorLogReq();
        request.setSubjectId(subjectId);
        request.setLoginUserId(loginUserId);
        request.setLoginRoleId(loginRoleId);
        request.setIp(ip);
        request.setLogContent(String.format("退出%s系统[ip：%s]", systemName, ip));
        jdecMonitorLogService.recordLogoutLog(request);
        return ApiResponse.success();
    }

    @PostMapping("/addNavLog")
    @Operation(
            summary = "添加导航日志",
            description =
                    "记录导航日志"
                            + "     * 形如： [元件管理-列表] 进入列表页面"
                            + "      日志内容格式为：{"
                            + "            \"moduleName\": \"模块名称/功能名称/页面名称\","
                            + "            \"controlName\": \"操作名称/单据名称\","
                            + "            \"hrefUrl\": \"单据详情页/订单详情页/业务详情页/任务详情页\","
                            + "            \"workDesc\":\"操作描述\","
                            + "        }")
    public ApiResponse<Void> addNavLog(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestHeader("Login-User-Id") Long loginUserId,
            @RequestHeader(value = "Login-Role-Id", required = false) Long loginRoleId,
            @RequestHeader(value = "Mock-Flag", required = false) Integer mockFlag,
            @RequestHeader(value = "Mock-User-Id", required = false) Long mockUserId,
            @RequestHeader(value = "Mock-Role-Id", required = false) Long mockRoleId,
            @RequestHeader("Log-Content") String logContent) {
        logContent = logContent != null ? logContent.trim() : null;
        if (subjectId == null) {
            return ApiResponse.error("subjectId不能为空");
        }
        if (loginUserId == null || loginUserId == 0L) {
            return ApiResponse.error("loginUserId不能为空");
        }
        if (logContent == null || logContent.isEmpty()) {
            return ApiResponse.error("日志内容不能为空");
        }
        MonitorLogReq request = new MonitorLogReq();
        request.setSubjectId(subjectId);
        request.setLoginUserId(loginUserId);
        request.setLoginRoleId(loginRoleId);
        request.setMockFlag(mockFlag != null ? mockFlag : 0);
        request.setMockUserId(mockUserId);
        request.setMockRoleId(mockRoleId);
        request.setLogContent(logContent);
        jdecMonitorLogService.recordNavLog(request);
        return ApiResponse.success();
    }

    @PostMapping("/addMockUser")
    @Operation(
            summary = "添加切换模拟用户日志",
            description = "添加切换模拟用户日志" + "     * 形如：切换身份：张三 (J10006821)  超级管理员")
    public ApiResponse<Void> addMockUser(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestHeader("Login-User-Id") Long loginUserId,
            @RequestHeader(value = "Login-Role-Id", required = false) Long loginRoleId,
            @RequestHeader(value = "Mock-Flag", required = false) Integer mockFlag,
            @RequestHeader(value = "Mock-User-Id", required = false) Long mockUserId,
            @RequestHeader(value = "Mock-Role-Id", required = false) Long mockRoleId) {
        if (subjectId == null) {
            return ApiResponse.error("subjectId不能为空");
        }
        if (loginUserId == null || loginUserId == 0L) {
            return ApiResponse.error("loginUserId不能为空");
        }
        if (mockUserId == null || mockUserId == 0L) {
            return ApiResponse.error("mockUserId不能为空");
        }
        if (mockRoleId == null || mockRoleId == 0L) {
            return ApiResponse.error("mockRoleId不能为空");
        }
        MonitorLogReq request = new MonitorLogReq();
        request.setSubjectId(subjectId);
        request.setLoginUserId(loginUserId);
        request.setLoginRoleId(loginRoleId);
        request.setMockFlag(mockFlag != null ? mockFlag : 0);
        request.setMockUserId(mockUserId);
        request.setMockRoleId(mockRoleId);
        jdecMonitorLogService.recordMockUser(request);
        return ApiResponse.success();
    }

    @PostMapping("/addChaneRole")
    @Operation(summary = "添加切换角色日志", description = "记录切换角色日志" + "     * 形如：切换角色：元件管理审核")
    public ApiResponse<Void> addChaneRole(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestHeader("Login-User-Id") Long loginUserId,
            @RequestHeader(value = "Login-Role-Id", required = false) Long loginRoleId) {
        if (subjectId == null) {
            return ApiResponse.error("subjectId不能为空");
        }
        if (loginUserId == null || loginUserId == 0L) {
            return ApiResponse.error("loginUserId不能为空");
        }
        MonitorLogReq request = new MonitorLogReq();
        request.setSubjectId(subjectId);
        request.setLoginUserId(loginUserId);
        request.setLoginRoleId(loginRoleId);
        jdecMonitorLogService.recordChaneRole(request);
        return ApiResponse.success();
    }

    @PostMapping("/addMockReset")
    @Operation(summary = "添加重置模拟用户日志", description = "添加重置模拟用户日志" + "形如：重置模拟用户：管理员")
    public ApiResponse<Void> addMockReset(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestHeader("Login-User-Id") Long loginUserId,
            @RequestHeader(value = "Login-Role-Id", required = false) Long loginRoleId) {
        if (subjectId == null) {
            return ApiResponse.error("subjectId不能为空");
        }
        if (loginUserId == null || loginUserId == 0L) {
            return ApiResponse.error("loginUserId不能为空");
        }
        MonitorLogReq request = new MonitorLogReq();
        request.setSubjectId(subjectId);
        request.setLoginUserId(loginUserId);
        request.setLoginRoleId(loginRoleId);
        jdecMonitorLogService.recordMockReset(request);
        return ApiResponse.success();
    }

    @PostMapping("/addMessageLog")
    @Operation(
            summary = "添加消息日志",
            description =
                    "添加消息日志"
                            + "日志内容为：发送消息的文本，如企业微信消息MD文本"
                            + "     *  带remark  如：[元件管理-FHSQ2409230955401 V2] 消息类型：元件驳回"
                            + "                                  消息接收人：张三J10006821  李四J10006822"
                            + "remark是Json格式的字符串，格式如下："
                            + "{"
                            + "            \"module\": \"模块名称/功能名称/页面名称\","
                            + "            \"orderId\": \"单据编号/订单编号/业务编号/任务编号\","
                            + "            \"hrefUrl\": \"单据详情页/订单详情页/业务详情页/任务详情页\","
                            + "            \"orderVersion\": \"单据版本号/订单版本号/业务版本号/任务版本号\","
                            + "            \"messageType\":\"操作类型/消息类型\","
                            + "            \"messageReceivers\":\"消息接收人[userid1,userid2,...]\""
                            + "        }")
    public ApiResponse<Void> addMessageLog(@Valid @RequestBody MonitorLogReq request) {
        jdecMonitorLogService.recordMessageLog(request);
        return ApiResponse.success();
    }

    @PostMapping("/addChangeSubject")
    @Operation(
            summary = "添加切换主体日志",
            description = "添加切换主体日志" + "     * 形如：切换主体：[主体名称]，用户：[用户ID]，角色：[角色ID]")
    public ApiResponse<Void> addChangeSubject(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestHeader("Subject-Name") String subjectName,
            @RequestHeader("Login-User-Id") Long loginUserId,
            @RequestHeader("Login-Role-Id") Long loginRoleId) {
        subjectName = subjectName != null ? subjectName.trim() : null;
        if (subjectId == null) {
            return ApiResponse.error("subjectId不能为空");
        }
        if (loginUserId == null || loginUserId == 0L) {
            return ApiResponse.error("loginUserId不能为空");
        }
        if (loginRoleId == null || loginRoleId == 0L) {
            return ApiResponse.error("loginRoleId不能为空");
        }
        if (subjectName == null || subjectName.isEmpty()) {
            return ApiResponse.error("主体名称不能为空");
        }
        jdecMonitorLogService.recordChangeSubject(loginUserId, loginRoleId, subjectName);
        return ApiResponse.success();
    }

    @GetMapping("/get")
    @Operation(summary = "获取监控日志详情", description = "根据ID获取监控日志详情")
    public ApiResponse<MonitorLogResp> get(@RequestParam Long id, @RequestParam String logType) {
        logType = logType != null ? logType.trim() : null;
        if (logType == null || logType.isEmpty()) {
            return ApiResponse.error("日志类型不能为空");
        }
        return ApiResponse.success(jdecMonitorLogService.get(id, logType));
    }
}
