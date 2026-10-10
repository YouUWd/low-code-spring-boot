package com.jdec.platform.common.biz.monitorlog.service;

import com.jdec.platform.common.api.dto.log.MonitorLoginInfo;
import com.jdec.platform.common.api.dto.log.MonitorMockInfo;
import com.jdec.platform.common.api.dto.request.MonitorLogSaveReq;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

/**
 * 监控日志信息补全服务
 *
 * <p>调用方只传 id，其余展示信息由本服务跨库查回后填充：
 *
 * <ul>
 *   <li><b>人力库（hr_manage）</b>：按用户id 查 {@code user} 表，补全登录用户与被模拟用户的姓名、头像、工号、部门、岗位。
 *   <li><b>配置中心（config_center）</b>：按主体id 查 {@code sys_project_subject} 补主体名称；按角色id 查 {@code
 *       sys_role} 补角色名称。
 * </ul>
 *
 * <p>补全属于辅助信息，任一查询失败仅记录告警并跳过，不影响日志落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorLogEnrichService {

    /** 人力库用户表 */
    private static final String TABLE_HR_USER = "user";

    /** 配置中心项目主体表 */
    private static final String TABLE_PROJECT_SUBJECT = "sys_project_subject";

    /** 配置中心角色表 */
    private static final String TABLE_SYS_ROLE = "sys_role";

    private final DataSourceResolver dataSourceResolver;

    /**
     * 补全日志请求中的展示信息（就地修改入参）。
     *
     * @param projectNo 项目编码，用于定位主体名称
     * @param req 监控日志保存请求
     */
    public void enrich(String projectNo, MonitorLogSaveReq req) {
        if (req == null) {
            return;
        }
        fillSubjectName(projectNo, req);
        fillLoginInfo(req.getLoginInfo());
        fillMockInfo(req.getMockInfo());
    }

    // ==================== 主体 ====================

    /** 主体名称：按主体id（+ 项目编码）查配置中心项目主体表 */
    private void fillSubjectName(String projectNo, MonitorLogSaveReq req) {
        Long subjectId = req.getSubjectId();
        if (subjectId == null || req.getSubjectName() != null) {
            return;
        }
        try {
            Condition condition = DSL.field(DSL.name("subject_id"), Long.class).eq(subjectId);
            if (isNotBlank(projectNo)) {
                condition =
                        condition.and(
                                DSL.field(DSL.name("project_no"), String.class).eq(projectNo));
            }
            String subjectName =
                    dsl(DataSourceConstants.CONFIG_CENTER)
                            .select(DSL.field(DSL.name("subject_name"), String.class))
                            .from(DSL.table(DSL.name(TABLE_PROJECT_SUBJECT)))
                            .where(condition)
                            .fetchOne(0, String.class);
            if (subjectName == null) {
                log.warn("监控日志主体名称补全失败，配置中心无该主体: subjectId={}", subjectId);
            }
            req.setSubjectName(subjectName);
        } catch (Exception e) {
            log.warn("监控日志主体名称补全失败: subjectId={}, projectNo={}", subjectId, projectNo, e);
        }
    }

    // ==================== 登录信息 ====================

    /** 登录用户：按用户id 查人力库补全用户信息，按角色id 查配置中心补全角色名称 */
    private void fillLoginInfo(MonitorLoginInfo loginInfo) {
        if (loginInfo == null) {
            return;
        }
        HrUser user = loadHrUser(loginInfo.getUserId());
        if (user != null) {
            loginInfo.setUserName(user.userName());
            loginInfo.setAvatar(user.avatar());
            loginInfo.setUserNumber(user.userNumber());
            loginInfo.setDeptId(user.deptId());
            loginInfo.setDeptName(user.deptName());
            loginInfo.setJobId(user.jobId());
            loginInfo.setJobName(user.jobName());
        }
        if (loginInfo.getRoleId() != null) {
            loginInfo.setRoleName(resolveRoleName(loginInfo.getRoleId()));
        }
    }

    // ==================== 模拟信息 ====================

    /** 被模拟用户：按用户id 查人力库补全用户信息 */
    private void fillMockInfo(MonitorMockInfo mockInfo) {
        if (mockInfo == null) {
            return;
        }
        HrUser user = loadHrUser(mockInfo.getUserId());
        if (user != null) {
            mockInfo.setName(user.userName());
            mockInfo.setAvatar(user.avatar());
            mockInfo.setUserNumber(user.userNumber());
            mockInfo.setDeptId(user.deptId());
            mockInfo.setDeptName(user.deptName());
            mockInfo.setJobId(user.jobId());
            mockInfo.setJobName(user.jobName());
        }
    }

    // ==================== 查询 ====================

    /**
     * 按用户id 查人力库 {@code user} 表。
     *
     * <p>部门id、岗位id 在人力库为逗号分隔的多值字符串，日志只记录首个值。
     *
     * @return 用户信息，用户不存在或查询失败返回 {@code null}
     */
    private HrUser loadHrUser(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            Record record =
                    dsl(DataSourceConstants.HR_MANAGE)
                            .select(
                                    DSL.field(DSL.name("user_name"), String.class).as("userName"),
                                    DSL.field(DSL.name("user_avatar"), String.class).as("avatar"),
                                    DSL.field(DSL.name("work_number"), String.class)
                                            .as("userNumber"),
                                    DSL.field(DSL.name("department_id"), String.class).as("deptId"),
                                    DSL.field(DSL.name("department_name"), String.class)
                                            .as("deptName"),
                                    DSL.field(DSL.name("job_id"), String.class).as("jobId"),
                                    DSL.field(DSL.name("job_name"), String.class).as("jobName"))
                            .from(DSL.table(DSL.name(TABLE_HR_USER)))
                            .where(
                                    DSL.field(DSL.name("id"), Long.class)
                                            .eq(userId)
                                            .and(
                                                    DSL.field(DSL.name("is_delete"), Integer.class)
                                                            .eq(1)))
                            .fetchOne();
            if (record == null) {
                log.warn("监控日志用户信息补全失败，人力库无该用户: userId={}", userId);
                return null;
            }
            return new HrUser(
                    record.get("userName", String.class),
                    record.get("avatar", String.class),
                    record.get("userNumber", String.class),
                    firstValueAsLong(record.get("deptId", String.class)),
                    record.get("deptName", String.class),
                    firstValueAsLong(record.get("jobId", String.class)),
                    record.get("jobName", String.class));
        } catch (Exception e) {
            log.warn("监控日志用户信息补全失败: userId={}", userId, e);
            return null;
        }
    }

    /** 按角色id 查配置中心 {@code sys_role} 表取角色名称，查询失败返回 {@code null} */
    private String resolveRoleName(Long roleId) {
        try {
            String roleName =
                    dsl(DataSourceConstants.CONFIG_CENTER)
                            .select(DSL.field(DSL.name("role_name"), String.class))
                            .from(DSL.table(DSL.name(TABLE_SYS_ROLE)))
                            .where(DSL.field(DSL.name("id"), Long.class).eq(roleId))
                            .fetchOne(0, String.class);
            if (roleName == null) {
                log.warn("监控日志角色名称补全失败，配置中心无该角色: roleId={}", roleId);
            }
            return roleName;
        } catch (Exception e) {
            log.warn("监控日志角色名称补全失败: roleId={}", roleId, e);
            return null;
        }
    }

    /** 按数据源名称取 jOOQ 上下文（显式指定数据源，不依赖当前线程的数据源路由） */
    private DSLContext dsl(String dsName) {
        return dataSourceResolver.getDSLContext(dsName);
    }

    /** 逗号分隔的多值字符串取首个值并转 Long，非数字返回 null */
    private Long firstValueAsLong(String value) {
        if (isNotBlank(value)) {
            String first = value.split(",")[0].trim();
            try {
                return Long.valueOf(first);
            } catch (NumberFormatException e) {
                log.warn("监控日志用户信息补全：id 非数字，忽略该值 value={}", value);
            }
        }
        return null;
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /** 人力库用户信息（日志所需字段） */
    private record HrUser(
            String userName,
            String avatar,
            String userNumber,
            Long deptId,
            String deptName,
            Long jobId,
            String jobName) {}
}
