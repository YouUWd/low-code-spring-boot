package com.jdec.platform.config.biz.audit.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.biz.audit.dto.AuditLogRequest;
import com.jdec.platform.config.biz.audit.dto.AuditLogSendContext;
import com.jdec.platform.config.biz.entity.SysMenu;
import com.jdec.platform.config.biz.entity.SysRole;
import com.jdec.platform.config.biz.mapper.SysMenuMapper;
import com.jdec.platform.config.biz.mapper.SysRoleMapper;
import com.jdec.platform.hr.api.SubjectApi;
import com.jdec.platform.hr.api.UserApi;
import com.jdec.platform.hr.api.bo.SubjectBO;
import com.jdec.platform.hr.api.bo.UserBO;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.TokenContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.security.SecurityUtils;
import com.jdec.platform.shared.security.model.LoginUser;
import com.jdec.platform.shared.utils.RequestContextUtils;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 审计日志HTTP发送服务
 *
 * <p>统一构建审计日志请求体并发送到监控服务，集中补全以下信息，避免各调用方重复实现：
 *
 * <ul>
 *   <li><b>登录用户/模拟用户</b>：姓名、头像、工号、部门、岗位 —— 统一调人力模块 {@link UserApi} 查询。
 *   <li><b>角色名称</b>：通过 {@code sys_role} 由角色ID反查。
 *   <li><b>模块名称</b>：取自调用方 {@link AuditLogSendContext#getModule()}（即 {@code @DataAudit} 注解的 module
 *       值）。
 *   <li><b>菜单参数/链接名称</b>：菜单参数 {@code menuParam} 直接透传请求头 {@code href-url}，{@code hrefName}
 *       为调用方传入的业务子模块名称。
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

    private final SysRoleMapper sysRoleMapper;
    private final SysMenuMapper sysMenuMapper;
    private final UserApi userApi;
    private final SubjectApi subjectApi;
    private final RestTemplate restTemplate;

    @Value("${audit.monitor-log-url}")
    private String monitorLogUrl;

    /**
     * 构建并发送审计日志（便捷方法，非阻塞）。
     *
     * <p>内部先构建请求体，再发送；任何异常均被吞掉并记录日志，保证不影响业务事务。
     *
     * @param context 调用方自定义的审计日志字段
     */
    public void sendAuditLog(AuditLogSendContext context) {
        try {
            AuditLogRequest request = buildRequest(context);
            send(request);
        } catch (Exception e) {
            log.error("审计日志构建/发送异常", e);
        }
    }

    /**
     * 构建完整的审计日志请求体。
     *
     * <p>除 {@link AuditLogSendContext} 中自定义的字段外，其余信息均从当前请求上下文自动补全。
     *
     * @param context 调用方自定义的审计日志字段
     * @return 审计日志请求体
     */
    public AuditLogRequest buildRequest(AuditLogSendContext context) {
        Long subjectId = AppContext.getSubjectId();

        // 用户信息统一从 UserContext 获取（AppContext 仅填充 projectNo/subjectId，不包含用户信息）
        LoginUser loginUserInfo = SecurityUtils.getLoginUser();
        boolean isMockLogin =
                loginUserInfo != null && Boolean.TRUE.equals(loginUserInfo.getIsMockLogin());
        // 登录用户ID：非模拟为 token 中的当前用户，模拟登录时为真实操作用户（与旧逻辑一致）
        Long loginUserId = loginUserInfo != null ? loginUserInfo.getRealUserId() : null;
        UserInfo loginUser = loadUser(loginUserId);

        // 模拟用户：仅模拟登录时存在
        Long mockUserId = isMockLogin ? loginUserInfo.getMockUserId() : null;
        UserInfo mockUser = loadUser(mockUserId);

        // 角色：非模拟时登录角色=当前角色；模拟时当前角色归属模拟用户，登录角色为空
        Long loginRoleId = loginUserInfo != null ? loginUserInfo.getRoleId() : null;

        // 菜单参数：直接透传前端请求头 href-url（去掉前导/）
        String hrefUrl =
                loginUserInfo != null
                        ? StrUtil.removePrefix(loginUserInfo.getHrefUrl(), "/")
                        : null;
        // 模块ID：通过去掉前导/后的 hrefUrl 匹配菜单 param，反查菜单关联模块ID
        Long moduleId = resolveModuleId(hrefUrl);

        return AuditLogRequest.builder()
                .subjectId(subjectId)
                .subjectName(resolveSubjectName(subjectId))
                .loginUserId(loginUserId)
                .loginUserName(loginUser != null ? loginUser.userName() : null)
                .loginAvater(loginUser != null ? loginUser.avatar() : null)
                .loginUserNumber(loginUser != null ? loginUser.number() : null)
                .loginRoleId(loginRoleId)
                .loginRoleName(resolveRoleName(loginRoleId))
                .loginUserDeptId(loginUser != null ? loginUser.deptId() : null)
                .loginUserDeptName(loginUser != null ? loginUser.deptName() : null)
                .loginUserJobId(loginUser != null ? loginUser.jobId() : null)
                .loginUserJobName(loginUser != null ? loginUser.jobName() : null)
                .mockFlag(isMockLogin ? 1 : 0)
                .mockUserId(mockUserId)
                .mockUserName(mockUser != null ? mockUser.userName() : null)
                .mockUserAvater(mockUser != null ? mockUser.avatar() : null)
                .mockUserNumber(mockUser != null ? mockUser.number() : null)
                .ip(RequestContextUtils.getClientIp())
                .moduleName(context != null ? context.getModule() : null)
                .moduleId(moduleId)
                .menuParam(hrefUrl)
                .hrefName(context != null ? context.getSubModule() : null)
                .dataId(context != null ? context.getDataId() : null)
                .controlName(context != null ? context.getControlName() : null)
                .operatorResult(context != null ? context.getOperatorResult() : null)
                .fontColor("")
                .browser(RequestContextUtils.getBrowser())
                .platForm(RequestContextUtils.getPlatform())
                .logRemark(context != null ? context.getLogRemark() : null)
                .logDate(
                        context != null && StrUtil.isNotBlank(context.getLogDate())
                                ? context.getLogDate()
                                : DateUtil.now())
                .build();
    }

    /**
     * 发送审计日志到HTTP接口（非阻塞）。
     *
     * <p>发送失败仅记录错误日志，不抛出异常，保证不影响业务事务。
     *
     * @param request 审计日志请求体
     */
    public void send(AuditLogRequest request) {
        try {
            if (request == null) {
                return;
            }
            log.info("发送审计日志到HTTP接口 - request={}", JSONUtil.toJsonStr(request));

            String url = monitorLogUrl;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 透传鉴权与上下文请求头
            String token = TokenContext.getToken();
            if (token != null) {
                headers.set("Authorization", "Bearer " + token);
            }
            String projectNo = AppContext.getProjectNo();
            if (projectNo != null) {
                headers.set("X-Project-No", projectNo);
            }
            Long subjectId = AppContext.getSubjectId();
            if (subjectId != null) {
                headers.set("X-Subject-Id", String.valueOf(subjectId));
            }

            HttpEntity<AuditLogRequest> httpRequest = new HttpEntity<>(request, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, httpRequest, Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("审计日志发送失败，HTTP状态码: {}，url: {}", response.getStatusCode(), url);
                return;
            }
            log.info("审计日志发送成功: url={}, httpStatus={}", url, response.getStatusCode());
        } catch (Exception e) {
            // 非阻塞：发送失败仅记录日志，不影响业务事务
            log.error("审计日志发送异常: url={}", monitorLogUrl, e);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 加载用户信息（姓名、头像、工号、部门、岗位）。
     *
     * <p>统一通过人力模块 {@link UserApi#listByIds} 查询，不查 {@code sys_user}。
     *
     * @param userId 用户ID（员工ID）
     * @return 用户信息，用户不存在或查询失败返回 {@code null}
     */
    @DataSource(DataSourceConstants.HR_MANAGE)
    private UserInfo loadUser(Long userId) {
        if (userId == null) {
            return null;
        }

        try {
            List<UserBO> users = userApi.listByIds(Set.of(userId));
            if (users != null && !users.isEmpty()) {
                UserBO user = users.getFirst();
                return new UserInfo(
                        user.getId(),
                        user.getUserName(),
                        user.getUserAvatar(),
                        user.getWorkNumber(),
                        user.getDepartmentId(),
                        user.getDepartmentName(),
                        user.getJobId(),
                        user.getJobName());
            }
        } catch (Exception e) {
            log.warn("查询用户信息失败(userApi): userId={}", userId, e);
        }
        return null;
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

    /**
     * 根据角色ID查询角色名称。
     *
     * @param roleId 角色ID
     * @return 角色名称，角色不存在或为空返回 {@code null}
     */
    private String resolveRoleName(Long roleId) {
        if (roleId == null) {
            return null;
        }
        try {
            SysRole role = sysRoleMapper.selectById(roleId);
            return role != null ? role.getRoleName() : null;
        } catch (Exception e) {
            log.warn("查询角色名称失败: roleId={}", roleId, e);
            return null;
        }
    }

    /**
     * 根据主体ID解析主体名称。
     *
     * <p>统一通过人力模块 {@link SubjectApi#getSubjectList()} 查询。
     *
     * @param subjectId 主体ID
     * @return 主体名称，主体不存在返回 {@code null}
     */
    private String resolveSubjectName(Long subjectId) {
        if (subjectId == null) {
            return null;
        }

        try {
            List<SubjectBO> subjects = subjectApi.getSubjectList();
            if (subjects != null) {
                return subjects.stream()
                        .filter(subject -> Objects.equals(subject.getId(), subjectId))
                        .map(SubjectBO::getSubjectName)
                        .findFirst()
                        .orElse(null);
            }
        } catch (Exception e) {
            log.warn("查询主体名称失败(subjectApi): subjectId={}", subjectId, e);
        }
        return null;
    }

    /** 用户信息（姓名/头像/工号/部门/岗位） */
    private record UserInfo(
            Long id,
            String userName,
            String avatar,
            String number,
            String deptId,
            String deptName,
            String jobId,
            String jobName) {}
}
