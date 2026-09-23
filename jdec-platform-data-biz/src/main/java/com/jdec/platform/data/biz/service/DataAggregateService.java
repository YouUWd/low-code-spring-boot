package com.jdec.platform.data.biz.service;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModulePermissionDTO;
import com.jdec.platform.config.api.dto.common.ModulePermissionDTO.FieldPermissionItem;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.UserMenuHeaderApi;
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.data.api.dto.request.MenuQueryReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.response.ApprovalRowItem;
import com.jdec.platform.data.api.dto.response.DataDetail;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.DataRecordItem;
import com.jdec.platform.data.api.dto.response.FieldMetaDTO;
import com.jdec.platform.data.api.dto.response.MenuDataResp;
import com.jdec.platform.data.api.dto.response.MenuDetailResp;
import com.jdec.platform.data.api.dto.response.ModuleDataNode;
import com.jdec.platform.data.api.dto.response.ModuleMetaDTO;
import com.jdec.platform.data.api.dto.response.ModuleSimplePermissionDTO;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 菜单动态数据与元数据聚合服务实现
 *
 * <p>作为面向 UI 的数据隔离与聚合适配层，在底层 DataApi 之上结合 UserMenuHeaderApi 解析菜单与表头配置，输出自相似复合数据模型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataAggregateService {

    private final DataApi dataApi;
    private final ConfigApi configApi;
    private final UserMenuHeaderApi userMenuHeaderApi;
    private final IApprovalEngine approvalEngine;

    private static final int MAX_TREE_DEPTH = 8;

    /** 根模块关键字段 ID 载体 */
    private record RootModuleFieldIds(
            Long idFieldId, Long createdByFieldId, Long businessStatusFieldId) {}

    /** 根模块关键属性元数据载体 (包含根主键 id, created_by, business_status) */
    private record RootModuleMeta(Long id, Long createdBy, Integer businessStatus) {}

    /** 待办/已办过滤上下文 */
    private record ApprovalFilterResult(List<FilterItem> filters, boolean shortCircuitEmpty) {}

    /** 列表行与按钮装配结果 */
    private record AssembledRecordResult(
            List<DataRecordItem> records, List<ApprovalRowItem> approvalRows) {}

    // =========================================================================
    // 1. 公共入口业务方法 (Public APIs)
    // =========================================================================

    /**
     * 基于菜单 ID 的动态数据列表查询 (自动通过 UserMenuHeaderApi 解析勾选的 fieldId 列表与 rootModuleId)
     *
     * @param menuId 菜单 ID
     * @param req 面向 UI 的查询过滤、排序与分页请求体 (可选)
     * @return 包含顶层多级列头、权限汇总、审批链数据与自相似数据树的复合响应
     */
    public MenuDataResp queryByMenu(Long menuId, MenuQueryReq req) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }

        // 1. 解析多级表头与根模块 ID
        MenuHeaderDisplayResp headerDisplay = getMenuHeaderDisplay(menuId);
        if (headerDisplay == null || headerDisplay.getHeader() == null) {
            throw new BusinessException("菜单 [" + menuId + "] 未配置有效的表头展示信息");
        }
        MenuHeaderColumnResp rootHeader = headerDisplay.getHeader();
        Long rootModuleId = rootHeader.getModuleId();
        if (rootModuleId == null) {
            throw new BusinessException("菜单 [" + menuId + "] 绑定的根模块 ID 为空");
        }

        // 2. 提取字段列表与模块菜单映射
        List<Long> headerFields = extractFieldIdsFromHeader(headerDisplay);
        Map<Long, Long> headerModuleMenuMap = extractModuleMenuMapFromHeader(headerDisplay);
        Long rootMenuId = headerModuleMenuMap.getOrDefault(rootModuleId, menuId);

        // 3. 查询根模块汇总权限
        ModuleSimplePermissionDTO simplePerm = queryRootModulePermission(rootModuleId);

        // 4. 查询审批链按钮映射 (如果是全部(3)，通过 approvalType=3 一次性获取合并待办和已办的审批链数据)
        Integer approvalType =
                (req != null && req.getApprovalType() != null) ? req.getApprovalType() : 3;
        Map<Long, List<Map<String, Object>>> btnGroupMap;
        if (approvalType == 3) {
            btnGroupMap = queryAllApprovalButtons(rootModuleId).allBtns();
        } else {
            btnGroupMap = queryApprovalButtons(rootModuleId, approvalType);
        }

        // 5. 提取根模块关键字段并补充查询字段 (id, created_by, business_status)
        RootModuleFieldIds rootFieldIds = extractRootModuleFieldIds(rootModuleId);
        List<Long> queryFields = enrichQueryFields(headerFields, rootFieldIds);

        // 6. 构建待办/已办/常规过滤条件
        ApprovalFilterResult filterResult =
                buildApprovalFilters(req, approvalType, rootModuleId, rootFieldIds, btnGroupMap);
        if (filterResult.shortCircuitEmpty()) {
            return buildEmptyMenuDataResp(headerDisplay, simplePerm, rootModuleId, rootMenuId);
        }

        // 7. 执行底层 DataApi 物理分页查询
        QueryReq queryReq =
                QueryReq.builder()
                        .moduleId(rootModuleId)
                        .pageNo(req != null ? req.getPageNo() : null)
                        .pageSize(req != null ? req.getPageSize() : null)
                        .fields(queryFields)
                        .filters(filterResult.filters())
                        .sorts(req != null ? req.getSorts() : null)
                        .build();

        DataPage<Map<String, Object>> page = dataApi.query(queryReq);
        List<Map<String, Object>> rawRecords =
                (page != null) ? page.getRecords() : Collections.emptyList();

        // 8. 转换装配记录树与审批按钮
        AssembledRecordResult assembledResult =
                assembleRecordsAndButtons(
                        rawRecords, rootModuleId, approvalType, btnGroupMap, headerModuleMenuMap);

        // 9. 构建顶层响应
        ModuleDataNode dataTree =
                ModuleDataNode.builder()
                        .moduleId(rootModuleId)
                        .menuId(rootMenuId)
                        .record(assembledResult.records())
                        .build();

        return MenuDataResp.builder()
                .header(headerDisplay.getHeader())
                .permission(simplePerm)
                .approval(
                        assembledResult.approvalRows().isEmpty()
                                ? null
                                : assembledResult.approvalRows())
                .data(dataTree)
                .build();
    }

    /**
     * 基于菜单 ID 一次性统计待办、已办、全部的记录数量
     *
     * @param menuId 菜单 ID
     * @param req 面向 UI 的通用过滤条件请求体 (可选)
     * @return 映射字典：1 -> 待办数量(todo), 2 -> 已办数量(done), 3 -> 全部数量(total)
     */
    public Map<Integer, Long> getApprovalCountByMenu(Long menuId, MenuQueryReq req) {
        if (menuId == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }

        // 1. 解析多级表头与根模块 ID
        MenuHeaderDisplayResp headerDisplay = getMenuHeaderDisplay(menuId);
        if (headerDisplay == null || headerDisplay.getHeader() == null) {
            throw new BusinessException("菜单 [" + menuId + "] 未配置有效的表头展示信息");
        }
        MenuHeaderColumnResp rootHeader = headerDisplay.getHeader();
        Long rootModuleId = rootHeader.getModuleId();
        if (rootModuleId == null) {
            throw new BusinessException("菜单 [" + menuId + "] 绑定的根模块 ID 为空");
        }

        RootModuleFieldIds rootFieldIds = extractRootModuleFieldIds(rootModuleId);

        // 2. 一次性获取审批链全部按钮数据 (approvalType = 3 会内部包含待办与已办)
        ApprovalButtonsGroup approvalGroup = queryAllApprovalButtons(rootModuleId);
        Map<Long, List<Map<String, Object>>> todoBtnMap = approvalGroup.todoBtns();
        Map<Long, List<Map<String, Object>>> doneBtnMap = approvalGroup.doneBtns();

        // 3. 统计【待办数量】 (approvalType = 1)
        ApprovalFilterResult todoFilterResult =
                buildApprovalFilters(req, 1, rootModuleId, rootFieldIds, todoBtnMap);
        Long todoCount = 0L;
        if (!todoFilterResult.shortCircuitEmpty()) {
            todoCount =
                    dataApi.count(
                            QueryReq.builder()
                                    .moduleId(rootModuleId)
                                    .filters(todoFilterResult.filters())
                                    .build());
        }

        // 4. 统计【已办数量】 (approvalType = 2: 直接使用审批链接口返回的数据量，无需额外查询 SQL)
        Long doneCount = (long) doneBtnMap.size();

        // 5. 统计【全部数量】 (结合待办分支与审批链返回的已办 id 集合)
        Set<Long> doneRecordIds = doneBtnMap.keySet();
        Set<Long> todoRecordIds = todoBtnMap.keySet();
        Set<Long> allApprovalIds = new LinkedHashSet<>(doneRecordIds);
        allApprovalIds.addAll(todoRecordIds);

        List<FilterItem> totalFilters =
                (req != null && req.getFilters() != null)
                        ? new ArrayList<>(req.getFilters())
                        : new ArrayList<>();

        Long currentUserId = UserContext.getUserId();
        boolean hasApprovalIds = !allApprovalIds.isEmpty() && rootFieldIds.idFieldId() != null;
        boolean canQueryDraft =
                currentUserId != null
                        && rootFieldIds.createdByFieldId() != null
                        && rootFieldIds.businessStatusFieldId() != null;

        Long totalCount = 0L;
        if (!hasApprovalIds && !canQueryDraft) {
            totalCount = 0L;
        } else {
            FilterItem approvalBranch = null;
            if (hasApprovalIds) {
                approvalBranch =
                        FilterItem.builder()
                                .fieldId(rootFieldIds.idFieldId())
                                .operator("IN")
                                .value(new ArrayList<>(allApprovalIds))
                                .build();
            }

            FilterItem draftBranch = null;
            if (canQueryDraft) {
                List<FilterItem> draftItems = new ArrayList<>();
                draftItems.add(
                        FilterItem.builder()
                                .fieldId(rootFieldIds.createdByFieldId())
                                .operator("EQ")
                                .value(currentUserId)
                                .build());
                draftItems.add(
                        FilterItem.builder()
                                .fieldId(rootFieldIds.businessStatusFieldId())
                                .operator("EQ")
                                .value(1)
                                .build());
                draftBranch = FilterItem.builder().logic("AND").children(draftItems).build();
            }

            if (approvalBranch != null && draftBranch != null) {
                totalFilters.add(
                        FilterItem.builder()
                                .logic("OR")
                                .children(Arrays.asList(approvalBranch, draftBranch))
                                .build());
            } else if (approvalBranch != null) {
                totalFilters.add(approvalBranch);
            } else {
                totalFilters.add(draftBranch);
            }

            totalCount =
                    dataApi.count(
                            QueryReq.builder()
                                    .moduleId(rootModuleId)
                                    .filters(totalFilters)
                                    .build());
        }

        Map<Integer, Long> countMap = new LinkedHashMap<>();
        countMap.put(1, todoCount != null ? todoCount : 0L);
        countMap.put(2, doneCount);
        countMap.put(3, totalCount != null ? totalCount : 0L);
        return countMap;
    }

    /**
     * 基于菜单 ID 的单据详情及元数据聚合查询 (仅需 menuId 和 id)
     *
     * @param menuId 菜单 ID
     * @param id 单据 ID
     * @return 包含 List<ModuleMetaDTO> meta 与 ModuleDataNode data 的复合响应
     */
    public MenuDetailResp getDetailByMenu(Long menuId, Long id) {
        if (menuId == null || id == null) {
            throw new IllegalArgumentException("菜单 ID 和单据 ID 不能为空");
        }

        MenuHeaderDisplayResp headerDisplay = getMenuHeaderDisplay(menuId);
        if (headerDisplay == null || headerDisplay.getHeader() == null) {
            throw new BusinessException("菜单 [" + menuId + "] 未配置有效的表头展示信息");
        }

        MenuHeaderColumnResp rootHeader = headerDisplay.getHeader();
        Long rootModuleId = rootHeader.getModuleId();
        if (rootModuleId == null) {
            throw new BusinessException("菜单 [" + menuId + "] 绑定的根模块 ID 为空");
        }

        List<Long> headerFields = extractFieldIdsFromHeader(headerDisplay);
        Map<Long, Long> headerModuleMenuMap = extractModuleMenuMapFromHeader(headerDisplay);

        return getDetailTree(rootModuleId, id, headerFields, menuId, headerModuleMenuMap);
    }

    /**
     * 单据详情查询 (返回携带当前模块及其所有子模块的元数据列表 meta 与数据 data)
     *
     * @param moduleId 模块 ID
     * @param id 单据 ID
     * @param fields 投影字段
     * @param menuId 菜单 ID
     * @param headerModuleMenuMap 模块-菜单映射
     * @return 包含 List<ModuleInfo> meta 与 ModuleDataNode data 的复合响应
     */
    public MenuDetailResp getDetailTree(
            Long moduleId,
            Long id,
            List<Long> fields,
            Long menuId,
            Map<Long, Long> headerModuleMenuMap) {
        if (moduleId == null || id == null) {
            throw new IllegalArgumentException("模块 ID 和单据 ID 不能为空");
        }

        Map<Long, Long> moduleMenuMap =
                headerModuleMenuMap != null ? headerModuleMenuMap : Collections.emptyMap();
        Long effectiveMenuId = moduleMenuMap.getOrDefault(moduleId, menuId);

        // 1. 调用底层 DataApi 获取详情
        DataDetail<Map<String, Object>> detail = dataApi.getDetail(moduleId, id, fields);
        Map<String, Object> rawData = (detail != null) ? detail.getData() : Collections.emptyMap();

        List<DataRecordItem> recordItems = new ArrayList<>();
        if (rawData != null && !rawData.isEmpty()) {
            DataRecordItem item = transformRecord(moduleId, rawData, moduleMenuMap);
            recordItems.add(item);
        }

        ModuleDataNode treeData =
                ModuleDataNode.builder()
                        .moduleId(moduleId)
                        .menuId(effectiveMenuId)
                        .record(recordItems)
                        .build();

        // 2. 收集结构中涉及的所有模块并组装元数据
        Map<Long, Long> collectedModuleMenuMap = new LinkedHashMap<>();
        collectModuleMenuMap(treeData, collectedModuleMenuMap);

        List<ModuleMetaDTO> metaList = assembleDetailMetaList(moduleId, collectedModuleMenuMap);
        return MenuDetailResp.of(metaList, treeData);
    }

    // =========================================================================
    // 2. 表头与权限解析辅助方法
    // =========================================================================

    /** 获取指定菜单下的表头展示结构 (通过 UserMenuHeaderApi) */
    public MenuHeaderDisplayResp getMenuHeaderDisplay(Long menuId) {
        Long userId = UserContext.getUserId();
        Long roleId = UserContext.getRoleId();

        if (userId == null || roleId == null || menuId == null) {
            return null;
        }

        try {
            return userMenuHeaderApi.getUserMenuHeaderDisplay(userId, menuId, roleId);
        } catch (Exception ex) {
            log.warn(
                    "获取用户菜单表头展示结构失败: userId={}, menuId={}, err={}",
                    userId,
                    menuId,
                    ex.getMessage());
            return null;
        }
    }

    /** 递归从多级表头中提取所有有效已勾选的字段 ID (去重保持顺序) */
    public List<Long> extractFieldIdsFromHeader(MenuHeaderDisplayResp headerDisplay) {
        if (headerDisplay == null || headerDisplay.getHeader() == null) {
            return Collections.emptyList();
        }
        Set<Long> fieldIdSet = new LinkedHashSet<>();
        collectFieldIdsRecursively(headerDisplay.getHeader(), fieldIdSet);
        return new ArrayList<>(fieldIdSet);
    }

    private void collectFieldIdsRecursively(MenuHeaderColumnResp node, Set<Long> fieldIdSet) {
        if (node == null) {
            return;
        }
        if (node.getFieldId() != null) {
            fieldIdSet.add(node.getFieldId());
        }
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            for (MenuHeaderColumnResp child : node.getChildren()) {
                collectFieldIdsRecursively(child, fieldIdSet);
            }
        }
    }

    /** 递归从多级表头中提取模块 ID 与菜单 ID 的映射表 (Map<moduleId, menuId>) */
    public Map<Long, Long> extractModuleMenuMapFromHeader(MenuHeaderDisplayResp headerDisplay) {
        if (headerDisplay == null || headerDisplay.getHeader() == null) {
            return Collections.emptyMap();
        }
        Map<Long, Long> map = new LinkedHashMap<>();
        collectModuleMenuMapRecursively(headerDisplay.getHeader(), map);
        return map;
    }

    private void collectModuleMenuMapRecursively(MenuHeaderColumnResp node, Map<Long, Long> map) {
        if (node == null) {
            return;
        }
        if (node.getModuleId() != null && node.getSourceId() != null) {
            map.putIfAbsent(node.getModuleId(), node.getSourceId());
        }
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            for (MenuHeaderColumnResp child : node.getChildren()) {
                collectModuleMenuMapRecursively(child, map);
            }
        }
    }

    /** 查询根模块汇总权限 */
    private ModuleSimplePermissionDTO queryRootModulePermission(Long rootModuleId) {
        Long roleId = UserContext.getRoleId();
        if (roleId == null) {
            return null;
        }
        try {
            Map<Long, ModulePermissionDTO> permMap =
                    configApi.getModulePermissions(roleId, Collections.singletonList(rootModuleId));
            if (permMap != null && permMap.containsKey(rootModuleId)) {
                ModulePermissionDTO rootPerm = permMap.get(rootModuleId);
                return ModuleSimplePermissionDTO.builder()
                        .apply(Boolean.TRUE.equals(rootPerm.getApply()) ? 1 : 0)
                        .view(Boolean.TRUE.equals(rootPerm.getView()) ? 1 : 0)
                        .edit(Boolean.TRUE.equals(rootPerm.getEdit()) ? 1 : 0)
                        .build();
            }
        } catch (Exception ex) {
            log.warn(
                    "查询根模块权限失败: moduleId={}, roleId={}, err={}",
                    rootModuleId,
                    roleId,
                    ex.getMessage());
        }
        return null;
    }

    // =========================================================================
    // 3. 审批流与过滤构建辅助方法
    // =========================================================================

    /** 一次性查询审批链全部按钮数据 (approvalType=3 内部自动拆分待办 handle 与查阅/已办 view) */
    private ApprovalButtonsGroup queryAllApprovalButtons(Long rootModuleId) {
        Map<Long, List<Map<String, Object>>> todoBtns = new LinkedHashMap<>();
        Map<Long, List<Map<String, Object>>> doneBtns = new LinkedHashMap<>();
        Map<Long, List<Map<String, Object>>> allBtns = new LinkedHashMap<>();

        try {
            List<Map<String, Object>> approvalRawList =
                    approvalEngine.getListButton(rootModuleId, 3);
            if (approvalRawList != null && !approvalRawList.isEmpty()) {
                for (Map<String, Object> rawBtn : approvalRawList) {
                    if (rawBtn == null) {
                        continue;
                    }
                    Long recordId = parseRecordIdFromRaw(rawBtn.get("recordId"));
                    if (recordId == null) {
                        continue;
                    }

                    Map<String, Object> btnInfo = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> e : rawBtn.entrySet()) {
                        if (!"recordId".equals(e.getKey())) {
                            btnInfo.put(e.getKey(), e.getValue());
                        }
                    }

                    String buttonKey = Objects.toString(btnInfo.get("buttonKey"), "");
                    if ("handle".equalsIgnoreCase(buttonKey)) {
                        todoBtns.computeIfAbsent(recordId, k -> new ArrayList<>()).add(btnInfo);
                    } else {
                        doneBtns.computeIfAbsent(recordId, k -> new ArrayList<>()).add(btnInfo);
                    }
                    allBtns.computeIfAbsent(recordId, k -> new ArrayList<>()).add(btnInfo);
                }
            }
        } catch (Exception ex) {
            log.warn(
                    "获取审批链全量按钮数据失败或当前模块未配置审批流: moduleId={}, err={}", rootModuleId, ex.getMessage());
        }
        return new ApprovalButtonsGroup(todoBtns, doneBtns, allBtns);
    }

    /** 查询审批链按钮映射 */
    private Map<Long, List<Map<String, Object>>> queryApprovalButtons(
            Long rootModuleId, Integer approvalType) {
        Map<Long, List<Map<String, Object>>> btnGroupMap = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> approvalRawList =
                    approvalEngine.getListButton(rootModuleId, approvalType);
            if (approvalRawList != null && !approvalRawList.isEmpty()) {
                for (Map<String, Object> rawBtn : approvalRawList) {
                    if (rawBtn == null) {
                        continue;
                    }
                    Long recordId = parseRecordIdFromRaw(rawBtn.get("recordId"));
                    if (recordId == null) {
                        continue;
                    }

                    Map<String, Object> btnInfo = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> e : rawBtn.entrySet()) {
                        if (!"recordId".equals(e.getKey())) {
                            btnInfo.put(e.getKey(), e.getValue());
                        }
                    }
                    btnGroupMap.computeIfAbsent(recordId, k -> new ArrayList<>()).add(btnInfo);
                }
            }
        } catch (Exception ex) {
            log.warn("获取审批链按钮数据失败或当前模块未配置审批流: moduleId={}, err={}", rootModuleId, ex.getMessage());
        }
        return btnGroupMap;
    }

    /** 补充关键字段至查询字段列表 */
    private List<Long> enrichQueryFields(List<Long> headerFields, RootModuleFieldIds rootFieldIds) {
        if (headerFields == null || headerFields.isEmpty()) {
            return headerFields;
        }
        Set<Long> queryFieldIdSet = new LinkedHashSet<>(headerFields);
        if (rootFieldIds.idFieldId() != null) {
            queryFieldIdSet.add(rootFieldIds.idFieldId());
        }
        if (rootFieldIds.createdByFieldId() != null) {
            queryFieldIdSet.add(rootFieldIds.createdByFieldId());
        }
        if (rootFieldIds.businessStatusFieldId() != null) {
            queryFieldIdSet.add(rootFieldIds.businessStatusFieldId());
        }
        return new ArrayList<>(queryFieldIdSet);
    }

    /** 构建待办/已办复合过滤条件 */
    private ApprovalFilterResult buildApprovalFilters(
            MenuQueryReq req,
            Integer approvalType,
            Long rootModuleId,
            RootModuleFieldIds rootFieldIds,
            Map<Long, List<Map<String, Object>>> btnGroupMap) {

        List<FilterItem> filters =
                (req != null && req.getFilters() != null)
                        ? new ArrayList<>(req.getFilters())
                        : new ArrayList<>();

        Long currentUserId = UserContext.getUserId();

        if (approvalType == 1) {
            // 【待办】: (id IN recordIds) OR (created_by == currentUserId && business_status == 1)
            Set<Long> todoRecordIds = btnGroupMap.keySet();
            boolean hasApprovalIds = !todoRecordIds.isEmpty() && rootFieldIds.idFieldId() != null;
            boolean canQueryDraft =
                    currentUserId != null
                            && rootFieldIds.createdByFieldId() != null
                            && rootFieldIds.businessStatusFieldId() != null;

            if (!hasApprovalIds && !canQueryDraft) {
                return new ApprovalFilterResult(filters, true);
            }

            FilterItem approvalBranch = null;
            if (hasApprovalIds) {
                approvalBranch =
                        FilterItem.builder()
                                .fieldId(rootFieldIds.idFieldId())
                                .operator("IN")
                                .value(new ArrayList<>(todoRecordIds))
                                .build();
            }

            FilterItem draftBranch = null;
            if (canQueryDraft) {
                List<FilterItem> draftItems = new ArrayList<>();
                draftItems.add(
                        FilterItem.builder()
                                .fieldId(rootFieldIds.createdByFieldId())
                                .operator("EQ")
                                .value(currentUserId)
                                .build());
                draftItems.add(
                        FilterItem.builder()
                                .fieldId(rootFieldIds.businessStatusFieldId())
                                .operator("EQ")
                                .value(1)
                                .build());
                draftBranch = FilterItem.builder().logic("AND").children(draftItems).build();
            }

            if (approvalBranch != null && draftBranch != null) {
                filters.add(
                        FilterItem.builder()
                                .logic("OR")
                                .children(Arrays.asList(approvalBranch, draftBranch))
                                .build());
            } else if (approvalBranch != null) {
                filters.add(approvalBranch);
            } else {
                filters.add(draftBranch);
            }
        } else if (approvalType == 2) {
            // 【已办】: 严格使用审批链已办数据
            Set<Long> doneRecordIds = btnGroupMap.keySet();
            if (doneRecordIds.isEmpty()) {
                return new ApprovalFilterResult(filters, true);
            }

            if (rootFieldIds.idFieldId() != null) {
                filters.add(
                        FilterItem.builder()
                                .fieldId(rootFieldIds.idFieldId())
                                .operator("IN")
                                .value(new ArrayList<>(doneRecordIds))
                                .build());
            } else {
                log.warn("模块 [{}] 未配置物理主键 [id] 字段，无法对已办 recordIds 进行 SQL 范围过滤", rootModuleId);
            }
        }

        return new ApprovalFilterResult(filters, false);
    }

    /** 组装记录树与行级审批按钮 */
    private AssembledRecordResult assembleRecordsAndButtons(
            List<Map<String, Object>> rawRecords,
            Long rootModuleId,
            Integer approvalType,
            Map<Long, List<Map<String, Object>>> btnGroupMap,
            Map<Long, Long> headerModuleMenuMap) {

        List<DataRecordItem> recordItems = new ArrayList<>();
        List<ApprovalRowItem> approvalRows = new ArrayList<>();
        Long currentUserId = UserContext.getUserId();

        if (rawRecords != null && !rawRecords.isEmpty()) {
            for (Map<String, Object> rawItem : rawRecords) {
                RootModuleMeta rootMeta = extractRootModuleMeta(rawItem, rootModuleId);
                Long recordId = rootMeta.id();
                List<Map<String, Object>> btnList =
                        (recordId != null) ? btnGroupMap.get(recordId) : null;

                boolean isDraftOrPendingMine =
                        currentUserId != null
                                && Objects.equals(currentUserId, rootMeta.createdBy())
                                && rootMeta.businessStatus() != null
                                && rootMeta.businessStatus() == 1;

                // 按钮装配与兜底
                if (approvalType == 1) {
                    if (btnList == null || btnList.isEmpty()) {
                        btnList = createSingleButton("handle", "办理");
                    }
                } else if (approvalType == 3) {
                    if (btnList == null || btnList.isEmpty()) {
                        btnList =
                                isDraftOrPendingMine
                                        ? createSingleButton("handle", "办理")
                                        : createSingleButton("view", "查阅");
                    }
                }

                DataRecordItem item = transformRecord(rootModuleId, rawItem, headerModuleMenuMap);
                recordItems.add(item);

                if (recordId != null && btnList != null && !btnList.isEmpty()) {
                    Map<String, Object> rowMap = new LinkedHashMap<>();
                    rowMap.put("id", recordId);
                    rowMap.put("btnList", btnList);
                    approvalRows.add(ApprovalRowItem.builder().row(rowMap).build());
                }
            }
        }

        return new AssembledRecordResult(recordItems, approvalRows);
    }

    private List<Map<String, Object>> createSingleButton(String buttonKey, String buttonName) {
        Map<String, Object> btn = new LinkedHashMap<>();
        btn.put("buttonKey", buttonKey);
        btn.put("buttonName", buttonName);
        return Collections.singletonList(btn);
    }

    private MenuDataResp buildEmptyMenuDataResp(
            MenuHeaderDisplayResp headerDisplay,
            ModuleSimplePermissionDTO simplePerm,
            Long rootModuleId,
            Long rootMenuId) {
        ModuleDataNode emptyDataTree =
                ModuleDataNode.builder()
                        .moduleId(rootModuleId)
                        .menuId(rootMenuId)
                        .record(Collections.emptyList())
                        .build();
        return MenuDataResp.builder()
                .header(headerDisplay.getHeader())
                .permission(simplePerm)
                .approval(null)
                .data(emptyDataTree)
                .build();
    }

    // =========================================================================
    // 4. 详情元数据装配与数据转换
    // =========================================================================

    private List<ModuleMetaDTO> assembleDetailMetaList(
            Long rootModuleId, Map<Long, Long> collectedModuleMenuMap) {
        List<ModuleMetaDTO> metaList = new ArrayList<>();
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        Long roleId = UserContext.getRoleId();

        Map<Long, ModulePermissionDTO> modulePerms =
                (roleId != null && !collectedModuleMenuMap.isEmpty())
                        ? configApi.getModulePermissions(roleId, collectedModuleMenuMap.keySet())
                        : Collections.emptyMap();
        ModuleTreeDTO treeDTO = configApi.getModuleTree(projectNo, subjectId, rootModuleId);

        for (Map.Entry<Long, Long> entry : collectedModuleMenuMap.entrySet()) {
            Long modId = entry.getKey();
            Long mId = entry.getValue();

            ModuleInfo modMeta = (treeDTO != null) ? treeDTO.getModule(modId) : null;
            Map<String, FieldMetaDTO> fieldsMap = new LinkedHashMap<>();

            ModulePermissionDTO modPerm = (modulePerms != null) ? modulePerms.get(modId) : null;
            Map<Long, FieldPermissionItem> fieldPermMap =
                    (modPerm != null && modPerm.getFieldPermissions() != null)
                            ? modPerm.getFieldPermissions()
                            : Collections.emptyMap();

            if (modMeta != null && modMeta.getFields() != null) {
                for (ModuleFieldDTO f : modMeta.getFields()) {
                    String tableName =
                            (f.getTableName() != null)
                                    ? f.getTableName()
                                    : modMeta.getPrimaryTable();
                    String fieldKey = tableName + "." + f.getColumnName();

                    FieldPermissionItem permItem = fieldPermMap.get(f.getId());
                    boolean canApply =
                            (roleId == null || permItem == null)
                                    || Boolean.TRUE.equals(permItem.getApply());
                    boolean canView =
                            (roleId == null || permItem == null)
                                    || Boolean.TRUE.equals(permItem.getView());
                    boolean canEdit =
                            (roleId == null || permItem == null)
                                    || Boolean.TRUE.equals(permItem.getEdit());

                    FieldMetaDTO fieldMeta =
                            FieldMetaDTO.builder()
                                    .id(f.getId())
                                    .displayName(f.getDisplayName())
                                    .apply(canApply)
                                    .view(canView)
                                    .edit(canEdit)
                                    .build();

                    fieldsMap.put(fieldKey, fieldMeta);
                }
            }

            metaList.add(
                    ModuleMetaDTO.builder().moduleId(modId).menuId(mId).fields(fieldsMap).build());
        }

        return metaList;
    }

    private void collectModuleMenuMap(ModuleDataNode treeNode, Map<Long, Long> moduleMenuMap) {
        if (treeNode == null) {
            return;
        }
        if (treeNode.getModuleId() != null) {
            moduleMenuMap.putIfAbsent(treeNode.getModuleId(), treeNode.getMenuId());
        }
        if (treeNode.getRecord() != null) {
            for (DataRecordItem item : treeNode.getRecord()) {
                if (item.getChildren() != null) {
                    for (ModuleDataNode childTree : item.getChildren()) {
                        collectModuleMenuMap(childTree, moduleMenuMap);
                    }
                }
            }
        }
    }

    /** 将单条原始记录转换为 DataRecordItem (提取平铺 table.column 的 row 与递归 children) */
    private DataRecordItem transformRecord(
            Long currentModuleId, Map<String, Object> rawMap, Map<Long, Long> moduleMenuMap) {
        return transformRecord(currentModuleId, rawMap, moduleMenuMap, 1);
    }

    @SuppressWarnings("unchecked")
    private DataRecordItem transformRecord(
            Long currentModuleId,
            Map<String, Object> rawMap,
            Map<Long, Long> moduleMenuMap,
            int depth) {
        if (rawMap == null || rawMap.isEmpty()) {
            return DataRecordItem.builder().row(Collections.emptyMap()).build();
        }

        if (depth > MAX_TREE_DEPTH) {
            log.warn(
                    "[DataAggregateService] 递归深度超出最大限制({}), 截断子模块装配: moduleId={}",
                    MAX_TREE_DEPTH,
                    currentModuleId);
            return DataRecordItem.builder().row(Collections.emptyMap()).build();
        }

        String modKey = String.valueOf(currentModuleId);
        Map<String, Object> contentMap = rawMap;
        if (rawMap.containsKey(modKey) && rawMap.get(modKey) instanceof Map<?, ?> innerMap) {
            contentMap = (Map<String, Object>) innerMap;
        }

        Map<String, Object> rowMap = new LinkedHashMap<>();
        List<ModuleDataNode> children = new ArrayList<>();

        for (Map.Entry<String, Object> entry : contentMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map<?, ?> nestedTableMap) {
                boolean isModuleKey = isNumeric(key);
                if (!isModuleKey) {
                    for (Map.Entry<?, ?> colEntry : nestedTableMap.entrySet()) {
                        String flatKey = key + "." + colEntry.getKey();
                        rowMap.put(flatKey, colEntry.getValue());
                    }
                } else {
                    if (!nestedTableMap.isEmpty()) {
                        Long childModId = Long.parseLong(key);
                        Long childMenuId =
                                moduleMenuMap != null ? moduleMenuMap.get(childModId) : null;
                        DataRecordItem childItem =
                                transformRecord(
                                        childModId,
                                        (Map<String, Object>) nestedTableMap,
                                        moduleMenuMap,
                                        depth + 1);
                        children.add(
                                ModuleDataNode.builder()
                                        .moduleId(childModId)
                                        .menuId(childMenuId)
                                        .record(Collections.singletonList(childItem))
                                        .build());
                    }
                }
            } else if (value instanceof List<?> childList) {
                if (isNumeric(key)) {
                    Long childModId = Long.parseLong(key);
                    Long childMenuId = moduleMenuMap != null ? moduleMenuMap.get(childModId) : null;
                    List<DataRecordItem> childRecords = new ArrayList<>();
                    for (Object childObj : childList) {
                        if (childObj instanceof Map<?, ?> childRowMap
                                && !((Map<?, ?>) childRowMap).isEmpty()) {
                            childRecords.add(
                                    transformRecord(
                                            childModId,
                                            (Map<String, Object>) childRowMap,
                                            moduleMenuMap,
                                            depth + 1));
                        }
                    }
                    children.add(
                            ModuleDataNode.builder()
                                    .moduleId(childModId)
                                    .menuId(childMenuId)
                                    .record(childRecords)
                                    .build());
                }
            } else {
                rowMap.put(key, value);
            }
        }

        return DataRecordItem.builder()
                .row(rowMap.isEmpty() ? null : rowMap)
                .children(children)
                .build();
    }

    // =========================================================================
    // 5. 底层提取与类型转换通用工具
    // =========================================================================

    private static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** 从根模块原始记录或展平后的行数据中统一提取根模块自身的 id, created_by 与 business_status 字段值 */
    @SuppressWarnings("unchecked")
    private RootModuleMeta extractRootModuleMeta(Map<String, Object> rawItem, Long rootModuleId) {
        if (rawItem == null || rawItem.isEmpty()) {
            return new RootModuleMeta(null, null, null);
        }

        Long id = null;
        Long createdBy = null;
        Integer businessStatus = null;

        // 1. 优先从根模块包裹中提取，例如 { "1001": { "member": { "id": 100, "created_by": 123,
        // "business_status": 1 } } }
        String modKey = String.valueOf(rootModuleId);
        if (rootModuleId != null
                && rawItem.containsKey(modKey)
                && rawItem.get(modKey) instanceof Map<?, ?> innerMap) {
            Map<String, Object> modContent = (Map<String, Object>) innerMap;
            if (modContent.containsKey("id")) {
                id = parseLong(modContent.get("id"));
            }
            if (modContent.containsKey("created_by")) {
                createdBy = parseLong(modContent.get("created_by"));
            } else if (modContent.containsKey("createdBy")) {
                createdBy = parseLong(modContent.get("createdBy"));
            }

            if (modContent.containsKey("business_status")) {
                businessStatus = parseInteger(modContent.get("business_status"));
            } else if (modContent.containsKey("businessStatus")) {
                businessStatus = parseInteger(modContent.get("businessStatus"));
            }

            for (Object subVal : modContent.values()) {
                if (subVal instanceof Map<?, ?> tableMap) {
                    if (id == null && tableMap.containsKey("id")) {
                        id = parseLong(tableMap.get("id"));
                    }
                    if (createdBy == null) {
                        if (tableMap.containsKey("created_by")) {
                            createdBy = parseLong(tableMap.get("created_by"));
                        } else if (tableMap.containsKey("createdBy")) {
                            createdBy = parseLong(tableMap.get("createdBy"));
                        }
                    }
                    if (businessStatus == null) {
                        if (tableMap.containsKey("business_status")) {
                            businessStatus = parseInteger(tableMap.get("business_status"));
                        } else if (tableMap.containsKey("businessStatus")) {
                            businessStatus = parseInteger(tableMap.get("businessStatus"));
                        }
                    }
                }
            }
        }

        // 2. 兜底从根属性中提取或通过遍历带点 key 提取
        if (id == null) {
            Object idVal = rawItem.get("id");
            if (idVal != null) {
                id = parseLong(idVal);
            } else {
                for (Map.Entry<String, Object> entry : rawItem.entrySet()) {
                    if (entry.getKey().endsWith(".id")) {
                        id = parseLong(entry.getValue());
                        break;
                    }
                }
            }
        }

        if (createdBy == null) {
            Object val = rawItem.get("created_by");
            if (val == null) {
                val = rawItem.get("createdBy");
            }
            if (val != null) {
                createdBy = parseLong(val);
            }
        }

        if (businessStatus == null) {
            Object val = rawItem.get("business_status");
            if (val == null) {
                val = rawItem.get("businessStatus");
            }
            if (val != null) {
                businessStatus = parseInteger(val);
            }
        }

        return new RootModuleMeta(id, createdBy, businessStatus);
    }

    /** 从元数据配置中提取根模块的 id, created_by, business_status 字段 ID */
    private RootModuleFieldIds extractRootModuleFieldIds(Long rootModuleId) {
        if (rootModuleId == null) {
            return new RootModuleFieldIds(null, null, null);
        }

        ModuleTreeDTO moduleTree =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);
        Long idFieldId = null;
        Long createdByFieldId = null;
        Long businessStatusFieldId = null;

        if (moduleTree != null && moduleTree.getRootModule() != null) {
            List<ModuleFieldDTO> fields = moduleTree.getRootModule().getFields();
            if (fields != null) {
                for (ModuleFieldDTO f : fields) {
                    String col = f.getColumnName();
                    if ("id".equalsIgnoreCase(col)) {
                        idFieldId = f.getId();
                    } else if ("created_by".equalsIgnoreCase(col)
                            || "createdBy".equalsIgnoreCase(col)) {
                        createdByFieldId = f.getId();
                    } else if ("business_status".equalsIgnoreCase(col)
                            || "businessStatus".equalsIgnoreCase(col)) {
                        businessStatusFieldId = f.getId();
                    }
                }
            }
        }

        return new RootModuleFieldIds(idFieldId, createdByFieldId, businessStatusFieldId);
    }

    private Long parseRecordIdFromRaw(Object rIdObj) {
        if (rIdObj instanceof Number num) {
            return num.longValue();
        } else if (rIdObj != null) {
            try {
                return Long.valueOf(rIdObj.toString());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Integer parseInteger(Object val) {
        if (val instanceof Number num) {
            return num.intValue();
        } else if (val != null) {
            try {
                return Integer.valueOf(val.toString());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Long parseLong(Object val) {
        if (val instanceof Number num) {
            return num.longValue();
        } else if (val != null) {
            try {
                return Long.valueOf(val.toString());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private record ApprovalButtonsGroup(
            Map<Long, List<Map<String, Object>>> todoBtns,
            Map<Long, List<Map<String, Object>>> doneBtns,
            Map<Long, List<Map<String, Object>>> allBtns) {}
}
