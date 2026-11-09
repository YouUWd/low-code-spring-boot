package com.jdec.platform.common.biz.query.condition;

import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.response.DataRightConfigResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.config.open.api.OpenRolePermissionApi;
import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.model.predicate.ComparisonPredicate;
import com.jdec.platform.data.api.model.predicate.CompoundPredicate;
import com.jdec.platform.data.api.model.predicate.Predicate;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.utils.JsonValueUtils;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * 角色行级数据权限条件解析器 (DataPermissionConditionResolver)
 *
 * <p>核心职责：
 *
 * <ul>
 *   <li>根据当前登录用户的角色，通过 {@link OpenRolePermissionApi} 拉取行级数据权限配置。
 *   <li>将主表及各子孙模块表的数据权限转换为标准的 {@link FilterItem} 结构化过滤项集合。
 *   <li>复用底层数据引擎 {@link DataApi#queryList(QueryReq)} 进行查询，直接利用数据引擎原生的模块树拓扑与子表 EXISTS
 *       上卷能力获取满足条件的主表物理 ID 集合。
 *   <li>彻底消除在 common-biz 重复推导父子表外键与手工拼装 EXISTS 的冗余逻辑。
 * </ul>
 */
@Slf4j
@Component
public class DataPermissionConditionResolver {

    public static final String SUBJECT_ID_COLUMN = "subject_id";

    private final OpenRolePermissionApi openRolePermissionApi;
    private final DataApi dataApi;
    private final ConfigApi configApi;

    @Autowired
    public DataPermissionConditionResolver(
            OpenRolePermissionApi openRolePermissionApi,
            DataApi dataApi,
            @Autowired(required = false) ConfigApi configApi) {
        this.openRolePermissionApi = openRolePermissionApi;
        this.dataApi = dataApi;
        this.configApi = configApi;
    }

    public DataPermissionConditionResolver(
            OpenRolePermissionApi openRolePermissionApi, DataApi dataApi) {
        this(openRolePermissionApi, dataApi, null);
    }

    /**
     * 根据当前用户登录角色与完整模块树，解析主表及所有子模块表的数据权限组合条件
     *
     * @param moduleTree 模块树元数据聚合 DTO (包含根模块与各子孙模块拓扑)
     * @param projectRelations 项目物理表关联拓扑 (向后兼容入参)
     * @return 组装好的 jOOQ Condition（若无权限配置或表未命中则返回 empty）
     */
    public Optional<Condition> resolve(
            ModuleTreeDTO moduleTree, ProjectTableRelationsDTO projectRelations) {
        if (moduleTree == null || moduleTree.getRootModule() == null) {
            return Optional.empty();
        }

        Long roleId = UserContext.getRoleId();
        if (roleId == null || openRolePermissionApi == null) {
            return Optional.empty();
        }

        List<DataRightConfigResp> permDetails;
        try {
            permDetails = openRolePermissionApi.getRoleDataPermissionDetail(roleId);
        } catch (Exception ex) {
            log.warn("获取角色数据权限详情失败: roleId={}, err={}", roleId, ex.getMessage());
            return Optional.empty();
        }

        if (CollectionUtils.isEmpty(permDetails)) {
            return Optional.empty();
        }

        // 以物理表名（统一小写）建立权限配置索引
        Map<String, DataRightConfigResp> permMap = new HashMap<>();
        for (DataRightConfigResp resp : permDetails) {
            if (resp != null && resp.getTableName() != null && !resp.getTableName().isBlank()) {
                permMap.put(resp.getTableName().trim().toLowerCase(), resp);
            }
        }

        String primaryTable = moduleTree.getRootModule().getPrimaryTable();
        Long rootModuleId = moduleTree.getRootModuleId();
        if (primaryTable == null || primaryTable.isBlank()) {
            return Optional.empty();
        }

        String projectNo =
                moduleTree.getProjectNo() != null && !moduleTree.getProjectNo().isBlank()
                        ? moduleTree.getProjectNo()
                        : (projectRelations != null && projectRelations.getProjectNo() != null
                                ? projectRelations.getProjectNo()
                                : AppContext.getProjectNo());

        // 收集模块树中所有有效的物理模块节点列表 (包含根模块与各级子孙模块)
        List<ModuleInfo> allModules = new ArrayList<>();
        if (moduleTree.getRootModule() != null) {
            allModules.add(moduleTree.getRootModule());
        }
        if (moduleTree.getModuleMap() != null) {
            for (ModuleInfo m : moduleTree.getModuleMap().values()) {
                if (m != null && !Objects.equals(m.getModuleId(), rootModuleId)) {
                    allModules.add(m);
                }
            }
        }

        // 检查是否有任何模块节点命中了数据权限配置
        boolean hasAnyTableInTree = false;
        boolean hasRestrictedTable = false;
        List<Predicate> tablePredicates = new ArrayList<>();

        for (ModuleInfo module : allModules) {
            if (module == null
                    || module.getPrimaryTable() == null
                    || module.getPrimaryTable().isBlank()) {
                continue;
            }

            String tableName = module.getPrimaryTable().trim().toLowerCase();
            Long modId = module.getModuleId();
            if (!permMap.containsKey(tableName)) {
                continue;
            }

            String cleanTable = module.getPrimaryTable().trim().replace("`", "");
            if (cleanTable.contains(".")) {
                cleanTable = cleanTable.substring(cleanTable.lastIndexOf('.') + 1);
            }
            Set<String> jsonColumns = getTableJsonColumns(projectNo, cleanTable);

            hasAnyTableInTree = true;
            DataRightConfigResp config = permMap.get(tableName);
            if (config == null || CollectionUtils.isEmpty(config.getDataList())) {
                continue;
            }

            // 过滤掉节点级全选（permissionCheckAll=1）的配置项，仅保留受限配置
            List<DataRightConfigResp.DataConfig> validConfigs =
                    config.getDataList().stream()
                            .filter(
                                    d ->
                                            d != null
                                                    && !Integer.valueOf(1)
                                                            .equals(d.getPermissionCheckAll()))
                            .collect(Collectors.toList());
            if (validConfigs.isEmpty()) {
                // 该表所有配置均为节点级全选放行，该表无限制
                continue;
            }

            // 存在针对具体主体/业务字段的限制
            hasRestrictedTable = true;

            // 按 subjectId 分组：同表下不同主体之间使用 OR 连接；同主体内部条件使用 AND 连接
            Map<Long, List<DataRightConfigResp.DataConfig>> subjectGroups = new LinkedHashMap<>();
            List<DataRightConfigResp.DataConfig> nonSubjectConfigs = new ArrayList<>();

            for (DataRightConfigResp.DataConfig dc : validConfigs) {
                if (dc.getSubjectId() != null) {
                    subjectGroups
                            .computeIfAbsent(dc.getSubjectId(), k -> new ArrayList<>())
                            .add(dc);
                } else {
                    nonSubjectConfigs.add(dc);
                }
            }

            List<Predicate> subjectPredicates = new ArrayList<>();
            for (Map.Entry<Long, List<DataRightConfigResp.DataConfig>> entry :
                    subjectGroups.entrySet()) {
                Long subjectId = entry.getKey();
                List<Predicate> singleSubjectPreds = new ArrayList<>();

                // 主体限制: subject_id EQ subjectId
                singleSubjectPreds.add(
                        ComparisonPredicate.of(
                                module.getPrimaryTable().trim(),
                                SUBJECT_ID_COLUMN,
                                "EQ",
                                subjectId));

                for (DataRightConfigResp.DataConfig dc : entry.getValue()) {
                    if (!Integer.valueOf(1).equals(dc.getSubjectCheckAll())
                            && dc.getColumnName() != null
                            && !dc.getColumnName().isBlank()) {
                        List<Object> effectiveBizNames =
                                dc.getBizNames() == null
                                        ? Collections.emptyList()
                                        : dc.getBizNames().stream()
                                                .map(JsonValueUtils::toSqlValue)
                                                .collect(Collectors.toList());
                        boolean isJson =
                                isJsonField(
                                        dc.getColumnName(),
                                        jsonColumns,
                                        dc.getBizNames(),
                                        effectiveBizNames);
                        String op =
                                (isJson && !effectiveBizNames.isEmpty()) ? "JSON_CONTAINS" : "IN";
                        singleSubjectPreds.add(
                                ComparisonPredicate.of(
                                        module.getPrimaryTable().trim(),
                                        dc.getColumnName().trim(),
                                        op,
                                        effectiveBizNames));
                    }
                }

                if (singleSubjectPreds.size() == 1) {
                    subjectPredicates.add(singleSubjectPreds.get(0));
                } else {
                    subjectPredicates.add(CompoundPredicate.and(singleSubjectPreds));
                }
            }

            // 处理无主体绑定的限制字段（若有）
            List<Predicate> nonSubjectPreds = new ArrayList<>();
            for (DataRightConfigResp.DataConfig dc : nonSubjectConfigs) {
                if (!Integer.valueOf(1).equals(dc.getSubjectCheckAll())
                        && dc.getColumnName() != null
                        && !dc.getColumnName().isBlank()) {
                    List<Object> effectiveBizNames =
                            dc.getBizNames() == null
                                    ? Collections.emptyList()
                                    : dc.getBizNames().stream()
                                            .map(JsonValueUtils::toSqlValue)
                                            .collect(Collectors.toList());
                    boolean isJson =
                            isJsonField(
                                    dc.getColumnName(),
                                    jsonColumns,
                                    dc.getBizNames(),
                                    effectiveBizNames);
                    String op = (isJson && !effectiveBizNames.isEmpty()) ? "JSON_CONTAINS" : "IN";
                    nonSubjectPreds.add(
                            ComparisonPredicate.of(
                                    module.getPrimaryTable().trim(),
                                    dc.getColumnName().trim(),
                                    op,
                                    effectiveBizNames));
                }
            }

            Predicate tableSubjectsPred = null;
            if (!subjectPredicates.isEmpty()) {
                tableSubjectsPred =
                        subjectPredicates.size() == 1
                                ? subjectPredicates.get(0)
                                : CompoundPredicate.or(subjectPredicates);
            }

            Predicate tablePred = null;
            if (tableSubjectsPred != null && !nonSubjectPreds.isEmpty()) {
                List<Predicate> combined = new ArrayList<>();
                combined.add(tableSubjectsPred);
                combined.addAll(nonSubjectPreds);
                tablePred = CompoundPredicate.and(combined);
            } else if (tableSubjectsPred != null) {
                tablePred = tableSubjectsPred;
            } else if (!nonSubjectPreds.isEmpty()) {
                tablePred =
                        nonSubjectPreds.size() == 1
                                ? nonSubjectPreds.get(0)
                                : CompoundPredicate.and(nonSubjectPreds);
            }

            if (tablePred != null) {
                tablePredicates.add(tablePred);
            }
        }

        // 1. 若当前模块树中没有任何物理表配置了数据权限，返回 empty (代表不施加数据权限限制)
        if (!hasAnyTableInTree) {
            return Optional.empty();
        }

        // 2. 若命中数据权限配置，但所有命中表均为全选放行，返回 trueCondition (全选放行)
        if (!hasRestrictedTable) {
            return Optional.of(DSL.trueCondition());
        }

        // 3. 安全防御加固：若标记了受限但未生成任何有效过滤谓词，遵循默认拒绝原则，防止空 Filter 查出全量数据越权
        if (CollectionUtils.isEmpty(tablePredicates)) {
            log.warn(
                    "[DataPermission] 存在受限表但未生成有效谓词条件，安全拦截默认拒绝: rootModuleId={}, primaryTable={}",
                    rootModuleId,
                    primaryTable);
            return Optional.of(DSL.falseCondition());
        }

        // 4. 复用底层数据引擎 dataApi.queryList(QueryReq) 求解主表 ID 集合
        // 投影规格仅声明主表 id，底层引擎自动将子表 FilterItem 上卷为 EXISTS 关联子查询并跳过子表数据抓取
        String rootCode =
                moduleTree.getRootModule() != null
                        ? moduleTree.getRootModule().getModuleCode()
                        : null;
        String view =
                (rootCode != null && !rootCode.isBlank()) ? rootCode : String.valueOf(rootModuleId);

        Predicate finalFilter =
                tablePredicates.size() == 1
                        ? tablePredicates.get(0)
                        : CompoundPredicate.and(tablePredicates);

        QueryReq queryReq =
                QueryReq.builder()
                        .view(view)
                        .columns(List.of(primaryTable.trim() + ".id"))
                        .filter(finalFilter)
                        .build();

        DataBlock block;
        try {
            block = dataApi.queryList(queryReq);
        } catch (Exception ex) {
            log.error(
                    "通过 dataApi.queryList 求解数据权限符合单据失败，遵循默认拒绝原则拦截: rootModuleId={}, primaryTable={}, err={}",
                    rootModuleId,
                    primaryTable,
                    ex.getMessage(),
                    ex);
            return Optional.of(DSL.falseCondition());
        }

        List<Long> matchedIds = extractIds(block, primaryTable);
        List<FilterItem> flattenedFilters = FilterItem.fromPredicateToList(finalFilter);
        log.info(
                "[DataPermission-QueryList] 复用 queryList 求解数据权限主表ID完成: rootModuleId={}, primaryTable={}, 过滤项数={}, 匹配ID数={}, 样例={}",
                rootModuleId,
                primaryTable,
                flattenedFilters.size(),
                matchedIds.size(),
                CommonFilterResolver.formatSample(matchedIds, 5));

        if (matchedIds.isEmpty()) {
            // 数据权限生效但没有任何记录满足，返回 falseCondition，避免放行
            return Optional.of(DSL.falseCondition());
        }

        return Optional.of(DSL.field(DSL.name(primaryTable, "id"), Long.class).in(matchedIds));
    }

    /** 提取 DataBlock 根记录的主表 ID 列表 */
    private List<Long> extractIds(DataBlock block, String primaryTable) {
        if (block == null || CollectionUtils.isEmpty(block.getRecords())) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        for (DataRow row : block.getRecords()) {
            if (row != null) {
                Long id = row.getLong(primaryTable, "id");
                if (id == null && row.getTables() != null) {
                    Object val = row.getTables().get("id");
                    if (val instanceof Number n) {
                        id = n.longValue();
                    }
                }
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    /** 从 INFORMATION_SCHEMA.COLUMNS 查询指定表的 JSON 列集合 (通过 ConfigApi 缓存门面) */
    private Set<String> getTableJsonColumns(String projectNo, String tableName) {
        if (configApi == null || tableName == null || tableName.isBlank()) {
            return Collections.emptySet();
        }
        try {
            Set<String> jsonCols = configApi.getTableJsonColumnNames(projectNo, tableName);
            return jsonCols != null ? jsonCols : Collections.emptySet();
        } catch (Exception ex) {
            log.warn("通过 configApi 查询表 {} 的 JSON 列集合失败: err={}", tableName, ex.getMessage());
            return Collections.emptySet();
        }
    }

    /** 判断字段是否为 JSON 结构字段 (基于从 INFORMATION_SCHEMA 查出的列集合或取值特征) */
    private static boolean isJsonField(
            String columnName,
            Set<String> jsonColumns,
            List<Object> rawBizNames,
            List<Object> effectiveBizNames) {
        if (columnName != null && jsonColumns != null && !jsonColumns.isEmpty()) {
            String cleanCol = columnName.trim().replace("`", "").toLowerCase();
            if (cleanCol.contains(".")) {
                cleanCol = cleanCol.substring(cleanCol.lastIndexOf('.') + 1);
            }
            if (jsonColumns.contains(cleanCol)) {
                return true;
            }
        }
        if (rawBizNames != null
                && rawBizNames.stream()
                        .anyMatch(DataPermissionConditionResolver::isJsonStructureValue)) {
            return true;
        }
        if (effectiveBizNames != null
                && effectiveBizNames.stream()
                        .anyMatch(DataPermissionConditionResolver::isJsonStructureValue)) {
            return true;
        }
        return false;
    }

    private static boolean isJsonStructureValue(Object val) {
        if (val == null) {
            return false;
        }
        if (val instanceof Map || val instanceof Collection) {
            return true;
        }
        if (val instanceof String s) {
            String trimmed = s.trim();
            return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                    || (trimmed.startsWith("[") && trimmed.endsWith("]"));
        }
        return false;
    }

    /** 向后兼容快捷方法：根据表名直接解析单表数据权限 */
    public Optional<Condition> resolve(String primaryTable) {
        ModuleTreeDTO dummyTree =
                ModuleTreeDTO.builder()
                        .rootModuleId(1L)
                        .rootModule(
                                ModuleInfo.builder()
                                        .moduleId(1L)
                                        .primaryTable(primaryTable)
                                        .build())
                        .build();
        return resolve(dummyTree, null);
    }
}
