package com.jdec.platform.dataengine.biz.service;

import cn.hutool.core.bean.BeanUtil;
import com.jdec.platform.config.api.*;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.response.SysModuleResp;
import com.jdec.platform.config.api.dto.response.SysModuleSimpleTreeResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.dataengine.api.dto.enums.QueryType;
import com.jdec.platform.dataengine.api.dto.model.*;
import com.jdec.platform.dataengine.api.dto.request.DynamicDetailQueryReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicQueryReq;
import com.jdec.platform.dataengine.api.dto.request.VersionListQueryReq;
import com.jdec.platform.dataengine.api.dto.response.DynamicQueryResp;
import com.jdec.platform.dataengine.api.dto.response.VersionListResp;
import com.jdec.platform.dataengine.biz.config.service.DataModuleConfigService;
import com.jdec.platform.dataengine.biz.dsl.JooqConditionBuilder;
import com.jdec.platform.dataengine.biz.dsl.JooqContextFactory;
import com.jdec.platform.dataengine.biz.dsl.JooqSqlBuilder;
import com.jdec.platform.dataengine.biz.dto.ModuleField;
import com.jdec.platform.dataengine.biz.dto.ModuleFilterInfo;
import com.jdec.platform.dataengine.biz.dto.ModuleTable;
import com.jdec.platform.dataengine.biz.dto.ModuleTableHeaderDTO;
import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import com.jdec.platform.dataengine.biz.dto.context.DetailQueryContext;
import com.jdec.platform.dataengine.biz.dto.context.ListQueryContext;
import com.jdec.platform.dataengine.biz.third.api.DetailBizQueryService;
import com.jdec.platform.dataengine.biz.third.api.ListBizQueryService;
import com.jdec.platform.dataengine.biz.third.api.data.DetailDataProcessFactory;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import java.util.*;
import java.util.Comparator;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/** 动态查询服务 (基于 jOOQ 两阶段分页与 Table-First 极简组装) */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicQueryService {

    private final MetadataCacheService metadataCacheService;
    private final PermissionFilterService permissionFilterService;
    private final FieldTransformService fieldTransformService;
    private final JooqContextFactory jooqContextFactory;
    private final JooqSqlBuilder jooqSqlBuilder;
    private final JooqConditionBuilder jooqConditionBuilder;

    private final ListBizQueryService listBizQueryService;
    private final DetailBizQueryService detailBizQueryService;

    private final SysModuleApi sysModuleApi;
    private final SysApprovalChainConfigApi sysApprovalChainConfigApi;

    private final DataModuleConfigService dataModuleConfigService;

    private final DetailDataProcessFactory detailDataProcessFactory;

    private final ConfigApi configApi;

    /** 一体化列表分页查询 */
    public DynamicQueryResp query(DynamicQueryReq req) {

        ListQueryContext.ListQueryContextBuilder listContextBuilder =
                ListQueryContext.builder()
                        .subjectId(AppContext.getSubjectId())
                        .roleId(UserContext.getRoleId());

        // TODO 通过menuId和moduleId查审批链配置获取
        Boolean hasApproval =
                sysApprovalChainConfigApi.getApprovalStatus(List.of(req.getModuleId()));

        QueryType queryType;

        if (hasApproval) {
            switch (req.getType()) {
                case 1 -> queryType = QueryType.LIST_APPROVAL_TODO;
                case 2 -> queryType = QueryType.LIST_APPROVAL_DONE;
                case 3 -> queryType = QueryType.LIST_APPROVAL_ALL;
                default -> queryType = QueryType.LIST_APPROVAL_ALL;
            }
        } else {
            queryType = QueryType.LIST;
        }

        listContextBuilder.queryType(queryType);

        listContextBuilder
                .order(req.getOrder())
                .search(req.getSearch())
                .type(req.getType())
                .pageIndex(req.getPageIndex())
                .pageSize(req.getPageSize())
                .moduleId(req.getModuleId());

        // TODO 填充其他查询参数 待完成

        Map<Object, Object> listDataMap =
                listBizQueryService.queryListBiz(listContextBuilder.build());

        Long moduleId = req.getModuleId();
        SysModuleResp completeResp = metadataCacheService.getModuleComplete(moduleId);
        String primaryTable = completeResp.getModule().getPrimaryTable();
        List<ModuleTableHeaderDTO> headers =
                permissionFilterService.filterReadableHeaders(completeResp);

        DSLContext dsl = jooqContextFactory.getContext();
        Condition condition =
                jooqConditionBuilder.buildConditions(
                        primaryTable, req.getFilters(), headers, AppContext.getSubjectId());

        // 1. 第一阶段: 查询主表精确总数 COUNT
        int total =
                dsl.fetchCount(
                        jooqSqlBuilder
                                .buildSelectFrom(dsl, completeResp, primaryTable)
                                .where(condition));

        // 2. 分页数据查询
        int pageIndex = req.getPageIndex() > 0 ? req.getPageIndex() : 1;
        int pageSize = req.getPageSize() > 0 ? req.getPageSize() : 20;
        int offset = (pageIndex - 1) * pageSize;

        var selectStep =
                jooqSqlBuilder.buildSelectFrom(dsl, completeResp, primaryTable).where(condition);
        if (req.getOrderBy() != null && !req.getOrderBy().isBlank()) {
            if ("ASC".equalsIgnoreCase(req.getOrderDirection())) {
                selectStep.orderBy(DSL.field(DSL.name(req.getOrderBy())).asc());
            } else {
                selectStep.orderBy(DSL.field(DSL.name(req.getOrderBy())).desc());
            }
        }
        Result<Record> recordsResult = selectStep.limit(pageSize).offset(offset).fetch();

        // 3. 提取主表记录并进行内存转换
        List<Map<String, Object>> primaryRows = new ArrayList<>();
        List<Long> primaryIds = new ArrayList<>();
        for (Record r : recordsResult) {
            Map<String, Object> map = new HashMap<>(r.intoMap());
            primaryRows.add(map);
            Object idVal = map.get("id");
            if (idVal instanceof Number num) {
                primaryIds.add(num.longValue());
            }
        }
        fieldTransformService.transformRecords(primaryRows, completeResp);

        // 4. 第二阶段: 批量下钻查询 1:N 关联从表
        //        Map<String, Map<Long, List<Map<String, Object>>>> multiTableDataMap =
        //                fetchOneToManyTables(dsl, completeResp, primaryIds);

        // 5. 组装行记录列表 (Table-First 纯粹 Map: record[tableName] = record/records)
        List<Map<String, Object>> structuredRecords = new ArrayList<>();
        for (Map<String, Object> row : primaryRows) {
            Map<String, Object> rowTables = new LinkedHashMap<>();
            Long rowId = row.get("id") instanceof Number num ? num.longValue() : null;

            // 主表数据
            rowTables.put(primaryTable, row);

            // 关联表数据 (1:1 / N:1 为单对象, 1:N 为对象列表)
            //            if (completeResp.getModuleTables() != null) {
            //                for (ModuleTableDTO tableDto : completeResp.getModuleTables()) {
            //                    String tName = tableDto.getTableName();
            //                    String rType = tableDto.getRelationType();
            //                    if ("1:N".equalsIgnoreCase(rType) ||
            // "ONE_TO_MANY".equalsIgnoreCase(rType)) {
            //                        List<Map<String, Object>> subRows =
            //                                multiTableDataMap
            //                                        .getOrDefault(tName, Collections.emptyMap())
            //                                        .getOrDefault(rowId, Collections.emptyList());
            //                        rowTables.put(tName, subRows);
            //                    } else {
            //                        // 1:1 或 N:1
            //                        rowTables.put(tName, row);
            //                    }
            //                }
            //            }
            //            structuredRecords.add(rowTables);
        }

        // 6. 组装 Meta 与 Pagination
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("moduleId", moduleId);
        meta.put("moduleCode", completeResp.getModule().getModuleCode());
        meta.put("moduleName", completeResp.getModule().getModuleName());
        meta.put("primaryTable", primaryTable);
        // meta.put("detailModuleId", completeResp.getModule().getDetailModuleId());
        meta.put("tableHeader", headers);

        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("pageIndex", pageIndex);
        pagination.put("pageSize", pageSize);
        pagination.put("total", total);
        pagination.put("totalPages", (int) Math.ceil((double) total / pageSize));

        return DynamicQueryResp.builder()
                .meta(meta)
                .pagination(pagination)
                .records(structuredRecords)
                .build();
    }

    /** 动态主子表详情查询 (顶层包含 moduleId 等标识，元数据包含 fieldNames 与 permissions) */
    public List<NestedModuleDataDTO> getDetail(
            DynamicDetailQueryReq dynamicDetailQueryReq, String btnType) {
        List<NestedModuleDataDTO> resultList = new ArrayList<>();

        // 获取模块树
        List<SysModuleSimpleTreeResp> moduleNodeResps =
                sysModuleApi.getAvailableModuleTree(
                        1, AppContext.getProjectNo(), AppContext.getSubjectId());

        if (CollectionUtils.isEmpty(moduleNodeResps)) {
            log.debug("菜单树查询结果未空，返回空列表。");
            return resultList;
        }

        // 以rootModuleId为根节点的模块树
        SysModuleSimpleTreeResp rootNode =
                findSubTreeByValue(moduleNodeResps, dynamicDetailQueryReq.getModuleId());

        Set<Long> involveModules = new HashSet<>();
        involveModules.add(dynamicDetailQueryReq.getModuleId());

        Set<Long> fieldDirectModuleIds = getRelateModules(dynamicDetailQueryReq.getFilterFields());
        involveModules.addAll(fieldDirectModuleIds);

        SysModuleSimpleTreeResp pruneMenus = pruneForest(rootNode, involveModules);

        Map<Long, List<ModuleFieldDTO>> moduleIdToFieldMap =
                getModuleFields(dynamicDetailQueryReq.getFilterFields());

        boolean history = false;

        Boolean hasApproval =
                sysApprovalChainConfigApi.getApprovalStatus(
                        List.of(dynamicDetailQueryReq.getModuleId()));

        QueryType queryType;

        if (hasApproval) {
            if (!history) {
                queryType = QueryType.DETAIL_APPROVAL_CURRENT;
            } else {
                queryType = QueryType.DETAIL_APPROVAL_HISTORY;
            }
        } else {
            queryType = QueryType.DETAIL;
        }

        switch (queryType) {
            case DETAIL, DETAIL_APPROVAL_CURRENT -> {
                recursiveQuery(
                        pruneMenus,
                        resultList,
                        dynamicDetailQueryReq.getId(),
                        btnType,
                        true,
                        moduleIdToFieldMap,
                        queryType);
            }
            case DETAIL_APPROVAL_HISTORY ->
                    getHistoryDetailData(
                            pruneMenus,
                            resultList,
                            dynamicDetailQueryReq,
                            btnType,
                            moduleIdToFieldMap);
            default -> throw new IllegalStateException("Unexpected value: " + queryType);
        }

        // 需要版本对比数据时才进行组装
        if (Objects.nonNull(dynamicDetailQueryReq.getShowVersionCompare())
                && dynamicDetailQueryReq.getShowVersionCompare()) {

            List<NestedModuleDataDTO> preVersionResultList = new ArrayList<>();

            // 查找当前数据的上一版本
            Record preVersionRecord = getPreVersion(rootNode, dynamicDetailQueryReq.getId());
            if (Objects.nonNull(preVersionRecord)) {
                // 查找上一版本的详情数据
                Map<String, Object> preVersionResultMap = preVersionRecord.intoMap();

                recursiveQuery(
                        pruneMenus,
                        preVersionResultList,
                        Long.parseLong(String.valueOf(preVersionResultMap.get("id"))),
                        btnType,
                        true,
                        moduleIdToFieldMap,
                        queryType);
            }

            DataIntegrationContext dataIntegrationContext =
                    DataIntegrationContext.builder()
                            .moduleId(dynamicDetailQueryReq.getModuleId())
                            .id(dynamicDetailQueryReq.getId())
                            .version(dynamicDetailQueryReq.getVersion())
                            .moduleIdToFieldMap(moduleIdToFieldMap)
                            .queryType(queryType)
                            .preVersionData(preVersionResultList)
                            .build();

            detailDataProcessFactory.processData(resultList, dataIntegrationContext);
        }

        return resultList;
    }

    private Record getPreVersion(SysModuleSimpleTreeResp rootNode, Long id) {
        SysModuleInfo sysModuleInfo =
                dataModuleConfigService.getModuleCompleteById(
                        AppContext.getProjectNo(), AppContext.getSubjectId(), rootNode.getId());

        DSLContext dslContext = jooqContextFactory.getContext();

        SelectJoinStep<org.jooq.Record> query =
                dslContext.select(DSL.asterisk()).from(DSL.name(sysModuleInfo.getPrimaryTable()));
        // 构造SQL 查询条件
        List<Condition> conditions = new ArrayList<>();
        conditions.add(DSL.field(DSL.name(sysModuleInfo.getPrimaryTable(), "id")).eq(id));

        query.where(conditions);

        Record recordsResult = query.fetchOne();
        if (Objects.nonNull(recordsResult)) {
            Long version =
                    recordsResult.intoMap().get("version_no") == null
                            ? null
                            : Long.parseLong(
                                    String.valueOf(recordsResult.intoMap().get("version_no")));
            String businessNo =
                    recordsResult.intoMap().get("business_no") == null
                            ? null
                            : String.valueOf(recordsResult.intoMap().get("business_no"));
            if (Objects.isNull(version) || Objects.isNull(businessNo)) {
                return null;
            }

            // 查询上一版本的主模块主表数据，获取数据ID和版本号
            DSLContext dslContextPreVersion = jooqContextFactory.getContext();

            SelectJoinStep<org.jooq.Record> preVersionQuery =
                    dslContextPreVersion
                            .select(DSL.asterisk())
                            .from(DSL.name(sysModuleInfo.getPrimaryTable()));
            // 构造SQL 查询条件
            List<Condition> preVersionConditions = new ArrayList<>();
            preVersionConditions.add(
                    DSL.field(DSL.name(sysModuleInfo.getPrimaryTable(), "business_no"))
                            .eq(businessNo));

            preVersionConditions.add(
                    DSL.field(DSL.name(sysModuleInfo.getPrimaryTable(), "version_no"))
                            .lessThan(version));

            preVersionConditions.add(
                    DSL.field(DSL.name(sysModuleInfo.getPrimaryTable(), "business_status")).eq(99));

            preVersionQuery.where(preVersionConditions);

            preVersionQuery.stream().limit(1);

            Record preVersionResult = preVersionQuery.fetchOne();
            return preVersionResult;
        } else {
            return null;
        }
    }

    private Map<Long, List<ModuleFieldDTO>> getModuleFields(List<Long> filterFields) {
        List<ModuleFieldDTO> fieldDTOs = dataModuleConfigService.listFieldsByIds(filterFields);

        return fieldDTOs.stream().collect(Collectors.groupingBy(ModuleFieldDTO::getModuleId));
    }

    private Set<Long> getRelateModules(List<Long> filterFields) {
        List<ModuleFieldDTO> fieldDTOs = dataModuleConfigService.listFieldsByIds(filterFields);

        return fieldDTOs.stream().map(ModuleFieldDTO::getModuleId).collect(Collectors.toSet());
    }

    private void recursiveQuery(
            SysModuleSimpleTreeResp rootNode,
            List<NestedModuleDataDTO> resultList,
            Long id,
            String btnType,
            Boolean topQuery,
            Map<Long, List<ModuleFieldDTO>> moduleIdToFieldMap,
            QueryType queryType) {
        // ========= 模式2：树形关联查询，多个模块属于同一棵树 =========
        // 取根模块（moduleCodeList第一个为根节点，其余是树内子模块）
        //        SysModuleSimpleTreeResp rootNode = findSubTreeByValue(moduleTree, topMenuId);
        if (rootNode == null) {
            return;
        }

        // filterFields为空时，返回本模块所有字段
        if (moduleIdToFieldMap.isEmpty()) {
            List<ModuleFieldDTO> moduleFields =
                    dataModuleConfigService.listFieldsByModuleIds(Arrays.asList(rootNode.getId()));
            moduleIdToFieldMap.put(
                    rootNode.getId(),
                    moduleFields.stream()
                            .filter(ele -> ele.getModuleId().equals(rootNode.getId()))
                            .collect(Collectors.toList()));
        }

        List<ModuleFieldDTO> moduleFileds = new ArrayList<>();
        if (moduleIdToFieldMap.containsKey(rootNode.getId())) {
            moduleFileds = moduleIdToFieldMap.get(rootNode.getId());
        }

        List<ModuleFieldDTO> finalModuleFileds = moduleFileds;
        moduleFileds.addAll(
                getIdAndRelateIdField(rootNode.getId()).stream()
                        .filter(
                                ele ->
                                        !finalModuleFileds.stream()
                                                .anyMatch(
                                                        item ->
                                                                item.getTableName()
                                                                                .equals(
                                                                                        ele
                                                                                                .getTableName())
                                                                        && item.getColumnName()
                                                                                .equals(
                                                                                        ele
                                                                                                .getColumnName())))
                        .collect(Collectors.toList()));

        DynamicQueryReq singleQueryDTO =
                DynamicQueryReq.builder()
                        .moduleId(rootNode.getId())
                        .menuId(rootNode.getId())
                        .mainDataId(id)
                        .btnType(btnType)
                        .fields(moduleFileds)
                        .queryType(queryType)
                        .build();

        NestedModuleDataDTO moduleData =
                queryModule(singleQueryDTO, rootNode, true, topQuery, moduleIdToFieldMap);
        resultList.add(moduleData);
    }

    private NestedModuleDataDTO queryModule(
            DynamicQueryReq queryReq,
            SysModuleSimpleTreeResp rootNode,
            Boolean recursive,
            Boolean topQUery,
            Map<Long, List<ModuleFieldDTO>> moduleIdToFieldMap) {
        NestedModuleDataDTO.NestedModuleDataDTOBuilder builder = NestedModuleDataDTO.builder();

        // 非顶层模块查询，需要设置关联键作为查询条件
        if (!topQUery) {
            queryReq.setMainDataId(null);
            queryReq.setRelateIds(queryReq.getRelateIds());
        }

        // TODO 通过menuId和moduleId查审批链配置获取
        Boolean hasApproval =
                sysApprovalChainConfigApi.getApprovalStatus(List.of(queryReq.getModuleId()));

        queryReq.setType(hasApproval ? 3 : 4);

        List<RecordDataDTO> currentModuleData = new ArrayList<>();

        currentModuleData = querySingleModule(rootNode, queryReq, queryReq.getQueryType());

        if (!recursive || rootNode.getChildren() == null || rootNode.getChildren().isEmpty()) {
            return builder.moduleId(rootNode.getId())
                    .menuId(rootNode.getId())
                    .record(currentModuleData)
                    .build();
        }

        Set<Long> primaryIds = getPrimaryIdSet(currentModuleData, rootNode.getId());

        SysModuleInfo sysModuleInfo =
                dataModuleConfigService.getModuleCompleteById(
                        AppContext.getProjectNo(), AppContext.getSubjectId(), rootNode.getId());

        Boolean virtualModule = false;

        if (StringUtils.isBlank(sysModuleInfo.getPrimaryTable())
                && CollectionUtils.isEmpty(primaryIds)) {
            log.debug("模块id:{},模块名称:{}为虚模块", rootNode.getId(), rootNode.getModuleName());
            primaryIds.addAll(queryReq.getRelateIds());
            currentModuleData.add(RecordDataDTO.builder().children(new ArrayList<>()).build());
            virtualModule = true;
        } else if (CollectionUtils.isEmpty(primaryIds)) {
            log.debug("模块id:{},模块名称:{}不是虚模块,但没有模块数据。", rootNode.getId(), rootNode.getModuleName());
            return builder.moduleId(rootNode.getId())
                    .menuId(rootNode.getId())
                    .record(currentModuleData)
                    .build();
        }

        for (SysModuleSimpleTreeResp childNode : rootNode.getChildren()) {
            List<ModuleFieldDTO> moduleFileds = new ArrayList<>();
            if (moduleIdToFieldMap.containsKey(childNode.getId())) {
                log.debug("模块id:{},模块名称:{}有字段在查询列表,", childNode.getId(), childNode.getModuleName());
                moduleFileds = moduleIdToFieldMap.get(childNode.getId());
            }

            List<ModuleFieldDTO> finalModuleFileds = moduleFileds;
            moduleFileds.addAll(
                    getIdAndRelateIdField(childNode.getId()).stream()
                            .filter(
                                    ele ->
                                            !finalModuleFileds.stream()
                                                    .anyMatch(
                                                            item ->
                                                                    item.getTableName()
                                                                                    .equals(
                                                                                            ele
                                                                                                    .getTableName())
                                                                            && item.getColumnName()
                                                                                    .equals(
                                                                                            ele
                                                                                                    .getColumnName())))
                            .collect(Collectors.toList()));

            // 组装当前模块查询参数
            DynamicQueryReq singleQueryDTO =
                    DynamicQueryReq.builder()
                            .moduleId(childNode.getId())
                            .mainDataId(queryReq.getMainDataId())
                            .relateIds(new ArrayList<>(primaryIds))
                            .btnType(queryReq.getBtnType())
                            .fields(moduleFileds)
                            .queryType(queryReq.getQueryType())
                            .build();
            NestedModuleDataDTO childResultList =
                    queryModule(singleQueryDTO, childNode, true, false, moduleIdToFieldMap);

            ProjectTableRelationsDTO projectTableRelationsDTO =
                    configApi.getProjectTableRelations(AppContext.getProjectNo());

            if (virtualModule) {
                currentModuleData
                        .get(0)
                        .getChildren()
                        .addAll(
                                filterSubModuleList(
                                        List.of(childResultList),
                                        primaryIds,
                                        sysModuleInfo,
                                        projectTableRelationsDTO));
            } else {
                if (CollectionUtils.isEmpty(currentModuleData)) {
                    currentModuleData.add(
                            RecordDataDTO.builder()
                                    .row(new HashMap<>())
                                    .children(new ArrayList<>())
                                    .build());
                }
                for (RecordDataDTO vo : currentModuleData) {
                    if (vo.getRow().isEmpty()) {
                        log.debug(
                                "子模块id:{},模块名称:{}是虚模块",
                                childNode.getId(),
                                childNode.getModuleName());
                        vo.getChildren().add(childResultList);
                    } else {
                        Long primaryId =
                                extractFieldValue(
                                        vo.getRow(), "id", sysModuleInfo.getPrimaryTable());
                        vo.getChildren()
                                .addAll(
                                        filterSubModuleList(
                                                List.of(childResultList),
                                                Set.of(primaryId),
                                                sysModuleInfo,
                                                projectTableRelationsDTO));
                    }
                }
            }
        }

        return builder.moduleId(rootNode.getId())
                .menuId(rootNode.getId())
                .record(currentModuleData)
                .build();
    }

    private List<ModuleFieldDTO> getIdAndRelateIdField(Long id) {
        SysModuleInfo moduleInfo =
                dataModuleConfigService.getModuleCompleteById(
                        AppContext.getProjectNo(), AppContext.getSubjectId(), id);

        Set<String> moduleTables =
                moduleInfo.getFieldList().stream()
                        .map(ModuleFieldDTO::getTableName)
                        .collect(Collectors.toSet());

        ProjectTableRelationsDTO projectTableRelationsDTO =
                configApi.getProjectTableRelations(AppContext.getProjectNo());

        List<ModuleFieldDTO> moduleFields = new ArrayList<>();

        for (String tableName : moduleTables) {

            if (projectTableRelationsDTO.getReverseRelations() != null
                    && projectTableRelationsDTO.getReverseRelations().containsKey(tableName)) {

                List<com.jdec.platform.config.api.dto.common.TableRelationDTO> relationList =
                        projectTableRelationsDTO.getReverseRelations().get(tableName);

                if (CollectionUtils.isEmpty(relationList)) {
                    continue;
                }

                if (StringUtils.isNotBlank(relationList.get(0).getJoinField())) {
                    moduleFields.add(
                            ModuleFieldDTO.builder()
                                    .moduleId(id)
                                    .tableName(tableName)
                                    .columnName(relationList.get(0).getJoinField())
                                    .build());
                }
            }

            moduleFields.add(
                    ModuleFieldDTO.builder()
                            .moduleId(id)
                            .tableName(tableName)
                            .columnName("id")
                            .build());
        }

        return moduleFields;
    }

    /**
     * @param subModuleList 当前子模块列表
     * @param parentPkSet 上层父主键id集合
     */
    public List<NestedModuleDataDTO> filterSubModuleList(
            List<NestedModuleDataDTO> subModuleList,
            Set<Long> parentPkSet,
            SysModuleInfo parentModuleInfo,
            ProjectTableRelationsDTO tableRelationsDTO) {
        if (subModuleList == null
                || subModuleList.isEmpty()
                || parentPkSet == null
                || parentPkSet.isEmpty()) {
            return Collections.emptyList();
        }

        List<NestedModuleDataDTO> resultList = new ArrayList<>();
        for (NestedModuleDataDTO subModule : subModuleList) {
            NestedModuleDataDTO newSubModule = new NestedModuleDataDTO();
            newSubModule.setModuleId(subModule.getModuleId());
            newSubModule.setMenuId(subModule.getMenuId());

            SysModuleInfo sysModuleInfo =
                    dataModuleConfigService.getModuleCompleteById(
                            AppContext.getProjectNo(),
                            AppContext.getSubjectId(),
                            subModule.getModuleId());

            com.jdec.platform.config.api.dto.common.TableRelationDTO tableRelationDTO =
                    tableRelationsDTO.findDirectRelation(
                            parentModuleInfo.getPrimaryTable(), sysModuleInfo.getPrimaryTable());

            if (Objects.isNull(tableRelationDTO)) {
                newSubModule.setRecord(subModule.getRecord());
                resultList.add(newSubModule);
            } else {
                List<RecordDataDTO> filteredRecords =
                        subModule.getRecord().stream()
                                .filter(
                                        record -> {
                                            // 读取子表的【外键字段】
                                            Long fkVal =
                                                    extractFieldValue(
                                                            record.getRow(),
                                                            tableRelationDTO.getJoinField(),
                                                            sysModuleInfo.getPrimaryTable());
                                            if (fkVal == null) {
                                                return false;
                                            }
                                            // 子表外键 在父主键集合内，保留
                                            return parentPkSet.contains(fkVal);
                                        })
                                .map(
                                        record -> {
                                            RecordDataDTO newRecord = new RecordDataDTO();
                                            newRecord.setRow(record.getRow());
                                            // 获取当前这条记录主键，作为下一级子模块的父主键集合
                                            Long currentPk =
                                                    extractFieldValue(
                                                            record.getRow(),
                                                            "id",
                                                            sysModuleInfo.getPrimaryTable());
                                            Set<Long> childPkSet = Collections.singleton(currentPk);
                                            // 下一层外键固定 member_id
                                            List<NestedModuleDataDTO> childSub =
                                                    filterSubModuleList(
                                                            record.getChildren(),
                                                            childPkSet,
                                                            sysModuleInfo,
                                                            tableRelationsDTO);
                                            newRecord.setChildren(childSub);
                                            return newRecord;
                                        })
                                .collect(Collectors.toList());
                newSubModule.setRecord(filteredRecords);
                resultList.add(newSubModule);
            }
        }

        return resultList;
    }

    // 通用提取字段
    private static Long extractFieldValue(
            Map<String, Object> row, String fieldName, String tableName) {
        if (row == null || row.isEmpty()) return null;
        Object val = row.get(tableName + "." + fieldName);
        if (val == null) return null;
        return Long.parseLong(val.toString());
    }

    private Set<Long> getPrimaryIdSet(List<RecordDataDTO> currentModuleData, Long moduleId) {
        Set<Long> primaryIds = new HashSet<>();
        SysModuleInfo sysModuleInfo = null;
        if (!CollectionUtils.isEmpty(currentModuleData)) {
            sysModuleInfo =
                    dataModuleConfigService.getModuleCompleteById(
                            AppContext.getProjectNo(), AppContext.getSubjectId(), moduleId);
        }

        for (RecordDataDTO recordDataDTO : currentModuleData) {
            if (!recordDataDTO.getRow().isEmpty()) {
                String primaryTableName = sysModuleInfo.getPrimaryTable();

                Map<String, Object> primaryTableData = new HashMap<>();
                // 获取primaryTableName数据
                for (Map.Entry<String, Object> entry : recordDataDTO.getRow().entrySet()) {
                    if (entry.getKey().contains(primaryTableName)) {
                        primaryTableData.put(entry.getKey(), entry.getValue());
                    }
                }

                if (Objects.nonNull(primaryTableData)) {
                    if (primaryTableData instanceof List) {
                        List<Map<String, Object>> moduleTableData =
                                (List<Map<String, Object>>) primaryTableData;

                        if (Objects.nonNull(moduleTableData)
                                && !CollectionUtils.isEmpty(moduleTableData)) {
                            primaryIds.addAll(
                                    moduleTableData.stream()
                                            .filter(
                                                    ele ->
                                                            ele.containsKey(
                                                                    primaryTableName + "." + "id"))
                                            .map(
                                                    item ->
                                                            Long.parseLong(
                                                                    String.valueOf(
                                                                            item.get(
                                                                                    primaryTableName
                                                                                            + "."
                                                                                            + "id"))))
                                            .collect(Collectors.toList()));
                        }
                    } else {
                        if (primaryTableData.containsKey(primaryTableName + "." + "id")) {
                            primaryIds.add(
                                    Long.parseLong(
                                            String.valueOf(
                                                    primaryTableData.get(
                                                            primaryTableName + "." + "id"))));
                        }
                    }
                }
            }
        }
        return primaryIds;
    }

    private List<RecordDataDTO> querySingleModule(
            SysModuleSimpleTreeResp node, DynamicQueryReq queryDTO, QueryType queryType) {
        List<RecordDataDTO> resultList = new ArrayList<>();
        if (node.getModuleCode().contains("LIST")) {
            // TODO 查找列表模块数据
            DynamicQueryResp listData = query(queryDTO);
            Map<String, Object> targetMap = new HashMap<>();
            BeanUtil.beanToMap(listData, targetMap, false, true);
            RecordDataDTO.builder().row(targetMap);
            resultList.add(null);
        } else {
            // 查找详情模块数据
            resultList.addAll(getDetailModuleData(node.getId(), queryType, queryDTO));
        }
        return resultList;
    }

    private List<RecordDataDTO> getDetailModuleData(
            Long moduleId, QueryType queryType, DynamicQueryReq queryReq) {

        Long subjectId = AppContext.getSubjectId();
        Long roleId = UserContext.getRoleId();

        DetailQueryContext detailQueryContext =
                DetailQueryContext.builder()
                        .btnType(queryReq.getBtnType())
                        .id(queryReq.getMainDataId())
                        .relateIds(queryReq.getRelateIds())
                        .menuId(queryReq.getMenuId())
                        .moduleId(moduleId)
                        .roleId(roleId)
                        .subjectId(subjectId)
                        .queryType(queryType)
                        .fields(queryReq.getFields())
                        .build();

        List<RecordDataDTO> recordDataDTOList =
                detailBizQueryService.queryDetailBiz(detailQueryContext);

        return recordDataDTOList;
    }

    private void getHistoryDetailData(
            SysModuleSimpleTreeResp moduleTree,
            List<NestedModuleDataDTO> resultList,
            DynamicDetailQueryReq dynamicDetailQueryReq,
            String btnType,
            Map<Long, List<ModuleFieldDTO>> moduleFieldsMap) {

        Long subjectId = AppContext.getSubjectId();
        Long roleId = UserContext.getRoleId();

        QueryType queryType = QueryType.DETAIL_APPROVAL_HISTORY;

        DetailQueryContext detailQueryContext =
                DetailQueryContext.builder()
                        .btnType(btnType)
                        .id(dynamicDetailQueryReq.getId())
                        .version(dynamicDetailQueryReq.getVersion())
                        .moduleId(moduleTree.getId())
                        .roleId(roleId)
                        .subjectId(subjectId)
                        .queryType(queryType)
                        .build();

        recursiveFillModuleFields(moduleTree, moduleFieldsMap);

        List<RecordDataDTO> recordDataDTOList =
                detailBizQueryService.queryDetailBiz(detailQueryContext);

        NestedModuleDataDTO.NestedModuleDataDTOBuilder nestedModuleDataDTOBuilder =
                NestedModuleDataDTO.builder();

        nestedModuleDataDTOBuilder.moduleId(moduleTree.getId());
        nestedModuleDataDTOBuilder.record(recordDataDTOList);

        resultList.add(nestedModuleDataDTOBuilder.build());
    }

    private void recursiveFillModuleFields(
            SysModuleSimpleTreeResp moduleTree,
            Map<Long, List<ModuleFieldDTO>> moduleIdToFieldMap) {

        // filterFields为空时，返回本模块所有字段
        if (moduleIdToFieldMap.isEmpty()) {
            List<ModuleFieldDTO> moduleFields =
                    dataModuleConfigService.listFieldsByModuleIds(
                            Arrays.asList(moduleTree.getId()));
            moduleIdToFieldMap.put(
                    moduleTree.getId(),
                    moduleFields.stream()
                            .filter(ele -> ele.getModuleId().equals(moduleTree.getId()))
                            .collect(Collectors.toList()));
        }

        List<ModuleFieldDTO> moduleFileds = new ArrayList<>();
        if (moduleIdToFieldMap.containsKey(moduleTree.getId())) {
            moduleFileds = moduleIdToFieldMap.get(moduleTree.getId());
        }

        List<ModuleFieldDTO> finalModuleFileds = moduleFileds;
        List<ModuleFieldDTO> relateFields =
                getIdAndRelateIdField(moduleTree.getId()).stream()
                        .filter(
                                ele ->
                                        !finalModuleFileds.stream()
                                                .anyMatch(
                                                        item ->
                                                                item.getTableName()
                                                                                .equals(
                                                                                        ele
                                                                                                .getTableName())
                                                                        && item.getColumnName()
                                                                                .equals(
                                                                                        ele
                                                                                                .getColumnName())))
                        .collect(Collectors.toList());

        moduleIdToFieldMap
                .computeIfAbsent(moduleTree.getId(), k -> new ArrayList<>())
                .addAll(relateFields);

        for (SysModuleSimpleTreeResp child : moduleTree.getChildren()) {
            recursiveFillModuleFields(child, moduleIdToFieldMap);
        }
    }

    /**
     * 根据模块配置补充主键id和关联id的权限节点配置
     *
     * @param moduleFilterInfo
     * @return
     */
    private List<ModuleField> fillIdAndJoinIdField(ModuleFilterInfo moduleFilterInfo) {

        List<ModuleField> allRightField = new ArrayList<>();
        allRightField.addAll(moduleFilterInfo.getSimpleModuleFields());

        // SELECT之前先补充主键ID和关联表ID，防止权限节点不勾选后台报错
        ModuleFilterInfo finalModuleFilterInfo = moduleFilterInfo;
        boolean primaryIdFinded =
                allRightField.stream()
                        .anyMatch(
                                ele ->
                                        ele.getTableName()
                                                        .equals(
                                                                finalModuleFilterInfo
                                                                        .getModuleInfo()
                                                                        .getPrimaryTable())
                                                && ele.getFieldCode().equals("id"));

        if (!primaryIdFinded) {
            log.debug(
                    "主表{}的主键ID未勾选权限节点，手动添加到节点集合，设置权限为3",
                    moduleFilterInfo.getModuleInfo().getPrimaryTable());
            allRightField.add(
                    ModuleField.builder()
                            .moduleId(moduleFilterInfo.getModuleInfo().getModuleId())
                            .tableName(moduleFilterInfo.getModuleInfo().getPrimaryTable())
                            .fieldCode("id")
                            .permissionCode(3)
                            .build());
        } else {
            // id字段统一做成3，避免之后查看权限时为1，前端不返回
            ModuleFilterInfo finalModuleFilterInfo1 = moduleFilterInfo;
            ModuleField matchedFields =
                    allRightField.stream()
                            .filter(
                                    ele ->
                                            ele.getTableName()
                                                            .equals(
                                                                    finalModuleFilterInfo1
                                                                            .getModuleInfo()
                                                                            .getPrimaryTable())
                                                    && ele.getFieldCode().equals("id"))
                            .findFirst()
                            .orElse(null);
            if (Objects.nonNull(matchedFields)) {
                log.debug(
                        "主表{}的主键ID勾选了权限节点，权限节点为{}",
                        moduleFilterInfo.getModuleInfo().getPrimaryTable(),
                        matchedFields.getFieldCode());
            }
        }

        // 遍历关联表，补齐SELECT数据
        for (ModuleTable moduleTable : moduleFilterInfo.getModuleTableList()) {
            ModuleFilterInfo finalModuleFilterInfo2 = moduleFilterInfo;
            boolean primaryJoinIdFinded =
                    allRightField.stream()
                            .anyMatch(
                                    ele ->
                                            ele.getTableName()
                                                            .equals(
                                                                    finalModuleFilterInfo2
                                                                            .getModuleInfo()
                                                                            .getPrimaryTable())
                                                    && ele.getFieldCode()
                                                            .equals(
                                                                    moduleTable
                                                                            .getJoinLeftField()));
            if (!primaryJoinIdFinded) {
                log.debug(
                        "主表{}的关联ID:{}未勾选权限节点，手动添加到节点集合",
                        moduleFilterInfo.getModuleInfo().getPrimaryTable(),
                        moduleTable.getJoinLeftField());
                allRightField.add(
                        ModuleField.builder()
                                .moduleId(moduleFilterInfo.getModuleInfo().getModuleId())
                                .tableName(moduleFilterInfo.getModuleInfo().getPrimaryTable())
                                .fieldCode(moduleTable.getJoinLeftField())
                                .permissionCode(3)
                                .build());
            } else {
                ModuleFilterInfo finalModuleFilterInfo3 = moduleFilterInfo;
                ModuleField matchedFields =
                        allRightField.stream()
                                .filter(
                                        ele ->
                                                ele.getTableName()
                                                                .equals(
                                                                        finalModuleFilterInfo3
                                                                                .getModuleInfo()
                                                                                .getPrimaryTable())
                                                        && ele.getFieldCode()
                                                                .equals(
                                                                        moduleTable
                                                                                .getJoinLeftField()))
                                .findFirst()
                                .orElse(null);
                if (Objects.nonNull(matchedFields)) {
                    log.debug(
                            "主表{}的关联ID:{}勾选了权限节点，权限节点为{}",
                            moduleFilterInfo.getModuleInfo().getPrimaryTable(),
                            moduleTable.getJoinLeftField(),
                            matchedFields.getFieldCode());
                }
            }

            boolean joinTableIdFinded =
                    allRightField.stream()
                            .anyMatch(
                                    ele ->
                                            ele.getTableName().equals(moduleTable.getTableName())
                                                    && ele.getFieldCode()
                                                            .equals(
                                                                    moduleTable
                                                                            .getJoinRightField()));
            if (!joinTableIdFinded) {
                log.debug(
                        "关联表{}的关联ID:{}未勾选权限节点，手动添加到节点集合",
                        moduleTable.getTableName(),
                        moduleTable.getJoinRightField());
                allRightField.add(
                        ModuleField.builder()
                                .moduleId(moduleFilterInfo.getModuleInfo().getModuleId())
                                .tableName(moduleTable.getTableName())
                                .fieldCode(moduleTable.getJoinRightField())
                                .permissionCode(1)
                                .build());
            } else {
                ModuleField matchedFields =
                        allRightField.stream()
                                .filter(
                                        ele ->
                                                ele.getTableName()
                                                                .equals(moduleTable.getTableName())
                                                        && ele.getFieldCode()
                                                                .equals(
                                                                        moduleTable
                                                                                .getJoinRightField()))
                                .findFirst()
                                .orElse(null);
                if (Objects.nonNull(matchedFields)) {
                    log.debug(
                            "关联表{}的关联ID:{}勾选了权限节点，权限节点为{}",
                            moduleTable.getTableName(),
                            moduleTable.getJoinRightField(),
                            matchedFields.getFieldCode());
                }
            }
        }

        // 主表增加业务状态字段
        allRightField.add(
                ModuleField.builder()
                        .moduleId(moduleFilterInfo.getModuleInfo().getModuleId())
                        .tableName(moduleFilterInfo.getModuleInfo().getPrimaryTable())
                        .fieldCode("business_status")
                        .permissionCode(1)
                        .build());

        return allRightField;
    }

    /**
     * 森林查找：入参多个根节点List，找到第一个匹配节点值的节点，返回其整个子树
     *
     * @param roots 森林：多个根节点组成的list
     * @param targetValue 目标节点值
     * @return 找到的子树根节点；找不到返回null
     */
    private SysModuleSimpleTreeResp findSubTreeByValue(
            List<SysModuleSimpleTreeResp> roots, Long targetValue) {
        if (roots == null || roots.isEmpty()) {
            return null;
        }
        // 遍历森林中每一棵树
        for (SysModuleSimpleTreeResp root : roots) {
            SysModuleSimpleTreeResp matchNode = dfsSearch(root, targetValue);
            if (matchNode != null) {
                return matchNode;
            }
        }
        return null;
    }

    /** 单棵树内部DFS */
    private SysModuleSimpleTreeResp dfsSearch(SysModuleSimpleTreeResp currNode, Long targetValue) {
        if (currNode == null) {
            return null;
        }
        if (targetValue.equals(currNode.getId())) {
            return currNode;
        }
        // 递归遍历子节点
        for (SysModuleSimpleTreeResp child : currNode.getChildren()) {
            SysModuleSimpleTreeResp res = dfsSearch(child, targetValue);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    /**
     * 入口方法：森林裁剪（多个根节点）
     *
     * @param root 原始树
     * @param keepIds 需要保留的目标节点ID集合
     * @return 裁剪之后的森林列表；无保留节点返回空集合
     */
    public static SysModuleSimpleTreeResp pruneForest(
            SysModuleSimpleTreeResp root, Collection<Long> keepIds) {
        if (root == null || keepIds == null || keepIds.isEmpty()) {
            return null;
        }
        SysModuleSimpleTreeResp newRoot = recursiveCloneAndPrune(root, keepIds);
        return newRoot;
    }

    /**
     * 递归：克隆节点，裁剪子节点
     *
     * @return 如果当前子树存在需要保留的节点，返回克隆后的节点；否则返回null
     */
    private static SysModuleSimpleTreeResp recursiveCloneAndPrune(
            SysModuleSimpleTreeResp originNode, Collection<Long> keepIds) {
        // 1. 先克隆当前节点（新树不修改原树）
        SysModuleSimpleTreeResp newNode = new SysModuleSimpleTreeResp();
        BeanUtils.copyProperties(originNode, newNode);
        newNode.setChildren(new ArrayList<>());

        boolean currentNodeNeedKeep = keepIds.contains(originNode.getId());

        // 2. 遍历所有子节点，递归裁剪
        List<SysModuleSimpleTreeResp> keptChildren = new ArrayList<>();
        for (SysModuleSimpleTreeResp originChild : originNode.getChildren()) {
            SysModuleSimpleTreeResp prunedChild = recursiveCloneAndPrune(originChild, keepIds);
            if (prunedChild != null) {
                keptChildren.add(prunedChild);
            }
        }
        newNode.getChildren().addAll(keptChildren);

        // 3. 判断：当前节点需要保留 或者 有需要保留的子节点 → 保留该节点
        if (currentNodeNeedKeep || !keptChildren.isEmpty()) {
            return newNode;
        } else {
            // 当前节点不是目标节点，且没有任何子节点需要保留 → 裁掉
            return null;
        }
    }

    public List<VersionListResp> getVersionList(VersionListQueryReq versionListQueryReq) {
        String businessNo = versionListQueryReq.getBusinessNo();
        Long moduleId = versionListQueryReq.getModuleId();

        Boolean hasApproval =
                sysApprovalChainConfigApi.getApprovalStatus(
                        List.of(versionListQueryReq.getModuleId()));

        if (!hasApproval) {
            return List.of();
        }

        List<VersionListResp> versionList = new ArrayList<>();

        Result<Record> recordsResult =
                getVersions(moduleId, businessNo, versionListQueryReq.getId());

        if (!CollectionUtils.isEmpty(recordsResult)) {

            // 变更时返回虚拟版本
            if ("change".equals(versionListQueryReq.getBtnType())) {

                Integer lastVersion = getMaxVersion(moduleId, businessNo);
                Integer fakeVersionNo = lastVersion == null ? 0 : lastVersion + 1;
                versionList.add(
                        VersionListResp.builder()
                                .version(fakeVersionNo)
                                .id(versionListQueryReq.getId())
                                .history(false)
                                .btnType("change-change")
                                .build());
            }

            int resultLength = recordsResult.size();

            for (int i = 0; i < recordsResult.size(); i++) {

                Map<String, Object> resultMap = recordsResult.get(i).intoMap();

                if (resultMap.containsKey("version_no")
                        && Objects.nonNull(resultMap.get("version_no"))) {

                    Integer versionNo =
                            Integer.parseInt(String.valueOf(resultMap.get("version_no")));

                    Long id = Long.parseLong(String.valueOf(resultMap.get("id")));

                    VersionListResp.VersionListRespBuilder versionBuilder =
                            VersionListResp.builder()
                                    .version(versionNo)
                                    .id(id)
                                    .history(true)
                                    .btnType("view");

                    // 除了第一版，其他版本设置preVersion为上一版本号
                    if (resultLength > 1 && i != (resultLength - 1)) {
                        versionBuilder.preVersion(versionList.get(i - 1).getVersion());
                    }

                    if (i == 0) {
                        versionBuilder.history(false);
                    }

                    versionList.add(versionBuilder.build());
                }
            }
        }

        return versionList;
    }

    private Result<Record> getVersions(Long moduleId, String businessNo, Long id) {
        SysModuleInfo sysModuleInfo =
                dataModuleConfigService.getModuleCompleteById(
                        AppContext.getProjectNo(), AppContext.getSubjectId(), moduleId);

        String primaryTable = sysModuleInfo.getPrimaryTable();

        DSLContext dslContext = jooqContextFactory.getContext();

        SelectJoinStep<Record> query =
                dslContext.select(DSL.asterisk()).from(DSL.name(primaryTable));
        // 构造SQL 查询条件
        List<Condition> conditions = new ArrayList<>();

        conditions.add(DSL.field(DSL.name(primaryTable, "business_no")).eq(businessNo));

        conditions.add(
                DSL.field(DSL.name(primaryTable, "business_status"))
                        .eq(99)
                        .or(DSL.field(DSL.name(primaryTable, "id")).eq(id)));

        query.where(conditions);

        query.orderBy(DSL.field(DSL.name("version_no")).desc());

        Result<Record> recordsResult = query.fetch();

        return recordsResult;
    }

    private Integer getMaxVersion(Long moduleId, String businessNo) {
        SysModuleInfo sysModuleInfo =
                dataModuleConfigService.getModuleCompleteById(
                        AppContext.getProjectNo(), AppContext.getSubjectId(), moduleId);

        String primaryTable = sysModuleInfo.getPrimaryTable();

        DSLContext dslContext = jooqContextFactory.getContext();

        List<Field<?>> fields = new ArrayList<>();
        fields.add(DSL.max(DSL.field(DSL.name(primaryTable, "version_no"))).as("max_version"));

        SelectJoinStep<Record> query = dslContext.select(fields).from(DSL.name(primaryTable));
        // 构造SQL 查询条件
        List<Condition> conditions = new ArrayList<>();

        conditions.add(DSL.field(DSL.name(primaryTable, "business_no")).eq(businessNo));

        query.where(conditions);

        query.groupBy(DSL.field(DSL.name("business_no")));

        Record maxVersionResult = query.fetchOne();

        if (Objects.isNull(maxVersionResult)) {
            return null;
        } else {
            Map<String, Object> resultMap = maxVersionResult.intoMap();
            if (resultMap.containsKey("max_version")
                    && Objects.nonNull(resultMap.get("max_version"))) {
                Integer maxVersion = Integer.parseInt(String.valueOf(resultMap.get("max_version")));
                return maxVersion;
            } else {
                return null;
            }
        }
    }

    private List<VersionListResp> getHistoryVersionList(Long moduleId, Long id) {

        DSLContext dslContext = jooqContextFactory.getContext();

        SelectJoinStep<Record> query =
                dslContext.select(DSL.asterisk()).from(DSL.name("module_data_snapshot_content"));
        // 构造SQL 查询条件
        List<Condition> conditions = new ArrayList<>();

        conditions.add(
                DSL.field(DSL.name("module_data_snapshot_content", "root_module_id")).eq(moduleId));
        conditions.add(
                DSL.field(DSL.name("module_data_snapshot_content", "root_table_data_id")).eq(id));

        query.orderBy(
                DSL.field(DSL.name("module_data_snapshot_content", "root_table_data_version"))
                        .desc());

        query.where(conditions);

        Result<Record> recordsResult = query.fetch();

        List<VersionListResp> historyVersionList = new ArrayList<>();

        for (Record r : recordsResult) {
            Map<String, Object> resultMap = r.intoMap();
            if (Objects.nonNull(resultMap.get("root_table_data_version"))) {
                Integer versionNo =
                        Integer.parseInt(String.valueOf(resultMap.get("root_table_data_version")));
                historyVersionList.add(
                        VersionListResp.builder()
                                .version(versionNo)
                                .history(true)
                                .btnType("view")
                                .build());
            }
        }

        historyVersionList.sort(Comparator.comparing(VersionListResp::getVersion).reversed());

        if (!CollectionUtils.isEmpty(historyVersionList)) {
            // 设置上一版本版本号
            int i = 0;
            int last = (historyVersionList.size() - 1);
            while (i < (last)) {
                historyVersionList.get(i).setPreVersion(historyVersionList.get(i + 1).getVersion());
                i++;
            }
        }

        return historyVersionList;
    }
}
