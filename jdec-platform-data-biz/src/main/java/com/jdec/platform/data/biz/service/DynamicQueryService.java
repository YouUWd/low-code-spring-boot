package com.jdec.platform.data.biz.service;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.response.ModuleHeaderNodeDTO;
import com.jdec.platform.config.api.dto.response.SysModuleMetaResp;
import com.jdec.platform.data.api.dto.model.EngineModuleMeta;
import com.jdec.platform.data.api.dto.request.DynamicFilterItem;
import com.jdec.platform.data.api.dto.request.DynamicOptionReq;
import com.jdec.platform.data.api.dto.request.DynamicQueryReq;
import com.jdec.platform.data.api.dto.request.EngineHeaderReq;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.DynamicOptionItem;
import com.jdec.platform.data.api.dto.response.EngineDataResult;
import com.jdec.platform.data.api.dto.response.EngineHeaderResp;
import com.jdec.platform.data.biz.dsl.JooqContextFactory;
import com.jdec.platform.data.biz.plan.compiler.QueryPlanCompiler;
import com.jdec.platform.data.biz.plan.executor.QueryPlanExecutor;
import com.jdec.platform.data.biz.plan.model.PhysicalFieldSpec;
import com.jdec.platform.data.biz.plan.model.QueryNodePlan;
import com.jdec.platform.data.biz.plan.model.QueryPlan;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

/**
 * 动态查询服务门面 (DynamicQueryService)
 *
 * <p>彻底基于 Plan 编译与执行模型驱动：
 *
 * <ul>
 *   <li>1. 由 QueryPlanCompiler 统一将自相似请求树编译为 QueryPlan；
 *   <li>2. 由 QueryPlanExecutor 执行物理扫描、批量抓取与行内树装配；
 *   <li>3. 保持与 DataEngineApi, DynamicDetailReq, DynamicOptionReq 的门面兼容。
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicQueryService {

    private final MetadataCacheService metadataCacheService;
    private final JooqContextFactory jooqContextFactory;
    private final QueryPlanCompiler queryPlanCompiler;
    private final QueryPlanExecutor queryPlanExecutor;

    /** 获取动态列表表头配置 (动静分离，带完整 modulePath 与数据定位 path) */
    public EngineHeaderResp getHeader(EngineHeaderReq req) {
        List<Long> fieldIds = req != null ? req.getFields() : null;
        List<ModuleFieldDTO> fields;
        if (fieldIds != null && !fieldIds.isEmpty()) {
            fields = metadataCacheService.listFieldsByIds(fieldIds);
        } else if (req != null && req.getModuleId() != null) {
            SysModuleMetaResp meta = metadataCacheService.getModuleComplete(req.getModuleId());
            fields = meta != null ? meta.getFields() : Collections.emptyList();
        } else {
            fields = Collections.emptyList();
        }

        Long rootModuleId = (req != null && req.getModuleId() != null) ? req.getModuleId() : null;
        List<ModuleFieldDTO> enrichedFields = enrichFieldsWithPath(fields, rootModuleId);
        return EngineHeaderResp.builder().fields(enrichedFields).build();
    }

    /** 为表头字段列表注入完整的 modulePath（模块血缘）与 path（数据提取路径） */
    private List<ModuleFieldDTO> enrichFieldsWithPath(
            List<ModuleFieldDTO> fields, Long rootModuleId) {
        if (fields == null || fields.isEmpty()) {
            return Collections.emptyList();
        }

        List<ModuleFieldDTO> result = new ArrayList<>();
        Map<Long, SysModuleMetaResp> metaCache = new HashMap<>();

        for (ModuleFieldDTO f : fields) {
            if (f == null) {
                continue;
            }
            ModuleFieldDTO copy = new ModuleFieldDTO();
            copy.setId(f.getId());
            copy.setModuleId(f.getModuleId());
            copy.setTableName(f.getTableName());
            copy.setColumnName(f.getColumnName());
            copy.setDisplayName(f.getDisplayName());
            copy.setSortOrder(f.getSortOrder());

            Long mid = f.getModuleId();
            if (mid != null) {
                // 1. 回溯推导自顶向下的完整 modulePath
                List<Long> modPath = new ArrayList<>();
                Long curr = mid;
                Set<Long> visited = new HashSet<>();
                while (curr != null && curr > 0 && visited.add(curr)) {
                    modPath.add(0, curr);
                    if (rootModuleId != null && curr.equals(rootModuleId)) {
                        break;
                    }
                    SysModuleMetaResp m =
                            metaCache.computeIfAbsent(
                                    curr, metadataCacheService::getModuleComplete);
                    curr =
                            (m != null && m.getModule() != null)
                                    ? m.getModule().getParentId()
                                    : null;
                }
                if (rootModuleId != null
                        && (modPath.isEmpty() || !modPath.get(0).equals(rootModuleId))) {
                    modPath.add(0, rootModuleId);
                }
                copy.setModulePath(modPath);
            }

            result.add(copy);
        }

        return result;
    }

    /** 核心通用动态模块树查询 (纯数据引擎，支持原子统一返回 Header 与 Data) */
    public DataPage<Map<String, Object>> query(DynamicQueryReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req);
        DataPage<Map<String, Object>> page = queryPlanExecutor.execute(plan);
        if (req == null || req.getWithHeader() == null || req.getWithHeader()) {
            page.setHeader(buildHeaderTreeFromPlan(plan));
        }
        return page;
    }

    /** 从编译后的 QueryPlan 动态构建 100% 镜像对齐的树形表头契约 (ModuleHeaderNodeDTO) */
    public ModuleHeaderNodeDTO buildHeaderTreeFromPlan(QueryPlan plan) {
        if (plan == null || plan.getRootNodePlan() == null) {
            return null;
        }
        return buildNodeHeader(plan.getRootNodePlan());
    }

    private ModuleHeaderNodeDTO buildNodeHeader(QueryNodePlan nodePlan) {
        if (nodePlan == null) {
            return null;
        }
        String label = "模块 " + nodePlan.getModuleId();
        if (nodePlan.getModuleMeta() != null && nodePlan.getModuleMeta().getModule() != null) {
            String mName = nodePlan.getModuleMeta().getModule().getModuleName();
            if (mName != null && !mName.isBlank()) {
                label = mName;
            }
        }

        List<ModuleHeaderNodeDTO> children = new ArrayList<>();

        // 1. 直属字段叶子节点
        if (nodePlan.getProjectedFields() != null) {
            for (PhysicalFieldSpec spec : nodePlan.getProjectedFields()) {
                if (spec.getColumnName() == null) {
                    continue;
                }
                String dataIndex =
                        (spec.getTableName() != null
                                        ? spec.getTableName()
                                        : nodePlan.getPrimaryTable())
                                + "."
                                + spec.getColumnName();
                String fieldLabel =
                        spec.getDisplayName() != null
                                ? spec.getDisplayName()
                                : spec.getColumnName();
                children.add(
                        ModuleHeaderNodeDTO.builder()
                                .fieldId(spec.getFieldId())
                                .label(fieldLabel)
                                .dataIndex(dataIndex)
                                .build());
            }
        }

        // 2. 递归构建独立子模块容器节点与同模块从表叶子字段
        if (nodePlan.getChildren() != null) {
            for (QueryNodePlan childPlan : nodePlan.getChildren()) {
                if (childPlan.getModuleId() != null
                        && !childPlan.getModuleId().equals(nodePlan.getModuleId())) {
                    // 独立子模块 (如 104、105、106)
                    ModuleHeaderNodeDTO childNode = buildNodeHeader(childPlan);
                    if (childNode != null) {
                        children.add(childNode);
                    }
                } else if (childPlan.getProjectedFields() != null) {
                    // 同模块从表 (如 103 下的 student_course_score_item)，字段平铺挂载在当前模块叶子节点下
                    for (PhysicalFieldSpec spec : childPlan.getProjectedFields()) {
                        if (spec.getColumnName() == null) {
                            continue;
                        }
                        String dataIndex =
                                (spec.getTableName() != null
                                                ? spec.getTableName()
                                                : childPlan.getPrimaryTable())
                                        + "."
                                        + spec.getColumnName();
                        String fieldLabel =
                                spec.getDisplayName() != null
                                        ? spec.getDisplayName()
                                        : spec.getColumnName();
                        children.add(
                                ModuleHeaderNodeDTO.builder()
                                        .fieldId(spec.getFieldId())
                                        .label(fieldLabel)
                                        .dataIndex(dataIndex)
                                        .build());
                    }
                }
            }
        }

        return ModuleHeaderNodeDTO.builder()
                .moduleId(nodePlan.getModuleId())
                .label(label)
                .children(children.isEmpty() ? null : children)
                .build();
    }

    /** 动态单据详情精准查询快捷方法 (无下级展开) */
    public EngineDataResult<Map<String, Object>> getDetail(Long moduleId, Long id) {
        return getDetail(moduleId, id, null);
    }

    /** 动态单据详情精准查询门面 (底层以 query 驱动，第一层级保证且只返回单条记录 Map，下级子模块自相似嵌套) */
    public EngineDataResult<Map<String, Object>> getDetail(
            Long moduleId, Long id, List<DynamicQueryReq> children) {
        if (moduleId == null || id == null) {
            throw new IllegalArgumentException("模块 ID 和单据 ID 不能为空");
        }

        SysModuleMetaResp meta = metadataCacheService.getModuleComplete(moduleId);
        if (meta == null) {
            throw new IllegalArgumentException("模块 ID [" + moduleId + "] 不存在或未配置元数据");
        }

        List<DynamicFilterItem> filters = new ArrayList<>();
        Long idFieldId = null;
        if (meta.getFields() != null) {
            for (ModuleFieldDTO f : meta.getFields()) {
                if ("id".equalsIgnoreCase(f.getColumnName())) {
                    idFieldId = f.getId();
                    break;
                }
            }
        }

        if (idFieldId != null) {
            filters.add(
                    DynamicFilterItem.builder()
                            .fieldId(idFieldId)
                            .operator("EQ")
                            .value(id)
                            .build());
        }

        DynamicQueryReq queryReq =
                DynamicQueryReq.builder()
                        .moduleId(moduleId)
                        .pageNo(1)
                        .pageSize(1)
                        .filters(filters)
                        .children(children)
                        .build();

        DataPage<Map<String, Object>> page = query(queryReq);
        Map<String, Object> record =
                (page != null && page.getRecords() != null && !page.getRecords().isEmpty())
                        ? page.getRecords().get(0)
                        : Collections.emptyMap();

        EngineModuleMeta engineMeta = buildEngineModuleMeta(meta);
        return EngineDataResult.of(engineMeta, record);
    }

    private EngineModuleMeta buildEngineModuleMeta(SysModuleMetaResp meta) {
        if (meta == null) {
            return null;
        }
        var m = meta.getModule();
        return EngineModuleMeta.builder()
                .moduleId(m != null ? m.getId() : null)
                .moduleCode(m != null ? m.getModuleCode() : "")
                .moduleName(m != null ? m.getModuleName() : "")
                .moduleDesc(m != null ? m.getModuleDesc() : "")
                .primaryTable(m != null ? m.getPrimaryTable() : "")
                .fields(meta.getFields())
                .headers(meta.getModuleHeaders())
                .statuses(meta.getModuleStatuses())
                .moduleNodes(meta.getModuleNodes())
                .build();
    }

    /** 通用字段搜索下拉候选项查询 */
    public List<DynamicOptionItem> getOptions(DynamicOptionReq req) {
        if (req == null || req.getModuleId() == null) {
            throw new IllegalArgumentException("候选项请求或模块 ID 不能为空");
        }

        SysModuleMetaResp completeResp = metadataCacheService.getModuleComplete(req.getModuleId());
        if (completeResp == null || completeResp.getModule() == null) {
            throw new IllegalArgumentException("模块 ID [" + req.getModuleId() + "] 不存在或未配置元数据");
        }

        String table = req.getTableName();
        String column = req.getColumnName();

        if (table == null || table.isBlank() || column == null || column.isBlank()) {
            return Collections.emptyList();
        }

        DSLContext dsl = jooqContextFactory.getContext();
        Field<Object> targetField = DSL.field(DSL.name(table, column));
        var selectStep =
                dsl.selectDistinct(targetField)
                        .from(DSL.table(DSL.name(table)))
                        .where(targetField.isNotNull())
                        .and(DSL.field(DSL.name(table, "deleted")).eq((byte) 0));

        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            selectStep = selectStep.and(targetField.like("%" + req.getKeyword().trim() + "%"));
        }

        var records = selectStep.limit(20).fetch();

        List<DynamicOptionItem> options = new ArrayList<>();
        for (var r : records) {
            Object val = r.get(0);
            if (val != null) {
                String strVal = String.valueOf(val);
                options.add(DynamicOptionItem.builder().label(strVal).value(strVal).build());
            }
        }
        return options;
    }
}
