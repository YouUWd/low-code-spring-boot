package com.jdec.platform.config.biz.audit.service;

import cn.hutool.json.JSONUtil;
import com.jdec.platform.config.api.dto.request.RoleSubjectReq;
import com.jdec.platform.config.api.dto.request.RoleUserReq;
import com.jdec.platform.config.api.dto.request.SaveRoleUserReq;
import com.jdec.platform.config.biz.audit.dto.AuditLogSendContext;
import com.jdec.platform.hr.api.bo.SubjectBO;
import com.jdec.platform.hr.api.bo.UserBO;
import com.jdec.platform.shared.enums.EffectiveTypeEnum;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 角色审批日志服务
 *
 * <p>采用编程式实现角色新增/修改/删除的审批日志记录。 修改时直接记录当前数据，不依赖持久化快照。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleApprovalLogService {

    private final AuditLogSendService auditLogSendService;

    private static final String TABLE_NAME = "sys_role";
    private static final String SUB_MODULE = "角色配置";
    private static final String MODULE = "系统设置";

    // ==================== 公开方法 ====================

    /**
     * 记录角色新增的审批日志
     *
     * @param roleId 角色ID
     * @param request 保存请求
     * @param subjectMap 主体名称映射
     * @param hrUserMap 用户信息映射
     */
    public void logRoleCreate(
            Long roleId,
            SaveRoleUserReq request,
            Map<Long, SubjectBO> subjectMap,
            Map<Long, UserBO> hrUserMap) {

        // 1. 构建新增操作的 ModuleAction
        List<Map<String, Object>> moduleActions =
                buildCreateActions(request, subjectMap, hrUserMap);

        // 2. 发送审批日志
        sendApprovalLog("新增", moduleActions);
    }

    /**
     * 记录角色修改的审批日志，直接记录变更后的完整数据
     *
     * @param roleId 角色ID
     * @param request 保存请求
     * @param subjectMap 主体名称映射
     * @param hrUserMap 用户信息映射
     */
    public void logRoleUpdate(
            Long roleId,
            SaveRoleUserReq request,
            Map<Long, SubjectBO> subjectMap,
            Map<Long, UserBO> hrUserMap) {

        // 1. 构建修改后的数据快照
        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(buildColumn("角色名称", "", request.getRoleName(), "roleName"));

        List<String> subjectDisplayNames =
                buildSubjectDisplayNames(request.getSubjectList(), subjectMap);
        columns.add(buildColumn("授权主体", "", subjectDisplayNames, "subject"));

        List<String> userDisplayNames = buildUserDisplayNames(request.getUsers(), hrUserMap);
        columns.add(buildColumn("指定成员", "", userDisplayNames, "userName"));

        // 2. 构建 ModuleAction
        Map<String, Object> recordChange = new LinkedHashMap<>();
        recordChange.put("name", request.getRoleName());
        recordChange.put("columns", columns);

        Map<String, Object> actionDetail = new LinkedHashMap<>();
        actionDetail.put("u", Collections.singletonList(recordChange));

        Map<String, Object> moduleAction = new LinkedHashMap<>();
        moduleAction.put("name", SUB_MODULE);
        moduleAction.put("mId", "");
        moduleAction.put("actions", Collections.singletonList(actionDetail));

        // 3. 发送审批日志
        sendApprovalLog("修改", Collections.singletonList(moduleAction));
    }

    /**
     * 记录角色删除的审批日志
     *
     * @param roleId 角色ID
     * @param roleName 角色名称
     */
    public void logRoleDelete(Long roleId, String roleName) {
        // 构建删除操作的 ModuleAction
        List<Map<String, Object>> moduleActions = buildDeleteActions(roleName);

        // 发送审批日志
        sendApprovalLog("删除", moduleActions);
    }

    // ==================== 构建新增操作 ====================

    /** 构建新增角色的 ModuleAction 列表 */
    private List<Map<String, Object>> buildCreateActions(
            SaveRoleUserReq request, Map<Long, SubjectBO> subjectMap, Map<Long, UserBO> hrUserMap) {

        List<Map<String, Object>> columns = new ArrayList<>();

        // 角色名称
        columns.add(buildColumn("角色名称", "", request.getRoleName(), "roleName"));

        // 授权主体
        List<String> subjectDisplayNames =
                buildSubjectDisplayNames(request.getSubjectList(), subjectMap);
        columns.add(buildColumn("授权主体", "", subjectDisplayNames, "subject"));

        // 指定成员
        List<String> userDisplayNames = buildUserDisplayNames(request.getUsers(), hrUserMap);
        columns.add(buildColumn("指定成员", "", userDisplayNames, "userName"));

        return buildModuleActions("i", "", columns);
    }

    // ==================== 构建删除操作 ====================

    /** 构建删除角色的 ModuleAction 列表 */
    private List<Map<String, Object>> buildDeleteActions(String roleName) {
        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(buildColumn("角色名称", roleName, "", "roleName"));
        return buildModuleActions("d", roleName, columns);
    }

    // ==================== 构建通用 ModuleAction ====================

    /**
     * 构建 ModuleAction 格式的日志内容
     *
     * @param actionType 操作类型：i=新增, u=修改, d=删除
     * @param recordName 记录名称
     * @param columns 字段变更列表
     * @return ModuleAction 列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildModuleActions(
            String actionType, String recordName, List<Map<String, Object>> columns) {

        Map<String, Object> recordChange = new LinkedHashMap<>();
        recordChange.put("name", recordName != null ? recordName : "");
        recordChange.put("columns", columns);

        Map<String, Object> actionDetail = new LinkedHashMap<>();
        actionDetail.put(actionType, Collections.singletonList(recordChange));

        Map<String, Object> moduleAction = new LinkedHashMap<>();
        moduleAction.put("name", SUB_MODULE);
        moduleAction.put("mId", "");
        moduleAction.put("actions", Collections.singletonList(actionDetail));

        return Collections.singletonList(moduleAction);
    }

    /** 构建列变更对象 */
    private Map<String, Object> buildColumn(
            String name, Object oldValue, Object newValue, String field) {
        Map<String, Object> column = new LinkedHashMap<>();
        column.put("name", name);
        column.put("old", oldValue);
        column.put("newer", newValue);
        column.put("field", field);
        return column;
    }

    // ==================== 主体和用户的显示名构建 ====================

    /** 构建主体显示名列表 */
    private List<String> buildSubjectDisplayNames(
            List<RoleSubjectReq> subjectList, Map<Long, SubjectBO> subjectMap) {
        if (subjectList == null) {
            return Collections.emptyList();
        }
        return subjectList.stream()
                .map(
                        s -> {
                            SubjectBO sb = subjectMap.get(s.getSubjectId());
                            String name =
                                    sb != null
                                            ? sb.getSubjectName()
                                            : String.valueOf(s.getSubjectId());
                            return buildSubjectDisplayName(
                                    name,
                                    formatEffectiveTime(
                                            s.getEffectiveType(),
                                            s.getEffectiveStartDate(),
                                            s.getEffectiveEndDate()));
                        })
                .collect(Collectors.toList());
    }

    /** 构建主体显示名（带时效后缀） */
    private String buildSubjectDisplayName(String name, String effectiveTime) {
        if (effectiveTime == null || effectiveTime.isEmpty()) {
            return name;
        }
        return name + "(" + effectiveTime + ")";
    }

    /** 构建用户显示名列表 */
    private List<String> buildUserDisplayNames(
            List<RoleUserReq> userList, Map<Long, UserBO> hrUserMap) {
        if (userList == null) {
            return Collections.emptyList();
        }
        return userList.stream()
                .map(
                        u -> {
                            Long userId = Long.parseLong(u.getUserId());
                            UserBO ub = hrUserMap.get(userId);
                            String name = ub != null ? ub.getUserName() : u.getUserName();
                            return buildUserDisplayName(
                                    name,
                                    formatEffectiveTime(
                                            u.getEffectiveType(),
                                            u.getEffectiveStartDate(),
                                            u.getEffectiveEndDate()));
                        })
                .collect(Collectors.toList());
    }

    /** 构建用户显示名（带时效后缀） */
    private String buildUserDisplayName(String name, String effectiveTime) {
        if (effectiveTime == null || effectiveTime.isEmpty()) {
            return name;
        }
        return name + "(" + effectiveTime + ")";
    }

    // ==================== 格式化时效 ====================

    /**
     * 格式化有效期为可读字符串
     *
     * @param effectiveType 有效期类型：1-永久，2-自定义
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 格式化后的时效字符串
     */
    private String formatEffectiveTime(
            Integer effectiveType, LocalDate startDate, LocalDate endDate) {
        if (effectiveType == null || EffectiveTypeEnum.PERMANENT.getCode().equals(effectiveType)) {
            return "";
        }
        if (startDate != null && endDate != null) {
            return startDate + "至" + endDate;
        } else if (startDate != null) {
            return startDate + "之后";
        } else if (endDate != null) {
            return endDate + "之前";
        }
        return "";
    }

    // ==================== 发送审计日志 ====================

    /**
     * 发送审批日志到监控服务
     *
     * @param operation 操作描述（新增/修改/删除）
     * @param moduleActions ModuleAction 列表
     */
    private void sendApprovalLog(String operation, List<Map<String, Object>> moduleActions) {
        try {
            // 构建 logRemark
            Map<String, Object> remarkMap = new LinkedHashMap<>();
            remarkMap.put("change", moduleActions);
            String remark = JSONUtil.toJsonStr(remarkMap);

            // 构建 controlName（单据操作名称）
            String controlName = operation + "了" + SUB_MODULE;

            // 构建发送上下文，其余信息（用户/角色/主体/菜单/模块/IP等）由 AuditLogSendService 自动补全
            AuditLogSendContext context =
                    AuditLogSendContext.builder()
                            .module(MODULE)
                            .subModule(SUB_MODULE)
                            .controlName(controlName)
                            .logRemark(remark)
                            .build();

            sendAuditLogRequest(context);
        } catch (Exception e) {
            log.error("角色审批日志发送失败: operation={}", operation, e);
        }
    }

    /** 在事务提交前发送审计日志（非阻塞，失败不影响业务事务） */
    private void sendAuditLogRequest(AuditLogSendContext context) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void beforeCommit(boolean readOnly) {
                            auditLogSendService.sendAuditLog(context);
                        }

                        @Override
                        public void afterCompletion(int status) {
                            if (status == STATUS_ROLLED_BACK) {
                                log.info("事务已回滚，跳过角色审批日志");
                            }
                        }
                    });
        } else {
            auditLogSendService.sendAuditLog(context);
        }
    }
}
