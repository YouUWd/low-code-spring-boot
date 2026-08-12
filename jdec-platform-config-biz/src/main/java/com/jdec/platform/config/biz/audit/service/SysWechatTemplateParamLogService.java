package com.jdec.platform.config.biz.audit.service;

import cn.hutool.json.JSONUtil;
import com.jdec.platform.config.api.dto.request.CreateWechatTemplateParamReq;
import com.jdec.platform.config.api.dto.request.UpdateWechatTemplateParamReq;
import com.jdec.platform.config.biz.audit.dto.AuditLogSendContext;
import com.jdec.platform.config.biz.entity.SysDataSnapshot;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModuleField;
import com.jdec.platform.config.biz.entity.SysWechatTemplateParam;
import com.jdec.platform.config.biz.mapper.SysModuleFieldMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.service.SysDataSnapshotService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 微信模板参数审计日志服务
 *
 * <p>采用编程式实现微信模板参数（sys_wechat_template_param）新增/修改/删除的审计日志，并维护数据快照。
 *
 * <p>审计日志格式（logRemark 中的 change 结构）：
 *
 * <pre>
 * {
 *   "change": [{
 *     "name": "消息模板参数设置",
 *     "mId": "",
 *     "actions": {
 *       "i|u|d": [{
 *         "name": "参数名称",              // 记录名称（修改时取修改前的参数名称）
 *         "columns": [                    // 新增为全字段（old 为空），修改只记录发生变化的字段
 *           {"name":"参数名称","old":"版本","newer":"版本01","field":"templateParamName"}, ...
 *         ]
 *       }]
 *     }
 *   }]
 * }
 * </pre>
 *
 * <p>字段映射：关联模块（moduleId）按 sys_module.module_name 解析中文名，所选字段（fieldId）按 sys_module_field.display_name
 * 解析（为空时回退 field_code），与 {@code SysWechatTemplateService} 详情装配逻辑保持一致。
 *
 * <p>数据快照以 {@link CreateWechatTemplateParamReq} 序列化的 JSON 保存，修改审计时与快照中保存的历史请求对照，
 * 从而识别字段变更（快照缺失时回退到数据库实体对比）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysWechatTemplateParamLogService {

    private final SysDataSnapshotService snapshotService;
    private final AuditLogSendService auditLogSendService;
    private final SysModuleMapper sysModuleMapper;
    private final SysModuleFieldMapper sysModuleFieldMapper;

    private static final String MODULE = "系统设置";
    private static final String SUB_MODULE = "消息模板参数设置";
    private static final String TABLE_NAME = "sys_wechat_template_param";

    // ==================== 公开方法 ====================

    /** 记录参数新增审计日志并保存快照 */
    public void logCreate(CreateWechatTemplateParamReq request, Long paramId) {
        try {
            // 构建新增记录（所有字段 old 为空）
            ParamValues values = fromRequest(request);
            Map<String, Object> record = buildCreateRecord(values);
            sendTemplateParamLog("新增", paramId, buildModuleActions("i", record));

            // 保存快照
            snapshotService.saveSnapshot(TABLE_NAME, paramId, JSONUtil.toJsonStr(request));
        } catch (Exception e) {
            log.error("记录微信模板参数新增审计日志失败: paramId={}", paramId, e);
        }
    }

    /**
     * 记录参数修改审计日志并更新快照
     *
     * <p>优先以数据快照中保存的历史请求作为修改前基线，快照缺失时回退到数据库实体对比。
     */
    public void logUpdate(
            UpdateWechatTemplateParamReq request, Long paramId, SysWechatTemplateParam oldParam) {
        try {
            CreateWechatTemplateParamReq oldReq = loadSnapshotRequest(paramId);
            ParamValues oldValues = oldReq != null ? fromRequest(oldReq) : fromEntity(oldParam);
            ParamValues newValues = fromRequest(request);

            // 只记录发生变化的字段
            List<Map<String, Object>> columns = buildUpdateColumns(oldValues, newValues);
            if (columns.isEmpty()) {
                log.debug("微信模板参数未发生变更，跳过审计日志: paramId={}", paramId);
                return;
            }

            // 构建修改记录（记录名称取修改前的参数名称）
            Map<String, Object> record = new LinkedHashMap<>();
            record.put(
                    "name",
                    buildRecordName(oldValues.templateParamName(), newValues.templateParamName()));
            record.put("columns", columns);
            sendTemplateParamLog("修改", paramId, buildModuleActions("u", record));

            // 更新快照
            snapshotService.saveOrUpdateSnapshot(TABLE_NAME, paramId, JSONUtil.toJsonStr(request));
        } catch (Exception e) {
            log.error("记录微信模板参数修改审计日志失败: paramId={}", paramId, e);
        }
    }

    /** 记录参数删除审计日志并删除快照 */
    public void logDelete(SysWechatTemplateParam deletedParam) {
        try {
            if (deletedParam == null) {
                return;
            }

            // 删除记录只保留参数名称
            Map<String, Object> record = new LinkedHashMap<>();
            record.put(
                    "name",
                    deletedParam.getTemplateParamName() != null
                            ? deletedParam.getTemplateParamName()
                            : "");
            sendTemplateParamLog("删除", deletedParam.getId(), buildModuleActions("d", record));

            // 删除快照
            snapshotService.deleteSnapshot(TABLE_NAME, deletedParam.getId());
        } catch (Exception e) {
            log.error(
                    "记录微信模板参数删除审计日志失败: paramId={}",
                    deletedParam != null ? deletedParam.getId() : null,
                    e);
        }
    }

    // ==================== 记录构建 ====================

    /** 构建新增记录的字段列（old 为空） */
    private Map<String, Object> buildCreateRecord(ParamValues values) {
        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(buildColumn("参数名称", "", values.templateParamName(), "templateParamName"));
        columns.add(buildColumn("参数标识", "", values.templateParamSlug(), "templateParamSlug"));
        columns.add(buildColumn("描述", "", values.templateParamDesc(), "templateParamDesc"));
        columns.add(buildColumn("关联模块", "", resolveModuleName(values.moduleId()), "moduleId"));
        columns.add(buildColumn("所选字段", "", resolveFieldName(values.fieldId()), "fieldId"));
        columns.add(
                buildColumn(
                        "转换方法",
                        "",
                        values.templateParamConvertMethod(),
                        "templateParamConvertMethod"));

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", values.templateParamName() != null ? values.templateParamName() : "");
        record.put("columns", columns);
        return record;
    }

    /** 构建修改记录的字段列（只记录发生变化的字段） */
    private List<Map<String, Object>> buildUpdateColumns(
            ParamValues oldValues, ParamValues newValues) {
        List<Map<String, Object>> columns = new ArrayList<>();
        addColumnIfChanged(
                columns,
                "参数名称",
                oldValues.templateParamName(),
                newValues.templateParamName(),
                "templateParamName");
        addColumnIfChanged(
                columns,
                "参数标识",
                oldValues.templateParamSlug(),
                newValues.templateParamSlug(),
                "templateParamSlug");
        addColumnIfChanged(
                columns,
                "描述",
                oldValues.templateParamDesc(),
                newValues.templateParamDesc(),
                "templateParamDesc");
        addColumnIfChanged(columns, "关联模块", oldValues.moduleId(), newValues.moduleId(), "moduleId");
        addColumnIfChanged(columns, "所选字段", oldValues.fieldId(), newValues.fieldId(), "fieldId");
        addColumnIfChanged(
                columns,
                "转换方法",
                oldValues.templateParamConvertMethod(),
                newValues.templateParamConvertMethod(),
                "templateParamConvertMethod");
        return columns;
    }

    /** 仅当字段值变化时追加列（值按字段映射解析为中文名） */
    private void addColumnIfChanged(
            List<Map<String, Object>> columns,
            String name,
            Object oldValue,
            Object newValue,
            String field) {
        if (!Objects.equals(oldValue, newValue)) {
            columns.add(
                    buildColumn(
                            name,
                            resolveField(field, oldValue),
                            resolveField(field, newValue),
                            field));
        }
    }

    // ==================== 字段值解析 ====================

    /** 按字段映射解析展示值（关联模块/所选字段转中文名） */
    private Object resolveField(String field, Object value) {
        if (value == null) {
            return null;
        }
        return switch (field) {
            case "moduleId" -> resolveModuleName((Long) value);
            case "fieldId" -> resolveFieldName((Long) value);
            default -> value;
        };
    }

    /** 解析模块名称 */
    private Object resolveModuleName(Long moduleId) {
        if (moduleId == null) {
            return null;
        }
        try {
            SysModule module = sysModuleMapper.selectById(moduleId);
            return module != null && module.getModuleName() != null
                    ? module.getModuleName()
                    : moduleId.toString();
        } catch (Exception e) {
            log.warn("解析模块名称失败: moduleId={}", moduleId, e);
            return moduleId.toString();
        }
    }

    /** 解析字段名称（display_name 为空时回退 field_code） */
    private Object resolveFieldName(Long fieldId) {
        if (fieldId == null) {
            return null;
        }
        try {
            SysModuleField field = sysModuleFieldMapper.selectById(fieldId);
            if (field == null) {
                return fieldId.toString();
            }
            return field.getDisplayName() != null ? field.getDisplayName() : field.getFieldCode();
        } catch (Exception e) {
            log.warn("解析字段名称失败: fieldId={}", fieldId, e);
            return fieldId.toString();
        }
    }

    // ==================== 通用构建 ====================

    /** 构建记录名称（修改时优先取修改前名称，其次取修改后名称） */
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
    private CreateWechatTemplateParamReq loadSnapshotRequest(Long paramId) {
        SysDataSnapshot snapshot = snapshotService.getLatestSnapshot(TABLE_NAME, paramId);
        if (snapshot == null
                || snapshot.getJsonData() == null
                || snapshot.getJsonData().isEmpty()) {
            return null;
        }
        try {
            return JSONUtil.toBean(snapshot.getJsonData(), CreateWechatTemplateParamReq.class);
        } catch (Exception e) {
            log.warn("微信模板参数快照反序列化失败，回退数据库实体对比: paramId={}", paramId, e);
            return null;
        }
    }

    // ==================== 值对象 ====================

    /** 审计字段值（不含 convertFlag） */
    private record ParamValues(
            String templateParamName,
            String templateParamSlug,
            String templateParamDesc,
            Long moduleId,
            Long fieldId,
            String templateParamConvertMethod) {}

    /** 从请求构建审计字段值 */
    private ParamValues fromRequest(CreateWechatTemplateParamReq request) {
        return new ParamValues(
                request.getTemplateParamName(),
                request.getTemplateParamSlug(),
                request.getTemplateParamDesc(),
                request.getModuleId(),
                request.getFieldId(),
                request.getTemplateParamConvertMethod());
    }

    /** 从数据库实体构建审计字段值 */
    private ParamValues fromEntity(SysWechatTemplateParam entity) {
        if (entity == null) {
            return new ParamValues(null, null, null, null, null, null);
        }
        return new ParamValues(
                entity.getTemplateParamName(),
                entity.getTemplateParamSlug(),
                entity.getTemplateParamDesc(),
                entity.getModuleId(),
                entity.getFieldId(),
                entity.getTemplateParamConvertMethod());
    }

    // ==================== 发送审计日志 ====================

    /**
     * 发送审计日志到监控服务
     *
     * @param operation 操作描述（新增/修改/删除）
     * @param dataId 业务单据主键ID
     * @param moduleActions ModuleAction 列表
     */
    private void sendTemplateParamLog(
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
            log.error("微信模板参数审计日志发送失败: operation={}", operation, e);
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
                                log.info("事务已回滚，跳过微信模板参数审计日志");
                            }
                        }
                    });
        } else {
            auditLogSendService.sendAuditLog(context);
        }
    }
}
