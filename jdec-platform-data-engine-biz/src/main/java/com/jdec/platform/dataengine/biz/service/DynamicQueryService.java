package com.jdec.platform.dataengine.biz.service;

import cn.hutool.core.bean.BeanUtil;
import com.jdec.platform.config.api.SysMenuApi;
import com.jdec.platform.config.api.SysRolePermissionApi;
import com.jdec.platform.config.api.dto.common.ModuleTableDTO;
import com.jdec.platform.config.api.dto.common.ModuleTableHeaderDTO;
import com.jdec.platform.config.api.dto.response.SysMenuTreeResp;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.api.dto.request.DynamicQueryReq;
import com.jdec.platform.dataengine.api.dto.response.DynamicQueryResp;
import com.jdec.platform.dataengine.biz.dsl.JooqConditionBuilder;
import com.jdec.platform.dataengine.biz.dsl.JooqContextFactory;
import com.jdec.platform.dataengine.biz.dsl.JooqSqlBuilder;
import com.jdec.platform.dataengine.biz.dto.ModuleField;
import com.jdec.platform.dataengine.biz.dto.ModuleFilterInfo;
import com.jdec.platform.dataengine.biz.dto.ModuleTable;
import com.jdec.platform.dataengine.biz.dto.context.DetailQueryContext;
import com.jdec.platform.dataengine.biz.dto.context.ListQueryContext;
import com.jdec.platform.dataengine.biz.enums.QueryType;
import com.jdec.platform.dataengine.biz.third.api.DetailBizQueryService;
import com.jdec.platform.dataengine.biz.third.api.ListBizQueryService;
import com.jdec.platform.dataengine.biz.third.api.module.ModuleInfoWithPermissionService;
import com.jdec.platform.shared.context.AppContext;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;
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

    private final SysMenuApi sysMenuApi;
    private final SysRolePermissionApi sysRolePermissionApi;

    private final ModuleInfoWithPermissionService moduleInfoWithPermissionService;

    /** 一体化列表分页查询 */
    public DynamicQueryResp query(DynamicQueryReq req) {

        ListQueryContext.ListQueryContextBuilder listContextBuilder =
                ListQueryContext.builder()
                        .subjectId(AppContext.getSubjectId())
                        .roleId(AppContext.getRoleId());

        // TODO 通过menuId和moduleId查审批链配置获取
        boolean hasApproval = false;

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
        SysModuleCompleteResp completeResp = metadataCacheService.getModuleComplete(moduleId);
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
        Map<String, Map<Long, List<Map<String, Object>>>> multiTableDataMap =
                fetchOneToManyTables(dsl, completeResp, primaryIds);

        // 5. 组装行记录列表 (Table-First 纯粹 Map: record[tableName] = record/records)
        List<Map<String, Object>> structuredRecords = new ArrayList<>();
        for (Map<String, Object> row : primaryRows) {
            Map<String, Object> rowTables = new LinkedHashMap<>();
            Long rowId = row.get("id") instanceof Number num ? num.longValue() : null;

            // 主表数据
            rowTables.put(primaryTable, row);

            // 关联表数据 (1:1 / N:1 为单对象, 1:N 为对象列表)
            if (completeResp.getModuleTables() != null) {
                for (ModuleTableDTO tableDto : completeResp.getModuleTables()) {
                    String tName = tableDto.getTableName();
                    String rType = tableDto.getRelationType();
                    if ("1:N".equalsIgnoreCase(rType) || "ONE_TO_MANY".equalsIgnoreCase(rType)) {
                        List<Map<String, Object>> subRows =
                                multiTableDataMap
                                        .getOrDefault(tName, Collections.emptyMap())
                                        .getOrDefault(rowId, Collections.emptyList());
                        rowTables.put(tName, subRows);
                    } else {
                        // 1:1 或 N:1
                        rowTables.put(tName, row);
                    }
                }
            }
            structuredRecords.add(rowTables);
        }

        // 6. 组装 Meta 与 Pagination
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("moduleId", moduleId);
        meta.put("moduleCode", completeResp.getModule().getModuleCode());
        meta.put("moduleName", completeResp.getModule().getModuleName());
        meta.put("primaryTable", primaryTable);
        meta.put("detailModuleId", completeResp.getModule().getDetailModuleId());
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
    public List<Object> getDetail(List<Long> menuIds, Long id, String btnType, boolean recursive) {
        List<Object> resultList = new ArrayList<>();

        List<SysMenuTreeResp> menuRespList = sysMenuApi.tree(1);
        if (CollectionUtils.isEmpty(menuRespList)) {
            log.debug("菜单树查询结果未空，返回空列表。");
            return resultList;
        }

        if (!recursive) {
            // ========= 模式1：多个模块独立查询，互不关联 =========
            for (Long menuId : menuIds) {
                SysMenuTreeResp node = findSubTreeByValue(menuRespList, menuId);
                if (node == null) {
                    continue;
                }
                // 组装当前模块查询参数
                DynamicQueryReq singleQueryDTO =
                        DynamicQueryReq.builder()
                                .moduleId(node.getModuleId())
                                .menuId(node.getId())
                                .mainDataId(id)
                                .btnType(btnType)
                                .build();
                // 独立查询当前模块，不递归子模块（独立模式默认不递归）
                Object moduleData = queryModule(singleQueryDTO, node, recursive);
                resultList.add(moduleData);
            }
        } else {
            // ========= 模式2：树形关联查询，多个模块属于同一棵树 =========
            // 取根模块（moduleCodeList第一个为根节点，其余是树内子模块）
            Long topMenuId = menuIds.get(0);
            SysMenuTreeResp rootNode = findSubTreeByValue(menuRespList, topMenuId);
            if (rootNode == null) {
                return resultList;
            }
            DynamicQueryReq singleQueryDTO =
                    DynamicQueryReq.builder()
                            .moduleId(rootNode.getModuleId())
                            .menuId(rootNode.getId())
                            .mainDataId(id)
                            .btnType(btnType)
                            .build();

            Object moduleData = queryModule(singleQueryDTO, rootNode, recursive);
            resultList.add(moduleData);
        }

        return resultList;
    }

    private NestedModuleDataDTO queryModule(
            DynamicQueryReq queryReq, SysMenuTreeResp rootNode, Boolean recursive) {
        NestedModuleDataDTO.NestedModuleDataDTOBuilder builder = NestedModuleDataDTO.builder();

        // TODO 通过menuId和moduleId查审批链配置获取
        boolean hasApproval = true;

        QueryType queryType;

        if (hasApproval) {
            queryType = QueryType.DETAIL_APPROVAL_CURRENT;
        } else {
            queryType = QueryType.DETAIL;
        }

        queryReq.setType(hasApproval ? 3 : 4);

        List<RecordDataDTO> currentModuleData = querySingleModule(rootNode, queryReq, queryType);

        if (!recursive || rootNode.getChildren() == null || rootNode.getChildren().isEmpty()) {
            return builder.moduleId(rootNode.getModuleId())
                    .menuId(rootNode.getId())
                    .record(currentModuleData)
                    .build();
        }

        for (SysMenuTreeResp childNode : rootNode.getChildren()) {
            // 组装当前模块查询参数
            DynamicQueryReq singleQueryDTO =
                    DynamicQueryReq.builder()
                            .moduleId(childNode.getModuleId())
                            .mainDataId(queryReq.getMainDataId())
                            .btnType(queryReq.getBtnType())
                            .build();
            NestedModuleDataDTO childResultList = queryModule(singleQueryDTO, childNode, true);
            for (RecordDataDTO vo : currentModuleData) {
                vo.getSubModule().add(childResultList);
            }
        }

        return builder.moduleId(rootNode.getModuleId())
                .menuId(rootNode.getId())
                .record(currentModuleData)
                .build();
    }

    private List<RecordDataDTO> querySingleModule(
            SysMenuTreeResp node, DynamicQueryReq queryDTO, QueryType queryType) {
        List<RecordDataDTO> resultList = new ArrayList<>();
        if (node.getMenuType() == 4) {
            // TODO 查找列表模块数据
            DynamicQueryResp listData = query(queryDTO);
            Map<String, Object> targetMap = new HashMap<>();
            BeanUtil.beanToMap(listData, targetMap, false, true);
            RecordDataDTO.builder().combinedRow(targetMap);
            resultList.add(null);
        } else if (node.getMenuType() == 2 || node.getMenuType() == 3) {
            // 查找详情模块数据
            resultList.add(getDetailModuleData(node.getModuleId(), queryType, queryDTO));
        }
        return resultList;
    }

    private RecordDataDTO getDetailModuleData(
            Long moduleId, QueryType queryType, DynamicQueryReq queryReq) {

        Long subjectId = AppContext.getSubjectId();
        Long roleId = AppContext.getRoleId();

        DetailQueryContext detailQueryContext =
                DetailQueryContext.builder()
                        .btnType(queryReq.getBtnType())
                        .id(queryReq.getMainDataId())
                        .menuId(queryReq.getMenuId())
                        .moduleId(moduleId)
                        .roleId(roleId)
                        .subjectId(subjectId)
                        .queryType(queryType)
                        .build();

        Map<String, Object> moduleData = detailBizQueryService.queryDetailBiz(detailQueryContext);

        return RecordDataDTO.builder().combinedRow(moduleData).subModule(new ArrayList<>()).build();
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
                            .filedType("SIMPLE")
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
                                .filedType("SIMPLE")
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
                                .filedType("SIMPLE")
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
                        .filedType("SIMPLE")
                        .tableName(moduleFilterInfo.getModuleInfo().getPrimaryTable())
                        .fieldCode("business_status")
                        .permissionCode(1)
                        .build());

        return allRightField;
    }

    /** 批量抓取当前页主表 ID 集合关联的 1:N 从表记录 */
    private Map<String, Map<Long, List<Map<String, Object>>>> fetchOneToManyTables(
            DSLContext dsl, SysModuleCompleteResp completeResp, List<Long> primaryIds) {

        Map<String, Map<Long, List<Map<String, Object>>>> result = new HashMap<>();
        if (primaryIds == null || primaryIds.isEmpty() || completeResp.getModuleTables() == null) {
            return result;
        }

        for (ModuleTableDTO tableDto : completeResp.getModuleTables()) {
            String rType = tableDto.getRelationType();
            if ("1:N".equalsIgnoreCase(rType) || "ONE_TO_MANY".equalsIgnoreCase(rType)) {
                String tableName = tableDto.getTableName();
                String rightField = tableDto.getJoinRightField(); // 外键字段, 如 student_id

                Result<Record> subRecords =
                        dsl.select(DSL.asterisk())
                                .from(DSL.table(DSL.name(tableName)))
                                .where(DSL.field(DSL.name(tableName, rightField)).in(primaryIds))
                                .fetch();

                Map<Long, List<Map<String, Object>>> grouped = new HashMap<>();
                for (Record r : subRecords) {
                    Map<String, Object> rMap = r.intoMap();
                    Object fkVal = rMap.get(rightField);
                    if (fkVal instanceof Number fkNum) {
                        grouped.computeIfAbsent(fkNum.longValue(), k -> new ArrayList<>())
                                .add(rMap);
                    }
                }
                result.put(tableName, grouped);
            }
        }
        return result;
    }

    /**
     * 森林查找：入参多个根节点List，找到第一个匹配节点值的节点，返回其整个子树
     *
     * @param roots 森林：多个根节点组成的list
     * @param targetValue 目标节点值
     * @return 找到的子树根节点；找不到返回null
     */
    private SysMenuTreeResp findSubTreeByValue(List<SysMenuTreeResp> roots, Long targetValue) {
        if (roots == null || roots.isEmpty()) {
            return null;
        }
        // 遍历森林中每一棵树
        for (SysMenuTreeResp root : roots) {
            SysMenuTreeResp matchNode = dfsSearch(root, targetValue);
            if (matchNode != null) {
                return matchNode;
            }
        }
        return null;
    }

    /** 单棵树内部DFS */
    private SysMenuTreeResp dfsSearch(SysMenuTreeResp currNode, Long targetValue) {
        if (currNode == null) {
            return null;
        }
        if (targetValue.equals(currNode.getId())) {
            return currNode;
        }
        // 递归遍历子节点
        for (SysMenuTreeResp child : currNode.getChildren()) {
            SysMenuTreeResp res = dfsSearch(child, targetValue);
            if (res != null) {
                return res;
            }
        }
        return null;
    }
}
