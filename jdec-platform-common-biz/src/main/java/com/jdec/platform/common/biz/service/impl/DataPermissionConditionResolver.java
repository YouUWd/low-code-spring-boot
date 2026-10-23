package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.response.DataRightConfigResp;
import com.jdec.platform.config.open.api.OpenRolePermissionApi;
import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.request.TableSpec;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.utils.JsonValueUtils;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.impl.DSL;
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
@RequiredArgsConstructor
public class DataPermissionConditionResolver {

    public static final String SUBJECT_ID_COLUMN = "subject_id";

    private final OpenRolePermissionApi openRolePermissionApi;
    private final DataApi dataApi;

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
        List<FilterItem> dataPermFilters = new ArrayList<>();

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

            hasAnyTableInTree = true;
            DataRightConfigResp config = permMap.get(tableName);
            if (config == null || CollectionUtils.isEmpty(config.getDataList())) {
                continue;
            }

            // 若配置了节点级全选，则该表无限制放行
            boolean isPermissionCheckAll =
                    config.getDataList().stream()
                            .anyMatch(
                                    d ->
                                            d != null
                                                    && Integer.valueOf(1)
                                                            .equals(d.getPermissionCheckAll()));
            if (isPermissionCheckAll) {
                continue;
            }

            // 存在针对具体主体/业务字段的限制
            hasRestrictedTable = true;
            for (DataRightConfigResp.DataConfig dc : config.getDataList()) {
                if (dc == null) {
                    continue;
                }
                Long subjectId = dc.getSubjectId();
                Integer subjectCheckAll = dc.getSubjectCheckAll();
                String columnName = dc.getColumnName();
                List<Object> bizNames = dc.getBizNames();

                if (subjectId != null) {
                    dataPermFilters.add(
                            FilterItem.builder()
                                    .moduleId(modId)
                                    .tableName(module.getPrimaryTable().trim())
                                    .columnName(SUBJECT_ID_COLUMN)
                                    .operator("EQ")
                                    .value(subjectId)
                                    .build());
                }

                if (!Integer.valueOf(1).equals(subjectCheckAll)
                        && columnName != null
                        && !columnName.isBlank()) {
                    // 业务值为 JSON 对象/数组时转为 JSON 文本，避免集合被直接绑定给 JDBC
                    List<Object> effectiveBizNames =
                            bizNames == null
                                    ? Collections.emptyList()
                                    : bizNames.stream()
                                            .map(JsonValueUtils::toSqlValue)
                                            .collect(Collectors.toList());
                    dataPermFilters.add(
                            FilterItem.builder()
                                    .moduleId(modId)
                                    .tableName(module.getPrimaryTable().trim())
                                    .columnName(columnName.trim())
                                    .operator("IN")
                                    .value(effectiveBizNames)
                                    .build());
                }
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

        // 3. 复用底层数据引擎 dataApi.queryList(QueryReq) 求解主表 ID 集合
        // 投影规格仅声明主表 id，底层引擎自动将子表 FilterItem 上卷为 EXISTS 关联子查询并跳过子表数据抓取
        QueryReq queryReq =
                QueryReq.builder()
                        .moduleId(rootModuleId)
                        .tables(List.of(TableSpec.of(rootModuleId, primaryTable, List.of("id"))))
                        .filters(dataPermFilters)
                        .build();

        DataBlock block;
        try {
            block = dataApi.queryList(queryReq);
        } catch (Exception ex) {
            log.warn(
                    "通过 dataApi.queryList 求解数据权限符合单据失败: rootModuleId={}, primaryTable={}, err={}",
                    rootModuleId,
                    primaryTable,
                    ex.getMessage());
            return Optional.empty();
        }

        List<Long> matchedIds = extractIds(block, primaryTable);
        log.info(
                "[DataPermission-QueryList] 复用 queryList 求解数据权限主表ID完成: rootModuleId={}, primaryTable={}, 过滤项数={}, 匹配ID数={}, 样例={}",
                rootModuleId,
                primaryTable,
                dataPermFilters.size(),
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
