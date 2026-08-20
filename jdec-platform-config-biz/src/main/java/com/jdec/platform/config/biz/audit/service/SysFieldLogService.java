package com.jdec.platform.config.biz.audit.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.jdec.platform.config.api.dto.request.SysFieldSaveReq;
import com.jdec.platform.config.api.dto.request.SysFieldValueSaveReq;
import com.jdec.platform.config.biz.audit.dto.AuditLogSendContext;
import com.jdec.platform.config.biz.audit.util.JsonDiffUtil;
import com.jdec.platform.config.biz.entity.SysField;
import com.jdec.platform.config.biz.entity.SysFieldValue;
import com.jdec.platform.shared.audit.annotation.AuditField;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 字段配置审计日志服务
 *
 * <p>采用编程式实现字段配置（sys_field）及其子表字段值（sys_field_value）修改的审计日志。 按需求字段配置只有更新操作，不记录新增。
 *
 * <p>审计日志格式（logRemark 中的 change 结构）：
 *
 * <pre>
 * {
 *   "change": [{
 *     "name": "字段配置",
 *     "mId": "",
 *     "actions": {
 *       "u": [{
 *         "name": "学生表(student)-学生性别",    // 记录名称：表中文名(表名)-字段显示名称（取修改前）
 *         "columns": [                         // 只记录发生变化的父表字段
 *           {"name":"字段显示名称","old":"学生性别","newer":"学生性别-001","field":"displayName"}, ...
 *         ],
 *         "children": [{
 *           "name": "字段值关联审批链",
 *           "mid": "",
 *           "actions": {
 *             "i": [{"name":"","columns":[{...old 为空...}]}],       // 新增：无主键 id
 *             "u": [{"name":"","columns":[{...old/newer 对照...}]}], // 更新：有主键 id 且内容有变化
 *             "d": [{"name":"薪资字段维护"}]                          // 删除：旧记录在新请求中缺失
 *           }
 *         }]
 *       }]
 *     }
 *   }]
 * }
 * </pre>
 *
 * <p>需要记录哪些字段由请求对象 {@link SysFieldSaveReq} / {@link SysFieldValueSaveReq} 上的 {@link AuditField}
 * 注解决定（字段声明顺序即日志列顺序），字段值解析复用 {@link JsonDiffUtil#resolveFieldValue}：模块名称 （moduleId）按
 * sys_module.module_name 解析、审批链分类（approvalChainTypeId）按 sys_approval_chain_type.title
 * 解析、加密/权限节点/启用等 0/1 字段按 {@link com.jdec.platform.config.api.enums.YesNoEnum} 映射为 否/是。排序字段
 * sortOrder 未标注，不记录日志。
 *
 * <p>子表（sys_field_value）按主键 id 全量比对：新请求中无 id 的子项记为新增(i)，有 id 且内容有变化记为更新(u)，
 * 修改前数据库中已存在但新请求中缺失的子项记为删除(d)。
 *
 * <p>修改前的基线直接取自数据库实体（sys_field / sys_field_value），无需数据快照。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysFieldLogService {

    private final AuditLogSendService auditLogSendService;
    private final JsonDiffUtil jsonDiffUtil;

    private static final String MODULE = "系统设置";
    private static final String SUB_MODULE = "字段配置";
    private static final String CHILD_SUB_MODULE = "字段值关联审批链";

    /**
     * 记录字段配置修改审计日志
     *
     * <p>字段配置只有更新操作，新增时（oldField 为空）不记录审计日志。
     *
     * @param oldField 修改前的字段配置实体
     * @param tableName 表名
     * @param request 保存请求（新值）
     * @param oldValues 修改前的字段值列表
     */
    public void logUpdate(
            SysField oldField,
            String tableName,
            SysFieldSaveReq request,
            List<SysFieldValue> oldValues) {
        try {
            if (oldField == null) {
                log.debug("字段配置为新增操作，按需求不记录审计日志: columnName={}", request.getColumnName());
                return;
            }

            // 1. 父表变更列（只记录发生变化的字段）
            List<Map<String, Object>> columns = buildParentUpdateColumns(oldField, request);

            // 2. 子表（字段值关联审批链）i/u/d
            List<Map<String, Object>> children = buildChildren(oldValues, request.getFieldValues());

            if (columns.isEmpty() && children == null) {
                log.debug("字段配置未发生变更，跳过审计日志: fieldId={}", oldField.getId());
                return;
            }

            // 3. 构建父记录并发送
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("name", buildRecordName(oldField, tableName, request));
            if (!columns.isEmpty()) {
                record.put("columns", columns);
            }
            if (children != null) {
                record.put("children", children);
            }
            sendFieldLog("修改", oldField.getId(), buildModuleActions("u", record));
        } catch (Exception e) {
            log.error("记录字段配置修改审计日志失败: fieldId={}", oldField != null ? oldField.getId() : null, e);
        }
    }

    // ==================== 父表相关 ====================

    /** 构建父表修改记录的字段列（按 SysFieldSaveReq 上 @AuditField 注解决定，只记录发生变化的字段） */
    private List<Map<String, Object>> buildParentUpdateColumns(
            SysField oldField, SysFieldSaveReq request) {
        List<Map<String, Object>> columns = new ArrayList<>();
        for (FieldMeta meta : getAuditFields(SysFieldSaveReq.class)) {
            Object oldValue = BeanUtil.getProperty(oldField, meta.fieldName());
            Object newValue = BeanUtil.getProperty(request, meta.fieldName());
            addColumnIfChanged(
                    columns,
                    meta.auditName(),
                    oldValue,
                    newValue,
                    meta.fieldName(),
                    SysFieldSaveReq.class);
        }
        return columns;
    }

    /** 仅当字段值变化时追加列（值按 @AuditField 注解解析为展示值） */
    private void addColumnIfChanged(
            List<Map<String, Object>> columns,
            String name,
            Object oldValue,
            Object newValue,
            String field,
            Class<?> clazz) {
        if (!Objects.equals(oldValue, newValue)) {
            columns.add(
                    buildColumn(
                            name,
                            resolveField(field, oldValue, clazz),
                            resolveField(field, newValue, clazz),
                            field));
        }
    }

    /** 构建父记录名称：表中文名(表名)-字段显示名称（取修改前显示名称，其次取修改后） */
    private String buildRecordName(SysField oldField, String tableName, SysFieldSaveReq request) {
        String tableCnName = request.getTableCnName() != null ? request.getTableCnName() : "";
        String displayName =
                oldField.getDisplayName() != null
                        ? oldField.getDisplayName()
                        : (request.getDisplayName() != null ? request.getDisplayName() : "");
        return tableCnName + "(" + (tableName != null ? tableName : "") + ")-" + displayName;
    }

    // ==================== 子表（字段值关联审批链）相关 ====================

    /** 构建子表变更的 children（i/u/d 可并存） */
    private List<Map<String, Object>> buildChildren(
            List<SysFieldValue> oldValues, List<SysFieldValueSaveReq> newValues) {
        List<SysFieldValue> oldList = oldValues != null ? oldValues : Collections.emptyList();
        List<SysFieldValueSaveReq> newList =
                newValues != null ? newValues : Collections.emptyList();

        // 按子表主键 id 匹配新旧子项，用于识别 i/d
        Map<Long, SysFieldValue> oldById =
                oldList.stream()
                        .filter(v -> v.getId() != null)
                        .collect(Collectors.toMap(SysFieldValue::getId, v -> v, (a, b) -> a));
        Set<Long> newIds =
                newList.stream()
                        .map(SysFieldValueSaveReq::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        List<Map<String, Object>> inserts = new ArrayList<>();
        List<Map<String, Object>> updates = new ArrayList<>();
        List<Map<String, Object>> deletes = new ArrayList<>();

        // 新子项（i/u）：
        // - 无主键 id：新增
        // - 有主键 id 且旧子项存在但内容无变化：忽略
        // - 有主键 id 且旧子项存在但内容有变化：视为更新(u)
        // - 有主键 id 但旧列表中不存在：新增
        for (SysFieldValueSaveReq nv : newList) {
            SysFieldValue ov = nv.getId() != null ? oldById.get(nv.getId()) : null;
            if (ov == null) {
                inserts.add(buildValueIRecord(nv));
            } else if (hasValueChanged(ov, nv)) {
                updates.add(buildValueURecord(ov, nv));
            }
        }

        // 旧子项：修改前存在但新请求中缺失 → 删除(d)
        for (SysFieldValue ov : oldList) {
            if (ov.getId() == null || !newIds.contains(ov.getId())) {
                deletes.add(buildValueDRecord(ov));
            }
        }

        if (inserts.isEmpty() && updates.isEmpty() && deletes.isEmpty()) {
            return null;
        }

        List<Map<String, Object>> actionDetails = new ArrayList<>();
        if (!inserts.isEmpty()) {
            actionDetails.add(singletonAction("i", inserts));
        }
        if (!updates.isEmpty()) {
            actionDetails.add(singletonAction("u", updates));
        }
        if (!deletes.isEmpty()) {
            actionDetails.add(singletonAction("d", deletes));
        }
        return buildChildrenObj(actionDetails);
    }

    /** 构建子项新增记录（按 @AuditField 注解决定列，old 为空） */
    private Map<String, Object> buildValueIRecord(SysFieldValueSaveReq value) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", "");
        List<Map<String, Object>> columns = new ArrayList<>();
        for (FieldMeta meta : getAuditFields(SysFieldValueSaveReq.class)) {
            columns.add(
                    buildColumn(
                            meta.auditName(),
                            "",
                            resolveField(
                                    meta.fieldName(),
                                    BeanUtil.getProperty(value, meta.fieldName()),
                                    SysFieldValueSaveReq.class),
                            meta.fieldName()));
        }
        record.put("columns", columns);
        return record;
    }

    /** 构建子项修改记录（按 @AuditField 注解决定列，只记录发生变化的字段，old/newer 对照） */
    private Map<String, Object> buildValueURecord(
            SysFieldValue oldValue, SysFieldValueSaveReq newValue) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", "");
        List<Map<String, Object>> columns = new ArrayList<>();
        for (FieldMeta meta : getAuditFields(SysFieldValueSaveReq.class)) {
            addColumnIfChanged(
                    columns,
                    meta.auditName(),
                    BeanUtil.getProperty(oldValue, meta.fieldName()),
                    BeanUtil.getProperty(newValue, meta.fieldName()),
                    meta.fieldName(),
                    SysFieldValueSaveReq.class);
        }
        record.put("columns", columns);
        return record;
    }

    /** 构建子项删除记录（只保留名称，名称取审批链分类，其次字段值、模块名称） */
    private Map<String, Object> buildValueDRecord(SysFieldValue oldValue) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", buildValueDeleteName(oldValue));
        return record;
    }

    /** 构建子项删除名称 */
    private String buildValueDeleteName(SysFieldValue oldValue) {
        Object chainType =
                resolveField(
                        "approvalChainTypeId",
                        oldValue.getApprovalChainTypeId(),
                        SysFieldValueSaveReq.class);
        if (chainType != null && !chainType.toString().isEmpty()) {
            return chainType.toString();
        }
        if (oldValue.getFieldValue() != null && !oldValue.getFieldValue().isEmpty()) {
            return oldValue.getFieldValue();
        }
        return oldValue.getModuleName() != null ? oldValue.getModuleName() : "";
    }

    /** 判断子项字段是否有变化（按 @AuditField 注解决定，排序字段不参与） */
    private boolean hasValueChanged(SysFieldValue oldValue, SysFieldValueSaveReq newValue) {
        for (FieldMeta meta : getAuditFields(SysFieldValueSaveReq.class)) {
            Object oldV = BeanUtil.getProperty(oldValue, meta.fieldName());
            Object newV = BeanUtil.getProperty(newValue, meta.fieldName());
            if (!Objects.equals(oldV, newV)) {
                return true;
            }
        }
        return false;
    }

    // ==================== children JSON 结构 ====================

    /** 构建 children 列表：[{name,mid,actions}]，actions 为 {i|u|d:[records]} 对象 */
    private List<Map<String, Object>> buildChildrenObj(List<Map<String, Object>> actionDetails) {
        Map<String, Object> actions = new LinkedHashMap<>();
        for (Map<String, Object> action : actionDetails) {
            actions.putAll(action);
        }
        Map<String, Object> moduleAction = new LinkedHashMap<>();
        moduleAction.put("name", CHILD_SUB_MODULE);
        moduleAction.put("mid", "");
        moduleAction.put("actions", actions);
        return Collections.singletonList(moduleAction);
    }

    /** 构建单个操作详情：{"i"|"u"|"d":[records]} */
    private Map<String, Object> singletonAction(
            String actionType, List<Map<String, Object>> records) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put(actionType, records);
        return action;
    }

    // ==================== 通用构建 ====================

    /** 构建顶层 ModuleAction 列表 */
    private List<Map<String, Object>> buildModuleActions(
            String actionType, Map<String, Object> record) {
        Map<String, Object> actionDetail = new LinkedHashMap<>();
        actionDetail.put(actionType, Collections.singletonList(record));

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
        column.put("old", oldValue != null ? oldValue : "");
        column.put("newer", newValue != null ? newValue : "");
        column.put("field", field);
        return column;
    }

    // ==================== 字段值解析 ====================

    /** 按请求对象上的 @AuditField 注解解析展示值（关联/枚举/字典转中文，简单字段原样返回） */
    private Object resolveField(String field, Object value, Class<?> clazz) {
        return jsonDiffUtil.resolveFieldValue(field, value, clazz);
    }

    // ==================== 注解驱动 ====================

    /** 获取请求类上标注了 @AuditField 的字段（按声明顺序，忽略 ignore 字段） */
    private List<FieldMeta> getAuditFields(Class<?> clazz) {
        List<FieldMeta> metas = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            AuditField auditField = field.getAnnotation(AuditField.class);
            if (auditField == null || auditField.ignore()) {
                continue;
            }
            metas.add(new FieldMeta(field.getName(), auditField.name()));
        }
        return metas;
    }

    /** 审计字段元数据 */
    private record FieldMeta(String fieldName, String auditName) {}

    // ==================== 发送审计日志 ====================

    /**
     * 发送审计日志到监控服务
     *
     * @param operation 操作描述（新增/修改/删除）
     * @param dataId 业务单据主键ID
     * @param moduleActions ModuleAction 列表
     */
    private void sendFieldLog(
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
            log.error("字段配置审计日志发送失败: operation={}", operation, e);
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
                                log.info("事务已回滚，跳过字段配置审计日志");
                            }
                        }
                    });
        } else {
            auditLogSendService.sendAuditLog(context);
        }
    }
}
