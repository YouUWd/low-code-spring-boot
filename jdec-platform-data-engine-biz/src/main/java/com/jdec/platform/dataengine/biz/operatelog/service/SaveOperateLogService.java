package com.jdec.platform.dataengine.biz.operatelog.service;

import cn.hutool.core.collection.CollectionUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jdec.platform.common.api.MonitorLogApi;
import com.jdec.platform.common.api.dto.log.MonitorApproveOperation;
import com.jdec.platform.common.api.dto.log.MonitorContentSystem;
import com.jdec.platform.common.api.dto.log.MonitorLoginInfo;
import com.jdec.platform.common.api.dto.log.MonitorMockInfo;
import com.jdec.platform.common.api.dto.request.MonitorLogSaveReq;
import com.jdec.platform.config.api.SysMenuApi;
import com.jdec.platform.config.api.dto.common.FieldWordCloudDTO;
import com.jdec.platform.config.api.dto.response.SysMenuResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.api.dto.response.DynamicSaveResp;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.dto.context.ModuleDetailQueryContext;
import com.jdec.platform.dataengine.biz.dto.context.ModuleQueryContext;
import com.jdec.platform.dataengine.biz.enums.SaveType;
import com.jdec.platform.dataengine.biz.operatelog.util.OperateLogRemarkUtil;
import com.jdec.platform.dataengine.biz.service.ModuleBizDataService;
import com.jdec.platform.dataengine.biz.service.ModuleMetaDataService;
import com.jdec.platform.dataengine.biz.third.api.persistence.DataPersistenceUtil;
import com.jdec.platform.dataengine.biz.util.JsonUtil;
import com.jdec.platform.shared.constant.MonitorLogConstant;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.RequestContextSnapshot;
import com.jdec.platform.shared.context.RequestContextSnapshotHolder;
import com.jdec.platform.shared.context.UserContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 无审批链的保存操作日志服务。
 *
 * <p>两段式：{@link #prepare} 在保存前取旧数据（此时库中仍是旧值）；{@link #finish} 在保存成功后对比生成 remark 并落库。
 * 与业务无事务关系，由切面负责异常隔离。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaveOperateLogService {

    /** 操作动作：首次保存 */
    public static final String OPERATION_SAVE = "保存";

    /** 操作动作：修改 */
    public static final String OPERATION_UPDATE = "修改";

    /** 操作动作：删除 */
    public static final String OPERATION_DELETE = "删除";

    /** 修改动作图标 */
    private static final String APPROVE_PATH_UPDATE =
            "http://192.168.60.8:9001/jdec-platform/image/submit.svg";

    /** 删除动作图标 */
    private static final String APPROVE_PATH_DELETE =
            "http://192.168.60.8:9001/jdec-platform/image/reject.svg";

    /** 无审批链时为手动操作 */
    private static final Integer APPROVAL_TYPE_MANUAL = 1;

    /** remark 中字段明细数组的键名 */
    private static final String COLUMNS_KEY = "columns";

    /** 字段明细中的字段英文名键名（表名.列名） */
    private static final String FIELD_KEY = "field";

    /** 字段明细中的词云标记键名 */
    private static final String CLOUD_KEY = "cloud";

    /** 词云标记：来自词云 */
    private static final Integer CLOUD_YES = 1;

    /** 词云标记：非词云 */
    private static final Integer CLOUD_NO = 0;

    private final ModuleMetaDataService moduleMetaDataService;

    private final ModuleBizDataService moduleBizDataService;

    private final OperateLogRemarkUtil operateLogRemarkUtil;

    private final MonitorLogApi monitorLogApi;

    private final SysMenuApi sysMenuApi;

    private final ConfigApi configApi;

    /** 保存前：判断是否需要记录日志并取旧数据；不需要（非普通保存 / 有审批链 / 参数缺失）返回 null。 */
    public SaveOperateLogContext prepare(DynamicSaveReq req) {
        if (req == null
                || req.getNestedModuleData() == null
                || req.getNestedModuleData().getModuleId() == null
                || CollectionUtil.isEmpty(req.getNestedModuleData().getRecord())) {
            return null;
        }
        // 仅普通保存；变更/实时保存属于审批链
        if (!SaveType.SAVE.equals(SaveType.getSaveType(req.getBtnType()))) {
            return null;
        }
        Long structureRootModuleId = req.getNestedModuleData().getModuleId();
        Map<Long, SysModuleCombineInfo> moduleCacheMap =
                moduleMetaDataService.getModuleMetaDataMapAsync(
                        ModuleQueryContext.builder()
                                .moduleId(structureRootModuleId)
                                .projectNo(AppContext.getProjectNo())
                                .subjectId(AppContext.getSubjectId())
                                .roleId(UserContext.getRoleId())
                                .isBuildFieldPermission(false)
                                .isBuildFieldPro(false)
                                .build());
        if (CollectionUtil.isEmpty(moduleCacheMap)) {
            return null;
        }
        SysModuleCombineInfo structureRootModule = moduleCacheMap.get(structureRootModuleId);
        if (structureRootModule == null || structureRootModule.getModuleInfo() == null) {
            return null;
        }
        Long rootModuleId = structureRootModule.getModuleTree().getRootModuleId();
        Boolean isApproval = moduleMetaDataService.getApprovalStatusByModuleIdAsync(rootModuleId);
        // 有审批链的模块交给既有链路处理
        if (Boolean.TRUE.equals(isApproval)) {
            return null;
        }

        String primaryTable = structureRootModule.getModuleInfo().getPrimaryTable();
        Map<String, Object> rootRow = req.getNestedModuleData().getRecord().get(0).getRow();
        Long structureRootDataId = null;
        if (StringUtils.isNotBlank(primaryTable)) {
            structureRootDataId =
                    DataPersistenceUtil.getTableRowIdWithTabName(rootRow, primaryTable);
        }
        // 动作判定：无主键=首次保存；提交了删除标记=删除；否则=修改
        String operation = resolveOperation(rootRow, primaryTable, structureRootDataId);
        NestedModuleDataDTO oldData = null;
        if (structureRootDataId != null && structureRootDataId > 0) {
            oldData =
                    moduleBizDataService.getModuleDetailData(
                            ModuleDetailQueryContext.builder()
                                    .moduleId(structureRootModuleId)
                                    .dataId(structureRootDataId)
                                    .versionNo(null)
                                    .moduleCacheMap(moduleCacheMap)
                                    .projectNo(AppContext.getProjectNo())
                                    .subjectId(AppContext.getSubjectId())
                                    .loginUser(UserContext.getLoginUser())
                                    .build());
        }
        return SaveOperateLogContext.builder()
                .structureRootModuleId(structureRootModuleId)
                .menuId(req.getNestedModuleData().getMenuId())
                .primaryTable(primaryTable)
                .operation(operation)
                .moduleCacheMap(moduleCacheMap)
                .oldData(oldData)
                .build();
    }

    /** 保存成功后：对比生成 remark、补全 approve/content，写操作日志（type=0）。 */
    public void finish(SaveOperateLogContext ctx, DynamicSaveReq req, DynamicSaveResp resp) {
        if (ctx == null || req == null || req.getNestedModuleData() == null) {
            return;
        }
        // 首次保存不记录 remark；后续保存有旧数据时生成变更明细，无变更不记录日志
        String remarkJson = null;
        if (ctx.getOldData() != null) {
            remarkJson =
                    operateLogRemarkUtil.buildRemark(
                            ctx.getOldData(),
                            req.getNestedModuleData(),
                            ctx.getModuleCacheMap(),
                            ctx.getMenuId());
            if (StringUtils.isBlank(remarkJson)) {
                return;
            }
        }

        SysMenuResp menu = queryMenu(ctx.getMenuId());
        String menuName =
                menu != null && StringUtils.isNotBlank(menu.getTitle())
                        ? menu.getTitle()
                        : moduleName(ctx);
        String operationResult = "已" + ctx.getOperation();

        MonitorLogSaveReq logReq = new MonitorLogSaveReq();
        logReq.setType(MonitorLogConstant.TYPE_OPERATION_LOG);
        logReq.setSubjectId(AppContext.getSubjectId());
        logReq.setApprove(buildApprove(menuName, ctx.getOperation(), operationResult));
        logReq.setContent(
                buildContent(
                        menuName,
                        menu != null ? menu.getParam() : null,
                        operationResult,
                        resolveBusinessNo(resp, req, ctx.getPrimaryTable())));

        MonitorLoginInfo loginInfo = new MonitorLoginInfo();
        loginInfo.setUserId(UserContext.getRealUserId());
        logReq.setLoginInfo(loginInfo);

        MonitorMockInfo mockInfo = new MonitorMockInfo();
        mockInfo.setUserId(UserContext.getMockUserId());
        mockInfo.setFlag(UserContext.isMockLogin() ? 1 : 0);
        logReq.setMockInfo(mockInfo);

        RequestContextSnapshot snapshot = RequestContextSnapshotHolder.get();
        if (snapshot != null) {
            logReq.setIp(snapshot.getClientIp());
            logReq.setBrowser(snapshot.getBrowser());
            logReq.setPlatForm(snapshot.getPlatform());
        }
        // moduleId + dataId：monitor_operate_index 需要，用于按单据查询操作日志
        logReq.setModuleId(resp != null ? resp.getModuleId() : null);
        logReq.setDataId(resp != null ? resp.getDataId() : null);
        // remark 为 JSON 串，需解析回对象再落库，避免监控日志侧二次序列化变成转义字符串
        if (StringUtils.isNotBlank(remarkJson)) {
            JsonNode remarkNode;
            try {
                remarkNode = JsonUtil.json2Node(remarkJson);
            } catch (JsonProcessingException e) {
                log.warn("操作日志 remark 解析失败，跳过日志记录", e);
                return;
            }
            // 字段明细补充词云标记：sys_field.relation_business_no 有值视为来自词云
            fillCloudFlags(remarkNode);
            logReq.setRemark(remarkNode);
        }

        monitorLogApi.saveLog(logReq);
    }

    /** 给 remark 中所有 columns 明细补充 cloud 标记（1-来自词云，0-否） */
    private void fillCloudFlags(JsonNode node) {
        fillCloudFlags(node, loadCloudFieldIndexes());
    }

    private void fillCloudFlags(JsonNode node, Set<String> cloudFieldIndexes) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            JsonNode columns = object.get(COLUMNS_KEY);
            if (columns != null && columns.isArray()) {
                for (JsonNode element : columns) {
                    if (!(element instanceof ObjectNode column)) {
                        continue;
                    }
                    JsonNode field = column.get(FIELD_KEY);
                    boolean cloud =
                            field != null
                                    && field.isTextual()
                                    && cloudFieldIndexes.contains(field.asText());
                    column.put(CLOUD_KEY, cloud ? CLOUD_YES : CLOUD_NO);
                }
            }
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                fillCloudFlags(object.get(name), cloudFieldIndexes);
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                fillCloudFlags(element, cloudFieldIndexes);
            }
        }
    }

    /** 查询配置中心词云字段索引（表名.列名）；查询失败按无词云字段处理，不阻断日志记录 */
    private Set<String> loadCloudFieldIndexes() {
        try {
            List<FieldWordCloudDTO> fields =
                    configApi.getWordCloudFields(
                            AppContext.getProjectNo(), AppContext.getSubjectId());
            Set<String> indexes = new HashSet<>();
            for (FieldWordCloudDTO field : fields) {
                if (StringUtils.isNotBlank(field.getRelationBusinessNo())
                        && field.getTableName() != null
                        && field.getColumnName() != null) {
                    indexes.add(field.getTableName() + "." + field.getColumnName());
                }
            }
            return indexes;
        } catch (Exception e) {
            log.warn("操作日志词云字段查询失败，全部标记为非词云", e);
            return Collections.emptySet();
        }
    }

    // ==================== 动作与展示信息 ====================

    /** 动作判定：提交了删除标记=删除；根记录无主键=首次保存；否则=修改 */
    private String resolveOperation(
            Map<String, Object> rootRow, String primaryTable, Long structureRootDataId) {
        if (StringUtils.isNotBlank(primaryTable)
                && Boolean.TRUE.equals(
                        DataPersistenceUtil.getTableRowDeletedFlagWithTabName(
                                rootRow, primaryTable))) {
            return OPERATION_DELETE;
        }
        return structureRootDataId != null && structureRootDataId > 0
                ? OPERATION_UPDATE
                : OPERATION_SAVE;
    }

    /** 审批信息：无审批链为手动操作（approvalType=1） */
    private MonitorApproveOperation buildApprove(
            String menuName, String operation, String operationResult) {
        MonitorApproveOperation approve = new MonitorApproveOperation();
        approve.setLogSubType(MonitorLogConstant.OPERATION_LOG);
        approve.setName((menuName != null ? menuName : "") + operation);
        approve.setPath(resolveApprovePath(operation));
        approve.setResult(operationResult);
        approve.setApprovalType(APPROVAL_TYPE_MANUAL);
        return approve;
    }

    /** 按操作动作选择图标：修改取提交图标、删除取驳回图标，首次保存无图标 */
    private String resolveApprovePath(String operation) {
        if (OPERATION_DELETE.equals(operation)) {
            return APPROVE_PATH_DELETE;
        }
        if (OPERATION_UPDATE.equals(operation)) {
            return APPROVE_PATH_UPDATE;
        }
        return null;
    }

    /** 日志内容：系统日志子类型，承载最外层菜单名称/参数与业务编码 */
    private MonitorContentSystem buildContent(
            String menuName, String menuParam, String operationResult, String businessNo) {
        MonitorContentSystem content = new MonitorContentSystem();
        content.setLogSubType(MonitorLogConstant.SYSTEM_LOG);
        content.setModuleName(menuName);
        content.setMenuParam(menuParam);
        content.setLinkName(businessNo);
        content.setOperation(operationResult);
        return content;
    }

    /** 业务编码：优先取保存响应，缺失时从请求行解析 */
    private String resolveBusinessNo(
            DynamicSaveResp resp, DynamicSaveReq req, String primaryTable) {
        if (resp != null && StringUtils.isNotBlank(resp.getBusinessNo())) {
            return resp.getBusinessNo();
        }
        if (StringUtils.isBlank(primaryTable) || req.getNestedModuleData().getRecord() == null) {
            return null;
        }
        return DataPersistenceUtil.getTableRowBusinessNoWithTabName(
                req.getNestedModuleData().getRecord().get(0).getRow(), primaryTable);
    }

    /** 菜单名称兜底：菜单查询不到时取当前结构根模块名称 */
    private String moduleName(SaveOperateLogContext ctx) {
        SysModuleCombineInfo module = ctx.getModuleCacheMap().get(ctx.getStructureRootModuleId());
        return module != null && module.getModuleInfo() != null
                ? module.getModuleInfo().getModuleName()
                : null;
    }

    /** 查询菜单（取名称与菜单参数），失败时返回 null 不影响日志落库 */
    private SysMenuResp queryMenu(Long menuId) {
        if (menuId == null) {
            return null;
        }
        try {
            return sysMenuApi.getMenuById(menuId);
        } catch (Exception e) {
            log.warn("查询菜单失败，menuId={}", menuId, e);
            return null;
        }
    }
}
