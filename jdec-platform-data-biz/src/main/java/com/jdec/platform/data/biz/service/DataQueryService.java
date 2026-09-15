package com.jdec.platform.data.biz.service;

import com.jdec.platform.data.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.data.api.dto.common.ModuleNodeDTO;
import com.jdec.platform.data.api.dto.model.ModuleInfo;
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.response.DataDetail;
import com.jdec.platform.data.api.dto.response.DataOption;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.biz.dsl.JooqDataContextFactory;
import com.jdec.platform.data.biz.plan.compiler.QueryPlanCompiler;
import com.jdec.platform.data.biz.plan.executor.QueryPlanExecutor;
import com.jdec.platform.data.biz.plan.model.QueryPlan;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class DataQueryService {

    private final MetaCacheService metaCacheService;
    private final JooqDataContextFactory jooqDataContextFactory;
    private final QueryPlanCompiler queryPlanCompiler;
    private final QueryPlanExecutor queryPlanExecutor;

    /** 核心通用动态模块树查询 */
    public DataPage<Map<String, Object>> query(QueryReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req);
        return queryPlanExecutor.execute(plan);
    }

    /** 动态单据详情精准查询快捷方法 (无下级展开) */
    public DataDetail<Map<String, Object>> getDetail(Long moduleId, Long id) {
        return getDetail(moduleId, id, (List<Long>) null);
    }

    /** 动态单据详情精准查询门面 (底层以 query 驱动，第一层级保证且只返回单条记录 Map，下级子模块自相似嵌套) */
    public DataDetail<Map<String, Object>> getDetail(Long moduleId, Long id, List<Long> fields) {
        if (moduleId == null || id == null) {
            throw new IllegalArgumentException("模块 ID 和单据 ID 不能为空");
        }

        ModuleInfo meta = metaCacheService.getModuleComplete(moduleId);
        if (meta == null) {
            throw new IllegalArgumentException("模块 ID [" + moduleId + "] 不存在或未配置元数据");
        }

        List<FilterItem> filters = new ArrayList<>();
        Long idFieldId = null;
        if (meta.getFields() != null) {
            for (ModuleFieldDTO f : meta.getFields()) {
                if ("id".equalsIgnoreCase(f.getColumnName())) {
                    idFieldId = f.getId();
                    break;
                }
            }
        }

        if (idFieldId == null) {
            throw new IllegalStateException(
                    String.format(
                            "模块 [%s](ID: %d) 的元数据中未配置主键 [id] 字段，无法执行单据详情查询",
                            meta.getModuleName(), moduleId));
        }

        filters.add(FilterItem.builder().fieldId(idFieldId).operator("EQ").value(id).build());

        QueryReq queryReq =
                QueryReq.builder()
                        .moduleId(moduleId)
                        .pageNo(1)
                        .pageSize(1)
                        .fields(fields)
                        .filters(filters)
                        .build();

        DataPage<Map<String, Object>> page = query(queryReq);
        Map<String, Object> record =
                (page != null && page.getRecords() != null && !page.getRecords().isEmpty())
                        ? page.getRecords().get(0)
                        : Collections.emptyMap();

        // 详情接口剥离底层物理关联拓扑，并构建与 data 树镜像对称的自相似 meta.children 树
        Set<Long> fieldSet = (fields != null && !fields.isEmpty()) ? new HashSet<>(fields) : null;
        ModuleInfo clientMeta = buildClientMetaTree(meta, fieldSet);

        return DataDetail.of(clientMeta, record);
    }

    /** 递归构建客户端呈现的自相似 ModuleInfo 元数据树 (包含子模块及各自过滤后的 fields) */
    private ModuleInfo buildClientMetaTree(ModuleInfo rootMeta, Set<Long> fieldSet) {
        if (rootMeta == null) {
            return null;
        }

        // 收集当前根模块涉及的所有子孙模块节点并按 parentId 分组
        Map<Long, List<ModuleNodeDTO>> childrenByParent = new HashMap<>();
        if (rootMeta.getModuleNodes() != null) {
            for (ModuleNodeDTO node : rootMeta.getModuleNodes()) {
                if (node != null && node.getParentId() != null) {
                    childrenByParent
                            .computeIfAbsent(node.getParentId(), k -> new ArrayList<>())
                            .add(node);
                }
            }
        }

        return assembleModuleInfoTree(rootMeta, childrenByParent, fieldSet);
    }

    private ModuleInfo assembleModuleInfoTree(
            ModuleInfo currentMeta,
            Map<Long, List<ModuleNodeDTO>> childrenByParent,
            Set<Long> fieldSet) {
        Long currentModuleId = currentMeta.getModuleId();

        // 1. 过滤当前模块的 fields
        List<ModuleFieldDTO> filteredFields = currentMeta.getFields();
        if (fieldSet != null && filteredFields != null) {
            filteredFields =
                    filteredFields.stream()
                            .filter(f -> f.getId() != null && fieldSet.contains(f.getId()))
                            .toList();
        }

        // 2. 递归收集并装配直接子模块
        List<ModuleInfo> childMetaList = new ArrayList<>();
        List<ModuleNodeDTO> directChildNodes = childrenByParent.get(currentModuleId);
        if (directChildNodes != null && !directChildNodes.isEmpty()) {
            for (ModuleNodeDTO childNode : directChildNodes) {
                if (childNode != null && childNode.getId() != null) {
                    ModuleInfo childMeta = metaCacheService.getModuleComplete(childNode.getId());
                    if (childMeta != null) {
                        ModuleInfo assembledChild =
                                assembleModuleInfoTree(childMeta, childrenByParent, fieldSet);
                        // 若显式指定了 fields 白名单，且该子模块及其所有后代均未命中任何字段，则不展示该子节点
                        if (fieldSet != null && isTreeEmpty(assembledChild)) {
                            continue;
                        }
                        childMetaList.add(assembledChild);
                    }
                }
            }
        }

        return currentMeta.toBuilder()
                .fields(filteredFields)
                .children(childMetaList.isEmpty() ? null : childMetaList)
                .tableRelations(null)
                .moduleNodes(null)
                .build();
    }

    /** 判断元数据节点及其子节点是否未命中任何字段 */
    private boolean isTreeEmpty(ModuleInfo node) {
        if (node == null) {
            return true;
        }
        boolean hasFields = node.getFields() != null && !node.getFields().isEmpty();
        if (hasFields) {
            return false;
        }
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            for (ModuleInfo c : node.getChildren()) {
                if (!isTreeEmpty(c)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 通用字段搜索下拉候选项查询 (基于 QueryPlan 统一编译与执行) */
    public List<DataOption> getOptions(OptionReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req);
        List<String> values = queryPlanExecutor.executeDistinctOptions(plan, req.getFieldId());

        List<DataOption> options = new ArrayList<>();
        for (String val : values) {
            if (val != null) {
                options.add(DataOption.builder().label(val).value(val).build());
            }
        }
        return options;
    }
}
