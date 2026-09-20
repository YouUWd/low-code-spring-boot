package com.jdec.platform.data.biz.service;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModulePermissionDTO;
import com.jdec.platform.config.api.dto.common.ModulePermissionDTO.FieldPermissionItem;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.UserMenuHeaderApi;
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
 * 动态数据聚合包装服务 (DataAggregateService)
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

        // 1. 通过 UserMenuHeaderApi 获取多级表头展示结构
        MenuHeaderDisplayResp headerDisplay = getMenuHeaderDisplay(menuId);
        if (headerDisplay == null || headerDisplay.getHeader() == null) {
            throw new BusinessException("菜单 [" + menuId + "] 未配置有效的表头展示信息");
        }

        MenuHeaderColumnResp rootHeader = headerDisplay.getHeader();
        Long rootModuleId = rootHeader.getModuleId();
        if (rootModuleId == null) {
            throw new BusinessException("菜单 [" + menuId + "] 绑定的根模块 ID 为空");
        }

        // 2. 提取所有已勾选的字段 ID 列表与模块菜单映射
        List<Long> headerFields = extractFieldIdsFromHeader(headerDisplay);
        Map<Long, Long> headerModuleMenuMap = extractModuleMenuMapFromHeader(headerDisplay);

        // 3. 构建底层 DataApi 的 QueryReq 请求
        QueryReq queryReq =
                QueryReq.builder()
                        .moduleId(rootModuleId)
                        .pageNo(req != null ? req.getPageNo() : null)
                        .pageSize(req != null ? req.getPageSize() : null)
                        .fields(headerFields)
                        .filters(req != null ? req.getFilters() : null)
                        .sorts(req != null ? req.getSorts() : null)
                        .build();

        // 4. 调用底层 DataApi 执行查询
        DataPage<Map<String, Object>> page = dataApi.query(queryReq);
        List<Map<String, Object>> rawRecords =
                (page != null) ? page.getRecords() : Collections.emptyList();

        // 5. 转换装配为 DataRecordItem 列表
        List<DataRecordItem> recordItems = new ArrayList<>();
        if (rawRecords != null && !rawRecords.isEmpty()) {
            for (Map<String, Object> rawItem : rawRecords) {
                DataRecordItem item = transformRecord(rootModuleId, rawItem, headerModuleMenuMap);
                recordItems.add(item);
            }
        }

        // 6. 构建根 ModuleDataNode 数据树 (根节点的 menuId 优先使用表头配置，或回退到传入的 menuId)
        Long rootMenuId = headerModuleMenuMap.getOrDefault(rootModuleId, menuId);
        ModuleDataNode dataTree =
                ModuleDataNode.builder()
                        .moduleId(rootModuleId)
                        .menuId(rootMenuId)
                        .record(recordItems)
                        .build();

        // 7. 查询根模块及子模块汇总权限 (转换为 1/0)
        ModuleSimplePermissionDTO simplePerm = null;
        Long roleId = UserContext.getRoleId();
        if (roleId != null) {
            Map<Long, ModulePermissionDTO> permMap =
                    configApi.getModulePermissions(roleId, Collections.singletonList(rootModuleId));
            if (permMap != null && permMap.containsKey(rootModuleId)) {
                ModulePermissionDTO rootPerm = permMap.get(rootModuleId);
                simplePerm =
                        ModuleSimplePermissionDTO.builder()
                                .apply(Boolean.TRUE.equals(rootPerm.getApply()) ? 1 : 0)
                                .view(Boolean.TRUE.equals(rootPerm.getView()) ? 1 : 0)
                                .edit(Boolean.TRUE.equals(rootPerm.getEdit()) ? 1 : 0)
                                .build();
            }
        }

        // 8. 查询审批链按钮数据
        List<ApprovalRowItem> approvalRows = new ArrayList<>();
        try {
            Integer approvalType =
                    (req != null && req.getApprovalType() != null) ? req.getApprovalType() : 3;
            List<Map<String, Object>> approvalRawList =
                    approvalEngine.getListButton(rootModuleId, approvalType);

            if (approvalRawList != null && !approvalRawList.isEmpty()) {
                Map<Long, List<Map<String, Object>>> btnGroupMap = new LinkedHashMap<>();

                for (Map<String, Object> rawBtn : approvalRawList) {
                    if (rawBtn == null) {
                        continue;
                    }
                    Long recordId = null;
                    Object rIdObj = rawBtn.get("recordId");
                    if (rIdObj instanceof Number num) {
                        recordId = num.longValue();
                    } else if (rIdObj != null) {
                        try {
                            recordId = Long.valueOf(rIdObj.toString());
                        } catch (Exception ignored) {
                        }
                    }

                    if (recordId == null) {
                        continue;
                    }

                    // 构造纯净的按钮对象 (移除 recordId，仅保留按钮属性)
                    Map<String, Object> btnInfo = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> e : rawBtn.entrySet()) {
                        if (!"recordId".equals(e.getKey())) {
                            btnInfo.put(e.getKey(), e.getValue());
                        }
                    }

                    btnGroupMap.computeIfAbsent(recordId, k -> new ArrayList<>()).add(btnInfo);
                }

                for (Map.Entry<Long, List<Map<String, Object>>> entry : btnGroupMap.entrySet()) {
                    Map<String, Object> rowMap = new LinkedHashMap<>();
                    rowMap.put("id", entry.getKey());
                    rowMap.put("btnList", entry.getValue());
                    approvalRows.add(ApprovalRowItem.builder().row(rowMap).build());
                }
            }
        } catch (Exception ex) {
            log.warn("获取审批链按钮数据失败或当前模块未配置审批流: moduleId={}, err={}", rootModuleId, ex.getMessage());
        }

        // 9. 组装顶层包含 header, permission, approval 和 data 的响应
        return MenuDataResp.builder()
                .header(headerDisplay.getHeader())
                .permission(simplePerm)
                .approval(approvalRows.isEmpty() ? null : approvalRows)
                .data(dataTree)
                .build();
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

        // 1. 通过 UserMenuHeaderApi 获取多级表头展示结构
        MenuHeaderDisplayResp headerDisplay = getMenuHeaderDisplay(menuId);
        if (headerDisplay == null || headerDisplay.getHeader() == null) {
            throw new BusinessException("菜单 [" + menuId + "] 未配置有效的表头展示信息");
        }

        MenuHeaderColumnResp rootHeader = headerDisplay.getHeader();
        Long rootModuleId = rootHeader.getModuleId();
        if (rootModuleId == null) {
            throw new BusinessException("菜单 [" + menuId + "] 绑定的根模块 ID 为空");
        }

        // 2. 提取所有已勾选的字段 ID 列表与模块菜单映射
        List<Long> headerFields = extractFieldIdsFromHeader(headerDisplay);
        Map<Long, Long> headerModuleMenuMap = extractModuleMenuMapFromHeader(headerDisplay);

        // 3. 调用单据详情查询
        return getDetailTree(rootModuleId, id, headerFields, menuId, headerModuleMenuMap);
    }

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
        // nodeType=0 (菜单节点) 时，sourceId 为菜单 ID，moduleId 为绑定的模块 ID
        if (node.getModuleId() != null && node.getSourceId() != null) {
            map.putIfAbsent(node.getModuleId(), node.getSourceId());
        }
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            for (MenuHeaderColumnResp child : node.getChildren()) {
                collectModuleMenuMapRecursively(child, map);
            }
        }
    }

    /**
     * 单据详情查询 (返回携带当前模块及其所有子模块的元数据列表 meta 与数据 data)
     *
     * @param moduleId 模块 ID
     * @param id 单据 ID
     * @param fields 投影字段
     * @param menuId 菜单 ID
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

        // 2. 收集结构中涉及的所有模块及其 menuId (Map<moduleId, menuId>)
        Map<Long, Long> collectedModuleMenuMap = new LinkedHashMap<>();
        collectModuleMenuMap(treeData, collectedModuleMenuMap);

        List<ModuleMetaDTO> metaList = new ArrayList<>();
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        Long roleId = UserContext.getRoleId();

        // 批量获取涉及模块的权限信息
        Map<Long, ModulePermissionDTO> modulePerms =
                (roleId != null && !collectedModuleMenuMap.isEmpty())
                        ? configApi.getModulePermissions(roleId, collectedModuleMenuMap.keySet())
                        : Collections.emptyMap();

        for (Map.Entry<Long, Long> entry : collectedModuleMenuMap.entrySet()) {
            Long modId = entry.getKey();
            Long mId = entry.getValue();

            ModuleInfo modMeta = configApi.getModuleWithFields(projectNo, subjectId, modId);
            Map<String, FieldMetaDTO> fieldsMap = new LinkedHashMap<>();

            ModulePermissionDTO modPerm = modulePerms != null ? modulePerms.get(modId) : null;
            Map<Long, FieldPermissionItem> fieldPermMap =
                    (modPerm != null && modPerm.getFieldPermissions() != null)
                            ? modPerm.getFieldPermissions()
                            : Collections.emptyMap();

            if (modMeta != null && modMeta.getFields() != null) {
                for (ModuleFieldDTO f : modMeta.getFields()) {
                    String tableName =
                            f.getTableName() != null ? f.getTableName() : modMeta.getPrimaryTable();
                    String fieldKey = tableName + "." + f.getColumnName();

                    FieldPermissionItem permItem = fieldPermMap.get(f.getId());

                    boolean canApply = permItem != null && Boolean.TRUE.equals(permItem.getApply());
                    boolean canView = permItem != null && Boolean.TRUE.equals(permItem.getView());
                    boolean canEdit = permItem != null && Boolean.TRUE.equals(permItem.getEdit());

                    // 若未配置权限角色或不在权限控制中，默认或者根据配置项生效
                    if (roleId == null || permItem == null) {
                        canApply = true;
                        canView = true;
                        canEdit = true;
                    }

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

        return MenuDetailResp.of(metaList, treeData);
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

    /** 最大递归装配深度，防止脏数据或循环依赖引发栈溢出 */
    private static final int MAX_TREE_DEPTH = 8;

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

        // 如果外层包裹了当前模块ID的键: { "1003": { "member": {...}, "1004": [...] } }
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
                // 嵌套物理表: 如 "member": { "id": 1001, "name": "经理办" }
                // 展平成 "member.id": 1001, "member.name": "经理办"
                boolean isModuleKey = isNumeric(key);
                if (!isModuleKey) {
                    for (Map.Entry<?, ?> colEntry : nestedTableMap.entrySet()) {
                        String flatKey = key + "." + colEntry.getKey();
                        rowMap.put(flatKey, colEntry.getValue());
                    }
                } else {
                    // 如果 key 是子模块 ID (1:1 关系挂载的 Map)
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
                // 1:N 子模块/从表集合
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
                // 已经是带点或者直接的字段
                rowMap.put(key, value);
            }
        }

        return DataRecordItem.builder()
                .row(rowMap.isEmpty() ? null : rowMap)
                .children(children)
                .build();
    }

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
}
