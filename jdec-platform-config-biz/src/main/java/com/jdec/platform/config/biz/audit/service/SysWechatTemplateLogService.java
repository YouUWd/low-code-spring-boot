package com.jdec.platform.config.biz.audit.service;

import cn.hutool.json.JSONUtil;
import com.jdec.platform.config.api.dto.request.CreateWechatTemplateReq;
import com.jdec.platform.config.api.dto.request.UpdateWechatTemplateReq;
import com.jdec.platform.config.biz.audit.dto.AuditLogSendContext;
import com.jdec.platform.config.biz.entity.SysDataSnapshot;
import com.jdec.platform.config.biz.entity.SysWechatTemplate;
import com.jdec.platform.config.biz.service.SysDataSnapshotService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 微信消息模板审计日志服务
 *
 * <p>采用编程式实现微信消息模板（sys_wechat_template）新增/修改/删除的审计日志，并维护数据快照。
 *
 * <p>审计日志格式（logRemark 中的 change 结构）：
 *
 * <pre>
 * {
 *   "change": [{
 *     "name": "消息配置",
 *     "mId": "",
 *     "actions": {
 *       "i|u|d": [{
 *         "name": "模板标题",              // 记录名称（修改时取修改前的模板标题）
 *         "columns": [                    // 新增为全字段（old 为空），修改只记录发生变化的字段
 *           {"name":"消息类型","old":"","newer":"外部登录验证码模板","field":"templateTitle"}, ...
 *         ]
 *       }]
 *     }
 *   }]
 * }
 * </pre>
 *
 * <p>字段映射：消息发送类型（templateSendType）按字典 wxSendType、触发类型（templateTriggerType）按字典
 * wxTriggerType、消息接收人（templateAcceptType）按字典 wxAcceptType 解析为中文label；templateAcceptType 为
 * 集合（配置ID数组），映射到日志时以逗号拼接的中文label字符串展示。
 *
 * <p>数据快照以 {@link CreateWechatTemplateReq} 序列化的 JSON 保存，修改审计时与快照中保存的历史请求对照，
 * 从而识别字段变更（快照缺失时回退到数据库实体对比）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysWechatTemplateLogService {

    private final SysDataSnapshotService snapshotService;
    private final AuditLogSendService auditLogSendService;
    private final DictFieldQueryService dictFieldQueryService;

    private static final String MODULE = "系统设置";
    private static final String SUB_MODULE = "消息配置";
    private static final String TABLE_NAME = "sys_wechat_template";

    /** 通用配置字典别名：消息发送类型 */
    private static final String WX_SEND_TYPE = "wxSendType";

    /** 通用配置字典别名：触发类型 */
    private static final String WX_TRIGGER_TYPE = "wxTriggerType";

    /** 通用配置字典别名：消息接收人 */
    private static final String WX_ACCEPT_TYPE = "wxAcceptType";

    // ==================== 公开方法 ====================

    /** 记录模板新增审计日志并保存快照 */
    public void logCreate(CreateWechatTemplateReq request, Long templateId) {
        try {
            // 构建新增记录（所有字段 old 为空）
            TemplateValues values = fromRequest(request);
            Map<String, Object> record = buildCreateRecord(values);
            sendTemplateLog("新增", templateId, buildModuleActions("i", record));

            // 保存快照
            snapshotService.saveSnapshot(TABLE_NAME, templateId, JSONUtil.toJsonStr(request));
        } catch (Exception e) {
            log.error("记录微信消息模板新增审计日志失败: templateId={}", templateId, e);
        }
    }

    /**
     * 记录模板修改审计日志并更新快照
     *
     * <p>优先以数据快照中保存的历史请求作为修改前基线，快照缺失时回退到数据库实体对比。
     */
    public void logUpdate(
            UpdateWechatTemplateReq request, Long templateId, SysWechatTemplate oldTemplate) {
        try {
            CreateWechatTemplateReq oldReq = loadSnapshotRequest(templateId);
            TemplateValues oldValues =
                    oldReq != null ? fromRequest(oldReq) : fromEntity(oldTemplate);
            TemplateValues newValues = fromRequest(request);

            // 只记录发生变化的字段
            List<Map<String, Object>> columns = buildUpdateColumns(oldValues, newValues);
            if (columns.isEmpty()) {
                log.debug("微信消息模板未发生变更，跳过审计日志: templateId={}", templateId);
                return;
            }

            // 构建修改记录（记录名称取修改前的模板标题）
            Map<String, Object> record = new LinkedHashMap<>();
            record.put(
                    "name", buildRecordName(oldValues.templateTitle(), newValues.templateTitle()));
            record.put("columns", columns);
            sendTemplateLog("修改", templateId, buildModuleActions("u", record));

            // 更新快照
            snapshotService.saveOrUpdateSnapshot(
                    TABLE_NAME, templateId, JSONUtil.toJsonStr(request));
        } catch (Exception e) {
            log.error("记录微信消息模板修改审计日志失败: templateId={}", templateId, e);
        }
    }

    /** 记录模板删除审计日志并删除快照 */
    public void logDelete(SysWechatTemplate deletedTemplate) {
        try {
            if (deletedTemplate == null) {
                return;
            }

            // 删除记录只保留模板标题
            Map<String, Object> record = new LinkedHashMap<>();
            record.put(
                    "name",
                    deletedTemplate.getTemplateTitle() != null
                            ? deletedTemplate.getTemplateTitle()
                            : "");
            sendTemplateLog("删除", deletedTemplate.getId(), buildModuleActions("d", record));

            // 删除快照
            snapshotService.deleteSnapshot(TABLE_NAME, deletedTemplate.getId());
        } catch (Exception e) {
            log.error(
                    "记录微信消息模板删除审计日志失败: templateId={}",
                    deletedTemplate != null ? deletedTemplate.getId() : null,
                    e);
        }
    }

    // ==================== 记录构建 ====================

    /** 构建新增记录的字段列（old 为空） */
    private Map<String, Object> buildCreateRecord(TemplateValues values) {
        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(buildColumn("消息类型", "", values.templateTitle(), "templateTitle"));
        columns.add(
                buildColumn(
                        "消息发送类型",
                        "",
                        resolveDictLabel(WX_SEND_TYPE, values.templateSendType()),
                        "templateSendType"));
        columns.add(
                buildColumn(
                        "触发类型",
                        "",
                        resolveDictLabel(WX_TRIGGER_TYPE, values.templateTriggerType()),
                        "templateTriggerType"));
        columns.add(
                buildColumn(
                        "消息接收人",
                        "",
                        resolveDictLabel(WX_ACCEPT_TYPE, values.templateAcceptType()),
                        "templateAcceptType"));
        columns.add(buildColumn("消息模板", "", values.templateContent(), "templateContent"));
        columns.add(buildColumn("是否审批链使用", "", values.approvalFlag(), "approvalFlag"));

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", values.templateTitle() != null ? values.templateTitle() : "");
        record.put("columns", columns);
        return record;
    }

    /** 构建修改记录的字段列（只记录发生变化的字段） */
    private List<Map<String, Object>> buildUpdateColumns(
            TemplateValues oldValues, TemplateValues newValues) {
        List<Map<String, Object>> columns = new ArrayList<>();
        addColumnIfChanged(
                columns,
                "消息类型",
                oldValues.templateTitle(),
                newValues.templateTitle(),
                "templateTitle");
        addColumnIfChanged(
                columns,
                "消息发送类型",
                oldValues.templateSendType(),
                newValues.templateSendType(),
                "templateSendType");
        addColumnIfChanged(
                columns,
                "触发类型",
                oldValues.templateTriggerType(),
                newValues.templateTriggerType(),
                "templateTriggerType");
        addColumnIfChanged(
                columns,
                "消息接收人",
                oldValues.templateAcceptType(),
                newValues.templateAcceptType(),
                "templateAcceptType");
        addColumnIfChanged(
                columns,
                "消息模板",
                oldValues.templateContent(),
                newValues.templateContent(),
                "templateContent");
        addColumnIfChanged(
                columns,
                "是否审批链使用",
                oldValues.approvalFlag(),
                newValues.approvalFlag(),
                "approvalFlag");
        return columns;
    }

    /** 仅当字段值变化时追加列（值按字段映射解析为展示值） */
    private void addColumnIfChanged(
            List<Map<String, Object>> columns,
            String name,
            Object oldValue,
            Object newValue,
            String field) {
        if (isValueChanged(oldValue, newValue)) {
            columns.add(
                    buildColumn(
                            name,
                            resolveField(field, oldValue),
                            resolveField(field, newValue),
                            field));
        }
    }

    /** 判断值是否发生变化（空集合与 null 视为相同） */
    private boolean isValueChanged(Object oldValue, Object newValue) {
        return !Objects.equals(normalizeEmpty(oldValue), normalizeEmpty(newValue));
    }

    /** 空集合归一化为 null，避免空集合与 null 之间产生无意义差异 */
    private Object normalizeEmpty(Object value) {
        if (value instanceof Collection<?> collection && collection.isEmpty()) {
            return null;
        }
        return value;
    }

    // ==================== 字段值解析 ====================

    /** 按字段映射解析展示值（发送类型/触发类型/接收人转字典中文label） */
    private Object resolveField(String field, Object value) {
        if (value == null) {
            return null;
        }
        return switch (field) {
            case "templateSendType" -> resolveDictLabel(WX_SEND_TYPE, value);
            case "templateTriggerType" -> resolveDictLabel(WX_TRIGGER_TYPE, value);
            case "templateAcceptType" -> resolveDictLabel(WX_ACCEPT_TYPE, value);
            default -> value;
        };
    }

    /**
     * 将字典配置ID（单个或集合）解析为中文label，集合以逗号拼接
     *
     * @param categoryAlias 字典分类别名
     * @param value 配置ID或配置ID集合
     * @return 中文label（集合为逗号拼接），未匹配到字典时回退为原始值
     */
    private Object resolveDictLabel(String categoryAlias, Object value) {
        if (value == null) {
            return null;
        }
        List<String> values = toStringList(value);
        if (values.isEmpty()) {
            return "";
        }
        try {
            Map<String, String> labelMap =
                    dictFieldQueryService.queryLabelsByValues(categoryAlias, values, null);
            if (values.size() == 1) {
                return labelMap.getOrDefault(values.get(0), values.get(0));
            }
            return values.stream()
                    .map(v -> labelMap.getOrDefault(v, v))
                    .collect(Collectors.joining(","));
        } catch (Exception e) {
            log.warn("解析字典label失败: categoryAlias={}, value={}", categoryAlias, value, e);
            return String.join(",", values);
        }
    }

    /** 将值转换为字符串列表（支持单个值与集合） */
    private List<String> toStringList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return Collections.singletonList(value.toString());
    }

    // ==================== 通用构建 ====================

    /** 构建记录名称（修改时优先取修改前标题，其次取修改后标题） */
    private String buildRecordName(String oldName, String newName) {
        if (oldName != null && !oldName.isEmpty()) {
            return oldName;
        }
        return newName != null ? newName : "";
    }

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

    // ==================== 快照 ====================

    /** 从快照加载修改前的历史请求，快照缺失或解析失败时返回null */
    private CreateWechatTemplateReq loadSnapshotRequest(Long templateId) {
        SysDataSnapshot snapshot = snapshotService.getLatestSnapshot(TABLE_NAME, templateId);
        if (snapshot == null
                || snapshot.getJsonData() == null
                || snapshot.getJsonData().isEmpty()) {
            return null;
        }
        try {
            return JSONUtil.toBean(snapshot.getJsonData(), CreateWechatTemplateReq.class);
        } catch (Exception e) {
            log.warn("微信消息模板快照反序列化失败，回退数据库实体对比: templateId={}", templateId, e);
            return null;
        }
    }

    // ==================== 值对象 ====================

    /** 审计字段值 */
    private record TemplateValues(
            String templateTitle,
            Long templateSendType,
            List<Long> templateAcceptType,
            Long templateTriggerType,
            String templateContent,
            Integer approvalFlag) {}

    /** 从请求构建审计字段值 */
    private TemplateValues fromRequest(CreateWechatTemplateReq request) {
        return new TemplateValues(
                request.getTemplateTitle(),
                request.getTemplateSendType(),
                request.getTemplateAcceptType(),
                request.getTemplateTriggerType(),
                request.getTemplateContent(),
                request.getApprovalFlag());
    }

    /** 从数据库实体构建审计字段值（接收人为JSON数组字符串，需解析） */
    private TemplateValues fromEntity(SysWechatTemplate entity) {
        if (entity == null) {
            return new TemplateValues(null, null, null, null, null, null);
        }
        return new TemplateValues(
                entity.getTemplateTitle(),
                entity.getTemplateSendType(),
                parseLongList(entity.getTemplateAcceptType()),
                entity.getTemplateTriggerType(),
                entity.getTemplateContent(),
                entity.getApprovalFlag());
    }

    /** 解析接收人JSON数组字符串为ID列表 */
    private List<Long> parseLongList(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return null;
        }
        try {
            return JSONUtil.toList(json, Long.class);
        } catch (Exception e) {
            log.warn("解析消息接收人JSON失败: json={}", json, e);
            return null;
        }
    }

    // ==================== 发送审计日志 ====================

    /**
     * 发送审计日志到监控服务
     *
     * @param operation 操作描述（新增/修改/删除）
     * @param dataId 业务单据主键ID
     * @param moduleActions ModuleAction 列表
     */
    private void sendTemplateLog(
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
            log.error("微信消息模板审计日志发送失败: operation={}", operation, e);
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
                                log.info("事务已回滚，跳过微信消息模板审计日志");
                            }
                        }
                    });
        } else {
            auditLogSendService.sendAuditLog(context);
        }
    }
}
