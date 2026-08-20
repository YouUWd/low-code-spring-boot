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
        sendApprovalLog("新增", roleId, moduleActions);
    }

    /**
     * 记录角色修改的审批日志，对比旧数据和新数据
     *
     * @param roleId 角色ID
     * @param oldRoleName 旧角色名称
     * @param request 保存请求（新数据）
     * @param oldRoleUsers 旧用户列表
     * @param oldRoleSubjects 旧主体列表
     * @param subjectMap 主体名称映射
     * @param hrUserMap 用户信息映射
     */
    public void logRoleUpdate(
            Long roleId,
            String oldRoleName,
            SaveRoleUserReq request,
            List<RoleUserReq> oldRoleUsers,
            List<RoleSubjectReq> oldRoleSubjects,
            Map<Long, SubjectBO> subjectMap,
            Map<Long, UserBO> hrUserMap) {

        // 1. 构建字段变更列表
        List<Map<String, Object>> columns = new ArrayList<>();

        // 角色名称对比（如果变化）
        if (!oldRoleName.equals(request.getRoleName())) {
            columns.add(buildColumn("角色名称", oldRoleName, request.getRoleName(), "roleName"));
        }

        // 授权主体对比（简单字符串拼接，不对齐）
        List<String> oldSubjectDisplayNames = buildSubjectDisplayNames(oldRoleSubjects, subjectMap);
        List<String> newSubjectDisplayNames =
                buildSubjectDisplayNames(request.getSubjectList(), subjectMap);

        String oldSubjectStr =
                oldSubjectDisplayNames.isEmpty() ? "" : String.join(",", oldSubjectDisplayNames);
        String newSubjectStr =
                newSubjectDisplayNames.isEmpty() ? "" : String.join(",", newSubjectDisplayNames);

        if (!oldSubjectStr.equals(newSubjectStr)) {
            columns.add(buildColumn("授权主体", oldSubjectStr, newSubjectStr, "subject"));
        }

        // 指定成员对比（对齐显示，返回数组格式）
        List<String> oldUserDisplayNames = buildUserDisplayNames(oldRoleUsers, hrUserMap);
        List<String> newUserDisplayNames = buildUserDisplayNames(request.getUsers(), hrUserMap);

        if (!isSameList(oldUserDisplayNames, newUserDisplayNames)) {
            AlignedChangeArray userChange =
                    buildAlignedChangeArray(oldUserDisplayNames, newUserDisplayNames);
            columns.add(buildColumn("指定成员", userChange.oldValue, userChange.newValue, "userName"));
        }

        // 如果没有任何字段变更，不发送日志
        if (columns.isEmpty()) {
            return;
        }

        // 2. 构建 ModuleAction
        Map<String, Object> recordChange = new LinkedHashMap<>();
        recordChange.put("name", request.getRoleName());
        recordChange.put("columns", columns);

        Map<String, Object> actionDetail = new LinkedHashMap<>();
        actionDetail.put("u", Collections.singletonList(recordChange));

        Map<String, Object> moduleAction = new LinkedHashMap<>();
        moduleAction.put("name", SUB_MODULE);
        moduleAction.put("mId", "");
        moduleAction.put("actions", actionDetail);

        // 3. 发送审批日志
        sendApprovalLog("修改", roleId, Collections.singletonList(moduleAction));
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
        sendApprovalLog("删除", roleId, moduleActions);
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
        String subjectDisplayStr = String.join(",", subjectDisplayNames);
        columns.add(buildColumn("授权主体", "", subjectDisplayStr, "subject"));

        // 指定成员（数组格式）
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
        moduleAction.put("actions", actionDetail);

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
     * @param dataId 业务单据主键ID
     * @param moduleActions ModuleAction 列表
     */
    private void sendApprovalLog(
            String operation, Long dataId, List<Map<String, Object>> moduleActions) {
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
                            .dataId(dataId)
                            .controlName(controlName)
                            .logRemark(remark)
                            .build();

            sendAuditLogRequest(context);
        } catch (Exception e) {
            log.error("角色审批日志发送失败: operation={}", operation, e);
        }
    }

    /** 在事务提交成功后发送审计日志（非阻塞，失败不影响业务事务） */
    private void sendAuditLogRequest(AuditLogSendContext context) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
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

    // ==================== 对齐变更辅助方法 ====================

    /** 对齐变更结果（字符串格式） */
    private static class AlignedChange {
        String oldValue;
        String newValue;

        AlignedChange(String oldValue, String newValue) {
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }

    /** 对齐变更结果（数组格式） */
    private static class AlignedChangeArray {
        List<String> oldValue;
        List<String> newValue;

        AlignedChangeArray(List<String> oldValue, List<String> newValue) {
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }

    /** 判断两个列表是否相同 */
    private boolean isSameList(List<String> list1, List<String> list2) {
        if (list1.size() != list2.size()) {
            return false;
        }
        for (int i = 0; i < list1.size(); i++) {
            if (!list1.get(i).equals(list2.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 构建对齐的变更记录（数组格式）
     *
     * <p>示例： 旧：[万勇杰, 邓由(2026-08-17之后)] 新：[邓由(2026-08-17之后), 冯露娜]
     *
     * <p>对齐结果： old: ["万勇杰", "邓由(2026-08-17之后)", "--"] newer: ["--", "邓由(2026-08-17之后)", "冯露娜"]
     *
     * @param oldList 旧列表
     * @param newList 新列表
     * @return 对齐的变更记录（数组格式）
     */
    private AlignedChangeArray buildAlignedChangeArray(List<String> oldList, List<String> newList) {
        // 使用 LinkedHashSet 保持顺序，同时去重
        Set<String> oldSet = new LinkedHashSet<>(oldList);
        Set<String> newSet = new LinkedHashSet<>(newList);

        // 找出被移除的、保持不变的、新增的
        List<String> removed = new ArrayList<>(); // 在旧列表但不在新列表
        List<String> kept = new ArrayList<>(); // 同时在新旧列表
        List<String> added = new ArrayList<>(); // 在新列表但不在旧列表

        // 按旧列表顺序遍历
        for (String item : oldList) {
            if (newSet.contains(item)) {
                if (!kept.contains(item)) {
                    kept.add(item);
                }
            } else {
                if (!removed.contains(item)) {
                    removed.add(item);
                }
            }
        }

        // 按新列表顺序遍历，找出新增项
        for (String item : newList) {
            if (!oldSet.contains(item)) {
                if (!added.contains(item)) {
                    added.add(item);
                }
            }
        }

        // 构建对齐的数组
        List<String> oldAligned = new ArrayList<>();
        List<String> newAligned = new ArrayList<>();

        // 1. 先放被移除的（old有，new用--占位）
        for (String item : removed) {
            oldAligned.add(item);
            newAligned.add("--");
        }

        // 2. 再放保持不变的（old和new都有）
        for (String item : kept) {
            oldAligned.add(item);
            newAligned.add(item);
        }

        // 3. 最后放新增的（old用--占位，new有）
        for (String item : added) {
            oldAligned.add("--");
            newAligned.add(item);
        }

        return new AlignedChangeArray(oldAligned, newAligned);
    }
}
