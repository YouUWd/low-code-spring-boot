package com.jdec.platform.monitorlog.biz.controller;

import com.jdec.platform.monitorlog.api.dto.request.*;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogPageListResp;
import com.jdec.platform.monitorlog.biz.service.JdecMonitorLogService;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
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
            @Valid @RequestBody MonitorControlLogReq request,
            @Parameter(description = "项目编号")
                    @RequestHeader(value = "X-Project-No", required = false)
                    String projectNo) {
        jdecMonitorLogService.recordOrderRunLog(request, projectNo);
        return ApiResponse.success();
    }

    @PostMapping("/addMessageLog")
    @Operation(
            summary = "添加消息日志",
            description =
                    "添加消息日志"
                            + "日志内容为：发送消息的文本，如企业微信消息MD文本"
                            + "     *  logRemark 如：[元件管理-FHSQ2409230955401 V2] 消息类型：元件驳回"
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
    public ApiResponse<Void> addMessageLog(
            @Valid @RequestBody MonitorMessageLogReq request,
            @Parameter(description = "项目编号")
                    @RequestHeader(value = "X-Project-No", required = false)
                    String projectNo) {
        jdecMonitorLogService.recordMessageLog(request, projectNo);
        return ApiResponse.success();
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

    private static final Set<String> BASE_LOG_TYPES =
            Set.of("loginin", "loginout", "changerole", "resetuser", "changesubject");
    private static final Set<String> MOCK_LOG_TYPES = Set.of("mockuser", "mockchangerole");

    @PostMapping("/sendSystemLog")
    @Operation(
            summary = "发送系统日志(Header传参)",
            description =
                    "通过Header参数记录系统控制日志，根据logType类型进行不同字段校验："
                            + "loginin/loginout/changerole/resetuser/changesubject 校验基础字段；"
                            + "mockuser/mockchangerole 额外校验模拟用户字段。"
                            + "传入loginUserId和loginRoleId，服务端自动获取用户、角色和模拟用户信息")
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
            @Parameter(description = "登录角色ID", required = true)
                    @RequestHeader(value = "Login-Role-Id")
                    Long loginRoleId,
            @Parameter(description = "主体ID")
                    @RequestHeader(value = "X-Subject-Id", required = false)
                    Long subjectId,
            @Parameter(description = "主体名称")
                    @RequestHeader(value = "Subject-Name", required = false)
                    String subjectName,
            @Parameter(description = "是否mock标记：0否；1是")
                    @RequestHeader(value = "Mock-Flug", required = false)
                    Integer mockFlug,
            @Parameter(description = "模拟用户ID(mockFlag=1时必传)")
                    @RequestHeader(value = "Mock-User-Id", required = false)
                    Long mockUserId,
            @Parameter(description = "IP地址") @RequestHeader(value = "X-Real-IP", required = false)
                    String ip,
            @Parameter(description = "浏览器") @RequestHeader(value = "Browser", required = false)
                    String browser,
            @Parameter(description = "系统名称") @RequestHeader(value = "System-Name", required = false)
                    String systemName) {

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
        if (!StringUtils.hasText(subjectName)) {
            throw new IllegalArgumentException("主体名称(Subject-Name)不能为空");
        }
        if (!StringUtils.hasText(systemName)) {
            throw new IllegalArgumentException("系统名称(System-Name)不能为空");
        }
        if (systemName.length() > 100) {
            throw new IllegalArgumentException("系统名称(System-Name)长度不能超过100字符");
        }
        if (!StringUtils.hasText(ip)) {
            throw new IllegalArgumentException("IP地址(X-Real-IP)不能为空");
        }
        if (ip.length() > 20) {
            throw new IllegalArgumentException("IP地址(X-Real-IP)长度不能超过20字符");
        }
        if (!StringUtils.hasText(browser)) {
            throw new IllegalArgumentException("浏览器(Browser)不能为空");
        }
        if (browser.length() > 100) {
            throw new IllegalArgumentException("浏览器(Browser)长度不能超过100字符");
        }

        if (mockFlug == null) {
            mockFlug = isMockType ? 1 : 0;
        }
        if (mockFlug != 0 && mockFlug != 1) {
            throw new IllegalArgumentException("mock标记(Mock-Flug)只能为0或1");
        }

        if (isMockType || mockFlug == 1) {
            if (mockUserId == null || mockUserId <= 0) {
                throw new IllegalArgumentException("模拟用户ID(Mock-User-Id)不能为空且必须大于0");
            }
        }

        MonitorSystemLogReq request = new MonitorSystemLogReq();
        request.setUserId(loginUserId);
        request.setRoleId(loginRoleId);
        request.setLogType(logType);
        request.setSubjectId(subjectId);
        request.setSubjectName(subjectName);
        request.setMockFlag(mockFlug);
        request.setMockUserId(mockUserId);
        request.setIp(ip);
        request.setBrowser(browser);
        request.setSystemName(systemName);

        jdecMonitorLogService.recordSystemControlLog(request, projectNo);
        return ApiResponse.success();
    }

    @PostMapping("/sendSystemNavLog")
    @Operation(
            summary = "发送系统导航日志(Header传参)",
            description = "通过Header参数记录系统导航日志，传入loginUserId和loginRoleId，服务端自动获取用户、角色和模拟用户信息")
    public ApiResponse<Void> sendSystemNavLog(
            @Parameter(description = "项目编号", required = true) @RequestHeader("X-Project-No")
                    String projectNo,
            @Parameter(description = "登录用户ID", required = true) @RequestHeader("Login-User-Id")
                    Long loginUserId,
            @Parameter(description = "登录角色ID", required = true) @RequestHeader("Login-Role-Id")
                    Long loginRoleId,
            @Parameter(description = "主体ID", required = true) @RequestHeader("X-Subject-Id")
                    Long subjectId,
            @Parameter(description = "主体名称", required = true) @RequestHeader("Subject-Name")
                    String subjectName,
            @Parameter(description = "是否mock标记：0否；1是", required = true) @RequestHeader("Mock-Flug")
                    Integer mockFlug,
            @Parameter(description = "模拟用户ID(mockFlag=1时必传)")
                    @RequestHeader(value = "Mock-User-Id", required = false)
                    Long mockUserId,
            @Parameter(description = "IP地址", required = true) @RequestHeader("X-Real-IP") String ip,
            @Parameter(description = "模块名称") @RequestHeader("Module-Name") String moduleName,
            @Parameter(description = "菜单参数") @RequestHeader("Menu-Param") String menuParam,
            @Parameter(description = "菜单名称") @RequestHeader("Href-Name") String hrefName,
            @Parameter(description = "菜单URL") @RequestHeader("Href-Url") String hrefUrl,
            @Parameter(description = "菜单操作描述") @RequestHeader("Control-Desc") String controlDesc,
            @Parameter(description = "浏览器", required = true) @RequestHeader("Browser")
                    String browser,
            @Parameter(description = "系统名称", required = true) @RequestHeader("System-Name")
                    String systemName) {

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
            throw new IllegalArgumentException("主体ID(subject-id)不能为空且必须大于0");
        }
        if (!StringUtils.hasText(subjectName)) {
            throw new IllegalArgumentException("主体名称(subject-name)不能为空");
        }
        if (mockFlug == null || (mockFlug != 0 && mockFlug != 1)) {
            throw new IllegalArgumentException("mock标记(mock-flug)不能为空且只能为0或1");
        }
        if (mockFlug == 1) {
            if (mockUserId == null || mockUserId <= 0) {
                throw new IllegalArgumentException("模拟用户ID(mock-user-id)不能为空且必须大于0");
            }
        }
        if (!StringUtils.hasText(ip)) {
            throw new IllegalArgumentException("IP地址(ip)不能为空");
        }
        if (ip.length() > 20) {
            throw new IllegalArgumentException("IP地址(ip)长度不能超过20字符");
        }
        if (!StringUtils.hasText(moduleName)) {
            throw new IllegalArgumentException("模块名称(module-name)不能为空");
        }
        if (moduleName.length() > 100) {
            throw new IllegalArgumentException("模块名称(module-name)长度不能超过100字符");
        }
        if (!StringUtils.hasText(menuParam)) {
            throw new IllegalArgumentException("菜单参数(menu-param)不能为空");
        }
        if (!StringUtils.hasText(hrefName)) {
            throw new IllegalArgumentException("菜单名称(href-name)不能为空");
        }
        if (!StringUtils.hasText(hrefUrl)) {
            throw new IllegalArgumentException("菜单操作URL(href-url)不能为空");
        }
        if (!StringUtils.hasText(controlDesc)) {
            throw new IllegalArgumentException("操作描述(control-desc)不能为空");
        }
        if (!StringUtils.hasText(browser)) {
            throw new IllegalArgumentException("浏览器(browser)不能为空");
        }
        if (browser.length() > 100) {
            throw new IllegalArgumentException("浏览器(browser)长度不能超过100字符");
        }
        if (!StringUtils.hasText(systemName)) {
            throw new IllegalArgumentException("系统名称(system-name)不能为空");
        }
        if (systemName.length() > 100) {
            throw new IllegalArgumentException("系统名称(system-name)长度不能超过100字符");
        }

        MonitorSystemNavLogReq request = new MonitorSystemNavLogReq();
        request.setUserId(loginUserId);
        request.setRoleId(loginRoleId);
        request.setSubjectId(subjectId);
        request.setSubjectName(subjectName);
        request.setMockFlag(mockFlug);
        request.setMockUserId(mockUserId);
        request.setIp(ip);
        request.setModuleName(moduleName);
        request.setMenuParam(menuParam);
        request.setHrefName(hrefName);
        request.setControlUrl(hrefUrl);
        request.setControlDesc(controlDesc);
        request.setBrowser(browser);
        request.setSystemName(systemName);
        request.setLogContent(
                "<span class=\"arco-table-td-content\"><div class=\"flex\"><span>["
                        + moduleName
                        + "-<a style=\"color: #408bee\" href=\""
                        + hrefUrl
                        + "\" target=\"_blank\">"
                        + hrefName
                        + "</a>]<span style=\"font-weight: 700; color: #2e7fed\">"
                        + controlDesc
                        + "</span></span></div></span>");
        jdecMonitorLogService.recordSystemNavLog(request, projectNo);
        return ApiResponse.success();
    }
}
