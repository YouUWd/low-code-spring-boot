package com.jdec.platform.config.biz.audit.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.jdec.platform.config.api.dto.request.ApprovalChainConfigSaveReq;
import com.jdec.platform.config.api.dto.request.ApprovalChainConfigSaveReq.ButtonConfig;
import com.jdec.platform.config.biz.audit.dto.AuditLogSendContext;
import com.jdec.platform.config.biz.audit.util.JsonDiffUtil;
import com.jdec.platform.config.biz.entity.SysApprovalChainConfig;
import com.jdec.platform.config.biz.entity.SysApprovalChainConfigButton;
import com.jdec.platform.config.biz.entity.SysDataSnapshot;
import com.jdec.platform.config.biz.service.SysDataSnapshotService;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 审批链配置审计日志服务
 *
 * <p>采用编程式实现审批链步骤（sys_approval_chain_config）新增/修改/删除的审计日志，并维护数据快照。
 *
 * <p>审计日志格式（logRemark 中的 change 结构）：
 *
 * <pre>
 * {
 *   "change": [{
 *     "name": "审批链步骤",
 *     "mId": "",
 *     "actions": {
 *       "i|u|d": [{
 *         "name": "审批链分类 01-02",          // 审批链分类 + 上一步 + 下一步拼接，数字10以内补0
 *         "columns": [                        // 模块名称/审批链分类/审批规则类型
 *           {"name":"模块名称","old":"","newer":"班级详情","field":"moduleId"}, ...
 *         ],
 *         "children": [                       // 按钮配置（List&lt;ButtonConfig&gt;，一个按钮一条记录）
 *           {
 *             "name": "审批操作配置",
 *             "mId": "",
 *             // 更新时按钮按 id 全量比对：无 id 或旧列表中不存在记为新增(i)，有 id 且内容有变化记为更新(u)，快照中存在而新操作中缺失记为删除(d)
 *             "actions": {"i": [...], "u": [...], "d": [...]}
 *           }
 *         ]
 *       }]
 *     }
 *   }]
 * }
 * </pre>
 *
 * <p>按钮字段的值解析复用 {@link JsonDiffUtil#resolveFieldValue(String, Object, Class)}，读取 {@link
 * ApprovalChainConfigSaveReq.ButtonConfig} 上的 {@code @AuditField} 注解：操作类型按 sys_config_item 关联展示
 * label，按钮样式按 sys_button 关联并将整对象按 {@code ButtonStyleDTO} 类型序列化，消息类型按 sys_wechat_template
 * 关联并转中文描述（逗号分隔ID）。
 *
 * <p>数据快照以 {@link ApprovalChainConfigSaveReq} 整体序列化为 JSON 保存，修改审计时与快照中保存的历史请求对照，
 * 从而识别步骤字段与按钮配置的变更（快照缺失时回退到数据库实体对比）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysApprovalChainLogService {

    private final JsonDiffUtil jsonDiffUtil;
    private final SysDataSnapshotService snapshotService;
    private final AuditLogSendService auditLogSendService;

    private static final String SUB_MODULE = "审批链步骤";
    private static final String BUTTON_SUB_MODULE = "审批操作配置";
    private static final String TABLE_NAME = "sys_approval_chain_config";
    private static final String MODULE = "系统设置";

    // ==================== 公开方法 ====================

    /**
     * 记录审批链步骤新增审计日志并保存快照
     *
     * @param request 保存请求
     * @param configId 步骤ID
     * @param newButtons 本次插入的按钮配置列表（数据库生成的ID会回写到快照）
     */
    public void logCreate(
            ApprovalChainConfigSaveReq request,
            Long configId,
            List<SysApprovalChainConfigButton> newButtons) {
        try {
            // 1. 构建步骤列（新增，old 为空）
            List<Map<String, Object>> stepColumns = buildStepColumnsCreate(request);

            // 2. 构建按钮配置 children
            List<Map<String, Object>> children = buildChildrenCreate(request);

            // 3. 步骤名称 = 审批链分类 + 上一步 + 下一步
            String stepName = buildStepName(request);

            // 4. 构建步骤记录并发送
            Map<String, Object> stepRecord = buildStepRecord(stepName, stepColumns, children);
            sendApprovalLog("新增", configId, buildModuleActions("i", stepRecord));

            // 5. 保存快照（完整保存 ApprovalChainConfigSaveReq）
            saveSnapshot(configId, request, newButtons);
        } catch (Exception e) {
            log.error("记录审批链配置新增审计日志失败: configId={}", configId, e);
        }
    }

    /**
     * 记录审批链步骤修改审计日志并更新快照
     *
     * <p>优先以数据快照中保存的历史 {@link ApprovalChainConfigSaveReq} 作为修改前基线进行diff，快照缺失时回退到 数据库实体对比。
     *
     * @param request 保存请求（新值）
     * @param configId 步骤ID
     * @param oldConfig 修改前的步骤配置
     * @param oldButtons 修改前的按钮配置列表
     * @param newButtons 本次保存后重新插入的按钮配置列表（数据库生成的ID会回写到快照）
     */
    public void logUpdate(
            ApprovalChainConfigSaveReq request,
            Long configId,
            SysApprovalChainConfig oldConfig,
            List<SysApprovalChainConfigButton> oldButtons,
            List<SysApprovalChainConfigButton> newButtons) {
        try {
            if (oldConfig == null) {
                logCreate(request, configId, newButtons);
                return;
            }

            ApprovalChainConfigSaveReq oldReq = loadSnapshotRequest(configId);

            // 1. 构建步骤列（只记录发生变化的字段）
            List<Map<String, Object>> stepColumns =
                    oldReq != null
                            ? buildStepColumnsUpdate(oldReq, request)
                            : buildStepColumnsUpdate(request, oldConfig);

            // 2. 构建按钮配置 children（可能出现 i/u/d 并存）
            List<Map<String, Object>> children =
                    oldReq != null
                            ? buildChildrenUpdate(
                                    oldReq.getButtonConfigs(), request.getButtonConfigs())
                            : buildChildrenUpdate(request, oldButtons);

            // 3. 步骤名称（取修改后的值）
            String stepName = buildStepName(request);

            // 4. 构建步骤记录并发送
            Map<String, Object> stepRecord = buildStepRecord(stepName, stepColumns, children);
            sendApprovalLog("修改", configId, buildModuleActions("u", stepRecord));

            // 5. 更新快照（完整保存 ApprovalChainConfigSaveReq）
            snapshotService.saveOrUpdateSnapshot(
                    TABLE_NAME,
                    configId,
                    JSONUtil.toJsonStr(buildSnapshotRequest(request, newButtons)));
        } catch (Exception e) {
            log.error("记录审批链配置修改审计日志失败: configId={}", configId, e);
        }
    }

    /** 记录审批链步骤删除审计日志并删除快照 */
    public void logDelete(SysApprovalChainConfig deletedConfig) {
        try {
            if (deletedConfig == null) {
                return;
            }
            // 删除记录只保留步骤名称
            Object chainType =
                    resolveStepField("approvalChainTypeId", deletedConfig.getApprovalChainTypeId());
            String stepName =
                    buildStepName(
                            chainType != null ? chainType.toString() : "",
                            deletedConfig.getCurrentStep(),
                            deletedConfig.getNextStep());

            Map<String, Object> stepRecord = new LinkedHashMap<>();
            stepRecord.put("name", stepName);
            sendApprovalLog("删除", deletedConfig.getId(), buildModuleActions("d", stepRecord));

            // 删除快照
            snapshotService.deleteSnapshot(TABLE_NAME, deletedConfig.getId());
        } catch (Exception e) {
            log.error(
                    "记录审批链配置删除审计日志失败: configId={}",
                    deletedConfig != null ? deletedConfig.getId() : null,
                    e);
        }
    }

    // ==================== 步骤相关 ====================

    /** 构建新增步骤的列（old 为空） */
    private List<Map<String, Object>> buildStepColumnsCreate(ApprovalChainConfigSaveReq request) {
        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(
                buildColumn(
                        "模块名称",
                        "",
                        resolveStepField("moduleId", request.getModuleId()),
                        "moduleId"));
        columns.add(
                buildColumn(
                        "审批链分类",
                        "",
                        resolveStepField("approvalChainTypeId", request.getApprovalChainTypeId()),
                        "approvalChainTypeId"));
        columns.add(
                buildColumn(
                        "审批规则类型",
                        "",
                        resolveStepField("approvalRule", request.getApprovalRule()),
                        "approvalRule"));
        return columns;
    }

    /** 构建修改步骤的列（基于快照中的历史请求与新请求对比，只记录发生变化的字段） */
    private List<Map<String, Object>> buildStepColumnsUpdate(
            ApprovalChainConfigSaveReq oldReq, ApprovalChainConfigSaveReq newReq) {
        List<Map<String, Object>> columns = new ArrayList<>();
        if (!Objects.equals(oldReq.getModuleId(), newReq.getModuleId())) {
            columns.add(
                    buildColumn(
                            "模块名称",
                            resolveStepField("moduleId", oldReq.getModuleId()),
                            resolveStepField("moduleId", newReq.getModuleId()),
                            "moduleId"));
        }
        if (!Objects.equals(oldReq.getApprovalChainTypeId(), newReq.getApprovalChainTypeId())) {
            columns.add(
                    buildColumn(
                            "审批链分类",
                            resolveStepField(
                                    "approvalChainTypeId", oldReq.getApprovalChainTypeId()),
                            resolveStepField(
                                    "approvalChainTypeId", newReq.getApprovalChainTypeId()),
                            "approvalChainTypeId"));
        }
        if (!Objects.equals(oldReq.getApprovalRule(), newReq.getApprovalRule())) {
            columns.add(
                    buildColumn(
                            "审批规则类型",
                            resolveStepField("approvalRule", oldReq.getApprovalRule()),
                            resolveStepField("approvalRule", newReq.getApprovalRule()),
                            "approvalRule"));
        }
        return columns;
    }

    /** 构建修改步骤的列（快照缺失时回退：基于数据库实体对比） */
    private List<Map<String, Object>> buildStepColumnsUpdate(
            ApprovalChainConfigSaveReq request, SysApprovalChainConfig oldConfig) {
        ApprovalChainConfigSaveReq oldReq = new ApprovalChainConfigSaveReq();
        BeanUtil.copyProperties(oldConfig, oldReq);
        return buildStepColumnsUpdate(oldReq, request);
    }

    /** 构建步骤名称：审批链分类 + 当前步骤 + 下一步（10以内数字补0） */
    private String buildStepName(ApprovalChainConfigSaveReq request) {
        Object chainType =
                resolveStepField("approvalChainTypeId", request.getApprovalChainTypeId());
        return buildStepName(
                chainType != null ? chainType.toString() : "",
                request.getCurrentStep(),
                request.getNextStep());
    }

    private String buildStepName(String chainTypeTitle, Integer currentStep, Integer nextStep) {
        return (chainTypeTitle != null ? chainTypeTitle : "")
                + " "
                + padStep(currentStep)
                + "-"
                + padStep(nextStep);
    }

    /** 步骤数字补0，10以内前置补0 */
    private String padStep(Integer step) {
        if (step == null) {
            return "00";
        }
        return String.format("%02d", step);
    }

    /** 构建步骤记录 */
    private Map<String, Object> buildStepRecord(
            String stepName,
            List<Map<String, Object>> stepColumns,
            List<Map<String, Object>> children) {
        Map<String, Object> stepRecord = new LinkedHashMap<>();
        stepRecord.put("name", stepName);
        if (!stepColumns.isEmpty()) {
            stepRecord.put("columns", stepColumns);
        }
        if (children != null) {
            stepRecord.put("children", children);
        }
        return stepRecord;
    }

    // ==================== 按钮配置 children ====================

    /** 构建新增的按钮配置 children */
    private List<Map<String, Object>> buildChildrenCreate(ApprovalChainConfigSaveReq request) {
        List<ButtonConfig> buttons = request.getButtonConfigs();
        if (buttons == null || buttons.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> insertRecords =
                buttons.stream().map(this::buildButtonIRecord).collect(Collectors.toList());
        return buildChildrenObj(Collections.singletonList(singletonAction("i", insertRecords)));
    }

    /** 构建修改的按钮配置 children（i/u/d）- 基于快照中的历史按钮与新请求按钮对比 */
    private List<Map<String, Object>> buildChildrenUpdate(
            List<ButtonConfig> oldButtons, List<ButtonConfig> newButtons) {
        List<ButtonConfig> oldBtnList = oldButtons != null ? oldButtons : Collections.emptyList();
        List<ButtonConfig> newBtnList = newButtons != null ? newButtons : Collections.emptyList();

        // 按按钮主键ID匹配新旧子项，用于识别 i/d
        Map<Long, ButtonConfig> oldById =
                oldBtnList.stream()
                        .filter(b -> b.getId() != null)
                        .collect(Collectors.toMap(ButtonConfig::getId, b -> b, (a, b) -> a));
        Set<Long> newBtnIds =
                newBtnList.stream()
                        .map(ButtonConfig::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        List<Map<String, Object>> inserts = new ArrayList<>();
        List<Map<String, Object>> updates = new ArrayList<>();
        List<Map<String, Object>> deletes = new ArrayList<>();

        // 新按钮（i/u）：
        // - 无主键 id：新增子项
        // - 有主键 id 且旧子项存在但内容无变化：忽略
        // - 有主键 id 且旧子项存在但内容有变化：视为更新(u)
        // - 有主键 id 但旧列表中不存在：新增
        for (ButtonConfig nb : newBtnList) {
            ButtonConfig ob = nb.getId() != null ? oldById.get(nb.getId()) : null;
            if (ob == null) {
                inserts.add(buildButtonIRecord(nb));
            } else if (hasButtonChanged(ob, nb)) {
                updates.add(buildButtonURecord(ob, nb));
            }
        }

        // 旧按钮：快照中存在但新更新操作中不存在 → 删除（d）
        for (ButtonConfig ob : oldBtnList) {
            if (ob.getId() == null || !newBtnIds.contains(ob.getId())) {
                deletes.add(buildButtonDRecord(ob));
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

    /** 构建修改的按钮配置 children - 数据库实体版（快照缺失时回退） */
    private List<Map<String, Object>> buildChildrenUpdate(
            ApprovalChainConfigSaveReq request, List<SysApprovalChainConfigButton> oldButtons) {
        List<ButtonConfig> oldBtnList = new ArrayList<>();
        if (oldButtons != null) {
            for (SysApprovalChainConfigButton b : oldButtons) {
                ButtonConfig c = new ButtonConfig();
                BeanUtil.copyProperties(b, c);
                oldBtnList.add(c);
            }
        }
        List<ButtonConfig> newButtons =
                request.getButtonConfigs() != null
                        ? request.getButtonConfigs()
                        : Collections.emptyList();
        return buildChildrenUpdate(oldBtnList, newButtons);
    }

    /** 构建按钮新增记录 */
    private Map<String, Object> buildButtonIRecord(ButtonConfig btn) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", buildButtonName(btn));
        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(
                buildColumn(
                        "操作类型",
                        "",
                        resolveButtonField("buttonTypeId", btn.getButtonTypeId()),
                        "buttonTypeId"));
        columns.add(
                buildColumn(
                        "按钮样式", "", resolveButtonField("buttonId", btn.getButtonId()), "buttonId"));
        columns.add(buildColumn("操作日志图标", "", btn.getIcon(), "icon"));
        columns.add(
                buildColumn(
                        "消息类型",
                        "",
                        resolveButtonField("msgTemplateIds", btn.getMsgTemplateIds()),
                        "msgTemplateIds"));
        record.put("columns", columns);
        return record;
    }

    /** 构建按钮删除记录（只保留按钮名称） */
    private Map<String, Object> buildButtonDRecord(ButtonConfig btn) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", buildButtonName(btn));
        return record;
    }

    /** 构建按钮更新记录（只记录发生变化的字段，old/new 对照） */
    private Map<String, Object> buildButtonURecord(ButtonConfig oldBtn, ButtonConfig newBtn) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", buildButtonName(newBtn));
        List<Map<String, Object>> columns = new ArrayList<>();
        addButtonColumnIfChanged(
                columns,
                "操作类型",
                oldBtn.getButtonTypeId(),
                newBtn.getButtonTypeId(),
                "buttonTypeId");
        addButtonColumnIfChanged(
                columns, "按钮样式", oldBtn.getButtonId(), newBtn.getButtonId(), "buttonId");
        addButtonColumnIfChanged(columns, "操作日志图标", oldBtn.getIcon(), newBtn.getIcon(), "icon");
        addButtonColumnIfChanged(
                columns,
                "消息类型",
                oldBtn.getMsgTemplateIds(),
                newBtn.getMsgTemplateIds(),
                "msgTemplateIds");
        record.put("columns", columns);
        return record;
    }

    /** 仅当字段值变化时追加列 */
    private void addButtonColumnIfChanged(
            List<Map<String, Object>> columns,
            String name,
            Object oldValue,
            Object newValue,
            String field) {
        if (!Objects.equals(oldValue, newValue)) {
            columns.add(
                    buildColumn(
                            name,
                            resolveButtonField(field, oldValue),
                            resolveButtonField(field, newValue),
                            field));
        }
    }

    /** 构建按钮名称：优先操作类型label，其次图标、日志描述 */
    private String buildButtonName(ButtonConfig btn) {
        Object typeLabel = resolveButtonField("buttonTypeId", btn.getButtonTypeId());
        if (typeLabel != null && !typeLabel.toString().isEmpty()) {
            return typeLabel.toString();
        }
        if (btn.getIcon() != null && !btn.getIcon().isEmpty()) {
            return btn.getIcon();
        }
        if (btn.getLogName() != null && !btn.getLogName().isEmpty()) {
            return btn.getLogName();
        }
        return "";
    }

    /** 判断按钮字段是否有变化（依据审计列对应的4个字段） */
    private boolean hasButtonChanged(ButtonConfig oldBtn, ButtonConfig newBtn) {
        return !Objects.equals(oldBtn.getButtonTypeId(), newBtn.getButtonTypeId())
                || !Objects.equals(oldBtn.getButtonId(), newBtn.getButtonId())
                || !Objects.equals(oldBtn.getIcon(), newBtn.getIcon())
                || !Objects.equals(oldBtn.getMsgTemplateIds(), newBtn.getMsgTemplateIds());
    }

    // ==================== children JSON 结构 ====================

    /** 构建 children 列表：[{name,mId,actions}]，actions 为 {i|u|d:[records]} 对象 */
    private List<Map<String, Object>> buildChildrenObj(List<Map<String, Object>> actionDetails) {
        Map<String, Object> actions = new LinkedHashMap<>();
        for (Map<String, Object> action : actionDetails) {
            actions.putAll(action);
        }
        Map<String, Object> moduleAction = new LinkedHashMap<>();
        moduleAction.put("name", BUTTON_SUB_MODULE);
        moduleAction.put("mId", "");
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
        column.put("old", oldValue);
        column.put("newer", newValue);
        column.put("field", field);
        return column;
    }

    /** 解析步骤字段值（基于 ApprovalChainConfigSaveReq 的 @AuditField 注解） */
    private Object resolveStepField(String fieldName, Object value) {
        return jsonDiffUtil.resolveFieldValue(fieldName, value, ApprovalChainConfigSaveReq.class);
    }

    /** 解析按钮字段值（基于 ButtonConfig 的 @AuditField 注解） */
    private Object resolveButtonField(String fieldName, Object value) {
        return jsonDiffUtil.resolveFieldValue(fieldName, value, ButtonConfig.class);
    }

    // ==================== 快照 ====================

    /** 新增时保存快照 */
    private void saveSnapshot(
            Long configId,
            ApprovalChainConfigSaveReq request,
            List<SysApprovalChainConfigButton> newButtons) {
        snapshotService.saveSnapshot(
                TABLE_NAME,
                configId,
                JSONUtil.toJsonStr(buildSnapshotRequest(request, newButtons)));
    }

    /** 从快照加载修改前的历史请求，快照缺失或解析失败时返回null */
    private ApprovalChainConfigSaveReq loadSnapshotRequest(Long configId) {
        SysDataSnapshot snapshot = snapshotService.getLatestSnapshot(TABLE_NAME, configId);
        if (snapshot == null
                || snapshot.getJsonData() == null
                || snapshot.getJsonData().isEmpty()) {
            return null;
        }
        try {
            return JSONUtil.toBean(snapshot.getJsonData(), ApprovalChainConfigSaveReq.class);
        } catch (Exception e) {
            log.warn("审批链快照反序列化失败，回退数据库实体对比: configId={}", configId, e);
            return null;
        }
    }

    /**
     * 构建快照请求（完整保存 {@link ApprovalChainConfigSaveReq} JSON）
     *
     * <p>由于按钮采用删除后重新插入的策略，每次保存后按钮主键ID会重新生成，因此需将数据库生成的按钮ID回写到 快照请求中，保证后续 update
     * 与快照按按钮ID对照时能正确识别新增/删除。
     */
    private ApprovalChainConfigSaveReq buildSnapshotRequest(
            ApprovalChainConfigSaveReq request, List<SysApprovalChainConfigButton> newButtons) {
        ApprovalChainConfigSaveReq snapshotReq =
                JSONUtil.toBean(JSONUtil.toJsonStr(request), ApprovalChainConfigSaveReq.class);
        if (snapshotReq.getButtonConfigs() != null && newButtons != null) {
            List<ButtonConfig> btnConfigs = snapshotReq.getButtonConfigs();
            for (int i = 0; i < btnConfigs.size() && i < newButtons.size(); i++) {
                btnConfigs.get(i).setId(newButtons.get(i).getId());
            }
        }
        return snapshotReq;
    }

    // ==================== 发送审计日志 ====================

    /**
     * 发送审计日志到监控服务
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
            log.error("审批链配置审计日志发送失败: operation={}", operation, e);
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
                                log.info("事务已回滚，跳过审批链配置审计日志");
                            }
                        }
                    });
        } else {
            auditLogSendService.sendAuditLog(context);
        }
    }
}
