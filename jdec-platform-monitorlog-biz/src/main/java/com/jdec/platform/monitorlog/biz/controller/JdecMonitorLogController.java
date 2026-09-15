package com.jdec.platform.monitorlog.biz.controller;

import com.jdec.platform.config.api.SysProjectSubjectApi;
import com.jdec.platform.config.api.dto.response.SysProjectSubjectResp;
import com.jdec.platform.monitorlog.api.dto.request.*;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogPageListResp;
import com.jdec.platform.monitorlog.biz.service.JdecMonitorLogService;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
import com.jdec.platform.shared.utils.RequestContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/monitorlog")
@RequiredArgsConstructor
@Tag(name = "监控日志", description = "监控日志查询接口")
public class JdecMonitorLogController {

    private final JdecMonitorLogService jdecMonitorLogService;

    private final SysProjectSubjectApi sysProjectSubjectApi;

    @PostMapping("/queryPage")
    @Operation(summary = "查询监控日志列表", description = "根据时间范围、日志类型、关键词查询监控日志")
    public ApiResponse<PageResult<MonitorLogPageListResp>> query(
            @Valid @RequestBody MonitorLogPageListReq request,
            @Parameter(description = "项目编号")
                    @RequestHeader(value = "X-Project-No", required = false)
                    String projectNo) {
        if (request.getPageNum() == null || request.getPageNum() < 1) {
            request.setPageNum(1L);
        }
        if (request.getPageSize() == null || request.getPageSize() < 1) {
            request.setPageSize(10L);
        }

        PageResult<MonitorLogPageListResp> records =
                jdecMonitorLogService.queryPageList(request, projectNo);
        return ApiResponse.success(records);
    }

    @GetMapping("/queryByModuleIdAndOrderId")
    @Operation(
            summary = "根据模块ID和单据编号查询多条日志",
            description = "传入moduleId+orderId查询多条日志记录，按时间倒序返回；可选logType指定日志类型")
    public ApiResponse<List<MonitorLogPageListResp>> queryByModuleIdAndOrderId(
            @Parameter(description = "模块ID", required = true) @RequestParam Long moduleId,
            @Parameter(description = "单据编号", required = true) @RequestParam String orderId,
            @Parameter(description = "日志类型(messagelog/controllog/systemlog，不传则查全表)")
                    @RequestParam(required = false)
                    String logType,
            @Parameter(description = "项目编号")
                    @RequestHeader(value = "X-Project-No", required = false)
                    String projectNo) {
        List<MonitorLogPageListResp> result =
                jdecMonitorLogService.queryByModuleIdAndOrderId(
                        moduleId, orderId, logType, projectNo);
        return ApiResponse.success(result);
    }

    @GetMapping("/queryByMenuIdAndDataId")
    @Operation(
            summary = "根据菜单ID和单据ID查询单条最新日志",
            description = "传入menuId+dataId查询单条最新日志记录；可选logType指定日志类型")
    public ApiResponse<MonitorLogPageListResp> queryByMenuIdAndDataId(
            @Parameter(description = "菜单ID", required = true) @RequestParam Long menuId,
            @Parameter(description = "单据ID", required = true) @RequestParam Long dataId,
            @Parameter(description = "项目编号")
                    @RequestHeader(value = "X-Project-No", required = false)
                    String projectNo) {
        MonitorLogPageListResp result =
                jdecMonitorLogService.queryByMenuIdAndDataId(menuId, dataId, projectNo);
        return ApiResponse.success(result);
    }

    @GetMapping("/querySystemControlLogByModuleIdAndDataId")
    @Operation(
            summary = "根据模块ID和单据ID查询系统操作日志列表",
            description = "传入moduleId+dataId查询系统操作日志（controllog）列表，按时间倒序排列")
    public ApiResponse<List<MonitorLogPageListResp>> querySystemControlLogByModuleIdAndDataId(
            @Parameter(description = "模块ID", required = true) @RequestParam Long moduleId,
            @Parameter(description = "单据ID（dataId）", required = true) @RequestParam Long dataId,
            @Parameter(description = "项目编号")
                    @RequestHeader(value = "X-Project-No", required = false)
                    String projectNo) {
        List<MonitorLogPageListResp> result =
                jdecMonitorLogService.querySystemControlLogByModuleIdAndDataId(
                        moduleId, dataId, projectNo);
        return ApiResponse.success(result);
    }

    @PostMapping("/addSystemLog")
    @Operation(
            summary = "添加系统日志",
            description =
                    "记录系统日志，如系统登录、退出等操作"
                            + "     * logContent 为日志详细内容"
                            + "     * logRemark 为日志备注(JSON格式)")
    public ApiResponse<Void> addSystemLog(
            @Valid @RequestBody MonitorSystemLogReq request,
            @Parameter(description = "项目编号")
                    @RequestHeader(value = "X-Project-No", required = false)
                    String projectNo) {
        populateSubjectNameIfAbsent(request);
        jdecMonitorLogService.recordSystemControlLog(request, projectNo);
        return ApiResponse.success();
    }

    @PostMapping("/addControlLog")
    @Operation(
            summary = "添加控制日志/单据操作日志",
            description =
                    "记录控制日志/单据操作日志"
                            + "     * logContent 为日志详细内容"
                            + "     * logRemark 为备注信息(自定义json，后台不做解析)")
    public ApiResponse<Void> addControlLog(
            @RequestBody MonitorControlLogReq request,
            @Parameter(description = "项目编号")
                    @RequestHeader(value = "X-Project-No", required = false)
                    String projectNo) {
        populateSubjectNameIfAbsent(request);
        jdecMonitorLogService.recordOrderRunLog(request, projectNo);
        return ApiResponse.success();
    }

    @PostMapping("/addMessageLog")
    @Operation(
            summary = "添加消息日志",
            description =
                    """
                    添加消息日志
                    日志内容为：发送消息的文本，如企业微信消息MD文本
                         *  logRemark 如：[元件管理-FHSQ2409230955401 V2] 消息类型：元件驳回
                                                          消息接收人：张三J10006821  李四J10006822
                    remark是Json格式的字符串，格式如下：
                    {
                                "moduleId": "模块ID",
                                "moduleName": "模块名称",
                                "menuId": "菜单ID",
                                "menuParam": "菜单参数",
                                "hrefName": "超链接文本",
                                "dataId": "数据表记录ID",
                                "orderVersion": "单据版本号/订单版本号/业务版本号/任务版本号",
                                "controlName":"操作类型/消息类型",
                                "messageReceivers":"消息接收人[userid1,userid2,...]",
                                "usersDetails":"消息接收人详情[{"UserName":"张三","UserNumber":"J10006821","UserAvatar":"https://example.com/avatar.jpg"},"UserName":"李四","UserNumber":"J10006822","UserAvatar":"https://example.com/avatar.jpg"}]"
                            }
                    """)
    public ApiResponse<Void> addMessageLog(
            @Valid @RequestBody MonitorMessageLogReq request,
            @Parameter(description = "项目编号")
                    @RequestHeader(value = "X-Project-No", required = false)
                    String projectNo) {
        populateSubjectNameIfAbsent(request);
        jdecMonitorLogService.recordMessageLog(request, projectNo);
        return ApiResponse.success();
    }

    private static final Set<String> BASE_LOG_TYPES =
            Set.of("loginin", "loginout", "changerole", "resetuser", "changesubject");
    private static final Set<String> MOCK_LOG_TYPES = Set.of("mockuser", "mockchangerole");

    @PostMapping("/sendSystemLog")
    @Operation(
            summary = "发送系统日志(Header+Body传参)",
            description =
                    "通过Header参数记录系统控制日志，根据logType类型进行不同字段校验："
                            + "loginin/loginout/changerole/resetuser/changesubject 校验基础字段；"
                            + "mockuser/mockchangerole 额外校验模拟用户字段。"
                            + "传入loginUserId和loginRoleId，服务端自动获取用户、角色和模拟用户信息。"
                            + "Mock标记服务端自动推断：Mock-User-Id>0时为1，否则为0，无需传Mock-Flug。"
                            + "浏览器和系统名称通过Body传递，请求体格式："
                            + "{\"browser\": \"Chrome\", \"systemName\": \"系统名称\"}")
    public ApiResponse<Void> sendSystemLog(
            @Parameter(
                            description =
                                    "日志类型(loginin/loginout/changerole/mockuser/mockchangerole/resetuser/changesubject)",
                            required = true)
                    @RequestHeader("Log-Type")
                    String logType,
            @Parameter(description = "项目编号")
                    @RequestHeader(value = "X-Project-No", required = false)
                    String projectNo,
            @Parameter(description = "登录用户ID", required = true)
                    @RequestHeader(value = "Login-User-Id")
                    Long loginUserId,
            @Parameter(description = "登录角色ID", required = true) @RequestHeader(value = "Role-Id")
                    Long loginRoleId,
            @Parameter(description = "主体ID")
                    @RequestHeader(value = "X-Subject-Id", required = false)
                    Long subjectId,
            @Parameter(description = "模拟用户ID，大于0时自动开启mock模式")
                    @RequestHeader(value = "Mock-User-Id", required = false)
                    Long mockUserId,
            @Valid @RequestBody MonitorSendSystemLogPostReq postReq) {

        if (!StringUtils.hasText(logType)) {
            throw new IllegalArgumentException("日志类型(Log-Type)不能为空");
        }
        boolean isBaseType = BASE_LOG_TYPES.contains(logType);
        boolean isMockType = MOCK_LOG_TYPES.contains(logType);
        if (!isBaseType && !isMockType) {
            throw new IllegalArgumentException(
                    "日志类型(Log-Type)不合法，允许值：loginin/loginout/changerole/resetuser/changesubject/mockuser/mockchangerole");
        }

        if (!StringUtils.hasText(projectNo)) {
            throw new IllegalArgumentException("项目编号(X-Project-No)不能为空");
        }
        if (loginUserId == null || loginUserId <= 0) {
            throw new IllegalArgumentException("登录用户ID(Login-User-Id)不能为空且必须大于0");
        }
        if (loginRoleId == null || loginRoleId <= 0) {
            throw new IllegalArgumentException("登录角色ID(Login-Role-Id)不能为空且必须大于0");
        }
        if (subjectId == null || subjectId <= 0) {
            throw new IllegalArgumentException("主体ID(X-Subject-Id)不能为空且必须大于0");
        }
        SysProjectSubjectResp subjectResp = sysProjectSubjectApi.getById(subjectId);
        if (subjectResp == null) {
            throw new IllegalArgumentException("主体信息不存在(X-Subject-Id=" + subjectId + ")");
        }
        String subjectName = subjectResp.getSubjectName();
        if (!StringUtils.hasText(subjectName)) {
            throw new IllegalArgumentException("主体名称不能为空");
        }
        String browser = postReq.getBrowser();
        String systemName = postReq.getSystemName();
        String ip = RequestContextUtils.getClientIp();
        if (!StringUtils.hasText(ip)) {
            throw new IllegalArgumentException("IP地址获取失败，请检查HTTP请求");
        }
        if (ip.length() > 20) {
            throw new IllegalArgumentException("IP地址长度不能超过20字符，当前IP：" + ip);
        }
        if (!StringUtils.hasText(browser)) {
            browser = "unknown";
        }

        int mockFlug;
        if (mockUserId != null && mockUserId > 0) {
            if (mockUserId.equals(loginUserId)) {
                mockUserId = null;
                mockFlug = 0;
            } else {
                mockFlug = 1;
            }
        } else {
            mockUserId = null;
            mockFlug = 0;
        }

        if (isMockType || mockFlug == 1) {
            if (mockUserId == null) {
                throw new IllegalArgumentException(
                        "模拟用户ID(Mock-User-Id)必须大于0且不能与Login-User-Id相同，日志类型为mockuser/mockchangerole或开启mock时必填");
            }
        }

        MonitorSystemLogReq request = new MonitorSystemLogReq();
        request.setLoginUserId(loginUserId);
        request.setLoginRoleId(loginRoleId);
        request.setLogType(logType);
        request.setSubjectId(subjectId);
        request.setSubjectName(subjectName);
        request.setMockFlag(mockFlug);
        request.setMockUserId(mockUserId);
        request.setIp(ip);
        request.setBrowser(browser);
        request.setSystemName(systemName);
        request.setLogSubType(logType);

        jdecMonitorLogService.recordSystemControlLog(request, projectNo);
        return ApiResponse.success();
    }

    @PostMapping("/sendSystemNavLog")
    @Operation(
            summary = "发送系统导航日志(Header+Body传参)",
            description =
                    """
                    通过Header传基础字段（项目编号、登录用户ID、登录角色ID、主体ID、模拟用户ID），\
                    POST Body传业务字段。服务端自动获取用户、角色、模拟用户信息，以及IP地址从HTTP请求获取。\
                    Mock标记服务端自动推断：Mock-User-Id>0时为1，否则为0，无需传Mock-Flug。
                    请求体内容和格式如下：
                    {
                      "moduleId": "模块ID",      // required,
                      "moduleName": "模块名称",      // required,
                      "menuId": "菜单ID",      // required,
                      "menuParam": "菜单参数",        // required
                      "hrefName": "超链接文本",       // required
                      "dataId": 456,                 // 可选
                      "controlDesc": "操作描述",      // required
                      "browser": "Chrome",           // required, <=100字符，默认"unknown"
                      "platForm": "Windows",         // required, <=100字符，默认"unknown"
                      "systemName": "系统名称"        // required, <=100字符，默认"unknown"
                    }
                    """)
    public ApiResponse<Void> sendSystemNavLog(
            @Parameter(description = "项目编号", required = true) @RequestHeader("X-Project-No")
                    String projectNo,
            @Parameter(description = "登录用户ID", required = true) @RequestHeader("Login-User-Id")
                    Long loginUserId,
            @Parameter(description = "登录角色ID", required = true) @RequestHeader("Role-Id")
                    Long loginRoleId,
            @Parameter(description = "主体ID", required = true) @RequestHeader("X-Subject-Id")
                    Long subjectId,
            @Parameter(description = "模拟用户ID，大于0时自动开启mock模式")
                    @RequestHeader(value = "Mock-User-Id", required = false)
                    Long mockUserId,
            @Valid @RequestBody MonitorSystemNavPostReq postReq) {

        /*if (!StringUtils.hasText(projectNo)) {
            throw new IllegalArgumentException("项目编号(X-Project-No)不能为空");
        }
        if (loginUserId == null || loginUserId <= 0) {
            throw new IllegalArgumentException("登录用户ID(Login-User-Id)不能为空且必须大于0");
        }
        if (loginRoleId == null || loginRoleId <= 0) {
            throw new IllegalArgumentException("登录角色ID(Login-Role-Id)不能为空且必须大于0");
        }
        if (subjectId == null || subjectId <= 0) {
            throw new IllegalArgumentException("主体ID(X-Subject-Id)不能为空且必须大于0");
        }
        SysProjectSubjectResp subjectResp = sysProjectSubjectApi.getById(subjectId);
        if (subjectResp == null) {
            throw new IllegalArgumentException("主体信息不存在(X-Subject-Id=" + subjectId + ")");
        }
        String subjectName = subjectResp.getSubjectName();
        if (!StringUtils.hasText(subjectName)) {
            throw new IllegalArgumentException("主体名称不能为空");
        }
        int mockFlug;
        if (mockUserId != null && mockUserId > 0) {
            if (mockUserId.equals(loginUserId)) {
                mockUserId = null;
                mockFlug = 0;
            } else {
                mockFlug = 1;
            }
        } else {
            mockUserId = null;
            mockFlug = 0;
        }
        String ip = RequestContextUtils.getClientIp();
        if (!StringUtils.hasText(ip)) {
            throw new IllegalArgumentException("IP地址获取失败，请检查HTTP请求");
        }
        if (ip.length() > 20) {
            throw new IllegalArgumentException("IP地址长度不能超过20字符，当前IP：" + ip);
        }

        String moduleName = postReq.getModuleName();
        String menuParam = postReq.getMenuParam();
        String hrefName = postReq.getHrefName();
        String controlDesc = postReq.getControlDesc();
        String browser = postReq.getBrowser();
        String systemName = postReq.getSystemName();
        Long moduleId = postReq.getModuleId();

        MonitorSystemNavLogReq request = new MonitorSystemNavLogReq();
        request.setLoginUserId(loginUserId);
        request.setLoginRoleId(loginRoleId);
        request.setSubjectId(subjectId);
        request.setSubjectName(subjectName);
        request.setMockFlag(mockFlug);
        request.setMockUserId(mockUserId);
        request.setIp(ip);
        request.setModuleId(moduleId);
        request.setModuleName(moduleName);
        request.setMenuId(postReq.getMenuId());
        request.setDataId(postReq.getDataId());
        request.setMenuParam(menuParam);
        request.setHrefName(hrefName);
        request.setBrowser(browser);
        request.setControlName(controlDesc);
        request.setPlatForm(postReq.getPlatForm());
        request.setSystemName(systemName);
        request.setLogSubType("systemNavLog");
        jdecMonitorLogService.recordSystemNavLog(request, projectNo);*/
        return ApiResponse.success();
    }

    private void populateSubjectNameIfAbsent(MonitorBaseLogReq req) {
        if (req.getSubjectId() == null || req.getSubjectId() <= 0) {
            return;
        }
        if (StringUtils.hasText(req.getSubjectName())) {
            return;
        }
        SysProjectSubjectResp subjectResp = sysProjectSubjectApi.getById(req.getSubjectId());
        if (subjectResp != null && StringUtils.hasText(subjectResp.getSubjectName())) {
            req.setSubjectName(subjectResp.getSubjectName());
        }
    }
}
