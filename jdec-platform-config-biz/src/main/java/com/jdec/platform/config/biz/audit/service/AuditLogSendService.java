package com.jdec.platform.config.biz.audit.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.constant.MonitorLogConstant;
import com.jdec.platform.config.api.dto.log.MonitorContentOperation;
import com.jdec.platform.config.api.dto.log.MonitorLoginInfo;
import com.jdec.platform.config.api.dto.log.MonitorMockInfo;
import com.jdec.platform.config.api.dto.request.MonitorLogSaveReq;
import com.jdec.platform.config.biz.audit.dto.AuditLogSendContext;
import com.jdec.platform.config.biz.entity.SysMenu;
import com.jdec.platform.config.biz.mapper.SysMenuMapper;
import com.jdec.platform.config.open.api.OpenMonitorLogApi;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.security.SecurityUtils;
import com.jdec.platform.shared.security.model.LoginUser;
import com.jdec.platform.shared.utils.RequestContextUtils;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 审计日志发送服务
 *
 * <p>统一构建操作日志（{@code monitor_log.type=0}）并交由监控日志接口落库，本服务只负责给出 id 类信息：
 *
 * <ul>
 *   <li><b>登录用户 / 角色 / 被模拟用户</b>：只填 id（{@code loginInfo.userId}、{@code loginInfo.roleId}、{@code
 *       mockInfo.userId}），姓名、头像、工号、部门、岗位、角色名称由监控日志侧跨库查回补全。
 *   <li><b>主体</b>：只填 id，主体名称由监控日志侧查回补全。
 *   <li><b>模块ID</b>：通过去掉前导{@code /}后的 {@code href-url} 匹配 {@code sys_menu.param} 反查菜单，
 *       按当前主体+项目编码过滤后取其关联模块ID。
 *   <li><b>IP/浏览器/平台</b>：通过 {@link RequestContextUtils} 从当前请求获取。
 * </ul>
 *
 * <p>发送策略：<b>非阻塞</b>。发送失败仅记录错误日志，不影响业务事务（审计属于辅助功能，不应阻塞业务操作）。
 *
 * <p>注意：本服务标注 {@code @DataSource(config_center)}，其内部查询统一走配置中心数据源； 通过数据源栈（{@code
 * DataSourceContextHolder}）支持嵌套切换，不会污染调用方数据源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class AuditLogSendService {

    /** 日志时间格式（与调用方传入的 {@code logDate} 一致） */
    private static final DateTimeFormatter LOG_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysMenuMapper sysMenuMapper;
    private final ObjectMapper objectMapper;
    private final OpenMonitorLogApi openMonitorLogApi;

    /**
     * 构建并发送审计日志（便捷方法，非阻塞）。
     *
     * <p>内部先构建请求体，再发送；任何异常均被吞掉并记录日志，保证不影响业务事务。
     *
     * @param context 调用方自定义的审计日志字段
     */
    public void sendAuditLog(AuditLogSendContext context) {
        try {
            MonitorLogSaveReq request = buildRequest(context);
            send(request);
        } catch (Exception e) {
            log.error("审计日志构建/发送异常", e);
        }
    }

    /**
     * 构建完整的操作日志请求体。
     *
     * <p>除 {@link AuditLogSendContext} 中自定义的字段外，其余信息均从当前请求上下文取 id，展示信息由监控日志侧补全。
     *
     * @param context 调用方自定义的审计日志字段
     * @return 监控日志保存请求（type=0 操作日志）
     */
    public MonitorLogSaveReq buildRequest(AuditLogSendContext context) {
        LoginUser loginUserInfo = SecurityUtils.getLoginUser();
        boolean isMockLogin =
                loginUserInfo != null && Boolean.TRUE.equals(loginUserInfo.getIsMockLogin());
        // 登录用户ID：非模拟为 token 中的当前用户，模拟登录时为真实操作用户（与旧逻辑一致）
        Long loginUserId = loginUserInfo != null ? loginUserInfo.getRealUserId() : null;
        Long loginRoleId = loginUserInfo != null ? loginUserInfo.getRoleId() : null;
        // 模拟用户：仅模拟登录时存在
        Long mockUserId = isMockLogin ? loginUserInfo.getMockUserId() : null;

        // 菜单参数：直接透传前端请求头 href-url（去掉前导/）
        String hrefUrl =
                loginUserInfo != null
                        ? StrUtil.removePrefix(loginUserInfo.getHrefUrl(), "/")
                        : null;
        // 模块ID：通过去掉前导/后的 hrefUrl 匹配菜单 param，反查菜单关联模块ID
        Long moduleId = resolveModuleId(hrefUrl);

        MonitorLogSaveReq req = new MonitorLogSaveReq();
        req.setType(MonitorLogConstant.TYPE_OPERATION_LOG);
        req.setSubjectId(AppContext.getSubjectId());
        req.setModuleId(moduleId);
        req.setDataId(context != null ? context.getDataId() : null);
        req.setIp(RequestContextUtils.getClientIp());
        req.setBrowser(RequestContextUtils.getBrowser());
        req.setPlatForm(RequestContextUtils.getPlatform());
        req.setLoginInfo(buildLoginInfo(loginUserId, loginRoleId));
        req.setMockInfo(buildMockInfo(isMockLogin, mockUserId));
        req.setContent(buildContent(context, hrefUrl));
        req.setRemark(parseRemark(context != null ? context.getLogRemark() : null));
        req.setRecordTime(resolveRecordTime(context != null ? context.getLogDate() : null));
        return req;
    }

    /**
     * 发送操作日志到监控日志接口（非阻塞）。
     *
     * <p>发送失败仅记录错误日志，不抛出异常，保证不影响业务事务。
     *
     * @param request 监控日志保存请求
     */
    public void send(MonitorLogSaveReq request) {
        try {
            if (request == null) {
                return;
            }
            log.info("发送操作日志到监控日志接口 - request={}", request);
            openMonitorLogApi.saveLog(request);
        } catch (Exception e) {
            // 非阻塞：发送失败仅记录日志，不影响业务事务
            log.error("操作日志发送异常", e);
        }
    }

    // ==================== 请求体组装 ====================

    /** 登录信息：只填用户id与角色id，姓名/头像/工号/部门/岗位/角色名称由监控日志侧补全 */
    private MonitorLoginInfo buildLoginInfo(Long userId, Long roleId) {
        if (userId == null && roleId == null) {
            return null;
        }
        MonitorLoginInfo loginInfo = new MonitorLoginInfo();
        loginInfo.setUserId(userId);
        loginInfo.setRoleId(roleId);
        return loginInfo;
    }

    /** 模拟信息：只填是否模拟与被模拟用户id，姓名/头像/工号由监控日志侧补全（非模拟登录时 flag=0） */
    private MonitorMockInfo buildMockInfo(boolean isMockLogin, Long mockUserId) {
        MonitorMockInfo mockInfo = new MonitorMockInfo();
        mockInfo.setFlag(isMockLogin ? 1 : 0);
        mockInfo.setUserId(mockUserId);
        return mockInfo;
    }

    /** 日志内容：操作日志子类型（{@code logSubType=operationLog}），承载模块/链接/操作/结果/字体颜色/菜单参数 */
    private MonitorContentOperation buildContent(AuditLogSendContext context, String menuParam) {
        MonitorContentOperation content = new MonitorContentOperation();
        content.setLogSubType(MonitorLogConstant.OPERATION_LOG);
        content.setModuleName(context != null ? context.getModule() : null);
        content.setLinkName(context != null ? context.getSubModule() : null);
        content.setOperation(context != null ? context.getControlName() : null);
        content.setResult(context != null ? context.getOperatorResult() : null);
        // 字体颜色暂时为空，后续从系统配置获取
        content.setFontColor("");
        content.setMenuParam(menuParam);
        return content;
    }

    /** 备注：原为字段差异 JSON 字符串，解析为对象后落库；解析失败按原始字符串保存 */
    private Object parseRemark(String logRemark) {
        if (StrUtil.isBlank(logRemark)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(logRemark);
            return node != null && !node.isNull() ? node : null;
        } catch (Exception e) {
            log.warn("审计日志备注解析失败，按原始字符串保存: logRemark={}", logRemark, e);
            return logRemark;
        }
    }

    /** 日志时间：调用方传入 {@code yyyy-MM-dd HH:mm:ss}，为空或格式非法时取当前时间 */
    private LocalDateTime resolveRecordTime(String logDate) {
        if (StrUtil.isBlank(logDate)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(logDate, LOG_DATE_FORMATTER);
        } catch (Exception e) {
            log.warn("审计日志时间解析失败，取当前时间: logDate={}", logDate);
            return LocalDateTime.now();
        }
    }

    /**
     * 通过菜单参数反查模块ID。
     *
     * <p>用去掉前导{@code /}后的 {@code href-url} 匹配 {@code sys_menu.param}，并按当前主体+项目编码过滤， 取命中菜单的关联模块ID。
     *
     * @param hrefUrl 去掉前导{@code /}后的菜单参数
     * @return 关联模块ID，未匹配到菜单返回 {@code null}
     */
    private Long resolveModuleId(String hrefUrl) {
        if (StrUtil.isBlank(hrefUrl)) {
            return null;
        }
        try {
            SysMenu menu =
                    sysMenuMapper.selectOne(
                            new LambdaQueryWrapper<SysMenu>()
                                    .eq(SysMenu::getParam, hrefUrl)
                                    .eq(
                                            AppContext.getSubjectId() != null,
                                            SysMenu::getSubjectId,
                                            AppContext.getSubjectId())
                                    .eq(
                                            StrUtil.isNotBlank(AppContext.getProjectNo()),
                                            SysMenu::getProjectNo,
                                            AppContext.getProjectNo())
                                    .last("limit 1"));
            return menu != null ? menu.getModuleId() : null;
        } catch (Exception e) {
            log.warn("反查菜单模块ID失败: hrefUrl={}", hrefUrl, e);
            return null;
        }
    }
}
