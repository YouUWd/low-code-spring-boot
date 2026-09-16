package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.SysMenuHeaderApi;
import com.jdec.platform.config.api.dto.request.SaveMenuReq;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
import com.jdec.platform.config.api.dto.response.MenuHeaderSearchTypeResp;
import com.jdec.platform.config.api.dto.response.SysMenuHeaderResp;
import com.jdec.platform.config.biz.entity.SysMenu;
import com.jdec.platform.config.biz.entity.SysMenuHeader;
import com.jdec.platform.config.biz.entity.SysModuleField;
import com.jdec.platform.config.biz.mapper.SysMenuHeaderMapper;
import com.jdec.platform.config.biz.mapper.SysMenuMapper;
import com.jdec.platform.config.biz.mapper.SysModuleFieldMapper;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 列表表头节点 Service 实现 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysMenuHeaderService implements SysMenuHeaderApi {

    /** 节点类型：菜单 */
    private static final Integer NODE_TYPE_MENU = 0;

    /** 节点类型：字段 */
    private static final Integer NODE_TYPE_FIELD = 1;

    /** 未选中 */
    private static final Integer CHECKED_NO = 0;

    /** 不省略 */
    private static final Integer ELLIPSIS_NO = 0;

    /** 已勾选 */
    private static final Integer CHECKED_YES = 1;

    /** 可排序 */
    private static final Integer SORTED_YES = 1;

    /** 同级排序值起始值 */
    private static final Integer SORT_VALUE_FIRST = 1;

    /** 虚拟id前缀：菜单 */
    private static final String VIR_ID_PREFIX_MENU = "MENU";

    /** 虚拟id前缀：字段 */
    private static final String VIR_ID_PREFIX_FIELD = "Field";

    /** 顶层节点的父节点虚拟id */
    private static final String ROOT_PARENT_ID = "0";

    /** 字段索引分隔符 */
    private static final String DATA_INDEX_SEPARATOR = ".";

    /** 变更标记：菜单关联的详情菜单已切换（表头配置尚未更新入库），0 表示未变化 */
    private static final Integer CHANGE_DETAIL_SWITCHED = 1;

    private final SysMenuHeaderMapper sysMenuHeaderMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysModuleFieldMapper sysModuleFieldMapper;
    private final ObjectMapper objectMapper;

    @Override
    public SysMenuHeaderResp getMenuHeaderTree(Long menuId, Integer change) {
        if (menuId == null) {
            throw new BusinessException("菜单ID不能为空");
        }

        // 生成侧：以该菜单为根，递归全部子级菜单，取各菜单绑定模块下的字段
        List<SysMenuHeaderResp> generatedNodes = buildGeneratedNodes(menuId);

        // 菜单关联的详情菜单已切换（表头配置尚未更新入库）：只用生成侧，不读表
        if (CHANGE_DETAIL_SWITCHED.equals(change)) {
            return assembleTree(menuId, generatedNodes);
        }

        // 详情菜单未变化：与 sys_menu_header 已保存配置按虚拟id 合并
        Set<String> generatedVirIds =
                generatedNodes.stream()
                        .map(SysMenuHeaderResp::getVirId)
                        .collect(Collectors.toSet());
        Map<String, SysMenuHeader> storedByVirId = new LinkedHashMap<>();
        for (SysMenuHeader stored : selectStoredNodes(menuId)) {
            storedByVirId.putIfAbsent(stored.getVirId(), stored);
        }
        List<SysMenuHeaderResp> mergedNodes = new ArrayList<>();
        for (SysMenuHeaderResp node : generatedNodes) {
            SysMenuHeader stored = storedByVirId.get(node.getVirId());
            // 两边都有同一虚拟id → 取表中记录（保留用户配置）；仅生成侧有 → 用生成侧默认值
            mergedNodes.add(stored != null ? mergeWithStored(node, stored, generatedVirIds) : node);
        }

        // 仅表中有的节点不再返回（丢弃）；递归子菜单生成的节点直接返回，不落库、不带主键 id
        return assembleTree(menuId, mergedNodes);
    }

    /**
     * 随菜单一并保存多级表头配置（由 {@code SysMenuService#saveMenu} 内部调用）
     *
     * <p>表头记录按「归属菜单ID」存放，归属菜单即菜单关联的详情菜单；未关联详情菜单的菜单不允许保存表头配置。
     *
     * <ul>
     *   <li>{@code change=1}（详情菜单已切换）：先删除变更前归属菜单（{@code previousMenuId}）在 {@code sys_menu_header}
     *       中的全部记录（一条 SQL），再按本次提交的节点树以 {@code menuId} 为归属重新落库。
     *   <li>{@code change=0}（详情菜单未变化）：将 {@code header} 树压平后与 {@code menuId} 已保存节点按 {@code virId}
     *       比对，已存在的节点按主键批量更新，未存在的节点批量新增。
     * </ul>
     *
     * <p>{@code header} 为空时只做上述清理、不做落库。数据隔离按 {@code subject_id} + {@code project_no}，所有写操作均为批量执行。
     *
     * @param menuId 本次表头记录的归属菜单ID（菜单关联的详情菜单ID）
     * @param previousMenuId 变更前表头记录的归属菜单ID（变更前的详情菜单ID），无变更或新增菜单时为 {@code null}
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveMenuHeader(
            Long menuId, Long previousMenuId, Integer change, SaveMenuReq.MenuHeaderNode header) {
        Long subjectId = AppContext.getSubjectId();
        String projectNo = AppContext.getProjectNo();

        // 详情菜单已切换：删除变更前归属菜单在 sys_menu_header 中的全部记录（一条 SQL）
        if (CHANGE_DETAIL_SWITCHED.equals(change) && previousMenuId != null) {
            int deleted =
                    sysMenuHeaderMapper.delete(
                            Wrappers.<SysMenuHeader>lambdaQuery()
                                    .eq(SysMenuHeader::getMenuId, previousMenuId)
                                    .eq(SysMenuHeader::getSubjectId, subjectId)
                                    .eq(SysMenuHeader::getProjectNo, projectNo));
            log.debug("详情菜单已切换，删除原归属菜单表头配置：previousMenuId={}, rows={}", previousMenuId, deleted);
        }

        // 未提交表头节点树时不做落库处理
        if (header == null) {
            return;
        }

        // 表头记录挂在该菜单的详情菜单下，未关联详情菜单的菜单不允许保存表头
        if (menuId == null) {
            throw new BusinessException("菜单未关联详情菜单，不能保存表头配置");
        }

        // 提交的树压平为节点列表（深度优先、父节点在前），父引用直接落父节点虚拟id
        List<SaveMenuReq.MenuHeaderNode> nodes = new ArrayList<>();
        flatten(header, nodes);

        // 一次性查出该菜单已保存的节点并按虚拟id 建索引，避免循环内查库
        Map<String, Long> storedIdByVirId = new HashMap<>();
        for (SysMenuHeader stored : selectStoredNodes(menuId)) {
            storedIdByVirId.putIfAbsent(stored.getVirId(), stored.getId());
        }

        // 已保存的节点按主键批量更新，未保存的节点批量新增
        List<SysMenuHeader> insertList = new ArrayList<>();
        List<SysMenuHeader> updateList = new ArrayList<>();
        for (SaveMenuReq.MenuHeaderNode node : nodes) {
            if (node.getVirId() == null || node.getVirId().trim().isEmpty()) {
                throw new BusinessException("表头节点虚拟id不能为空");
            }
            SysMenuHeader entity = toHeaderEntity(menuId, node);
            Long storedId = storedIdByVirId.get(node.getVirId());
            if (storedId != null) {
                entity.setId(storedId);
                updateList.add(entity);
            } else {
                entity.setSubjectId(subjectId);
                entity.setProjectNo(projectNo);
                insertList.add(entity);
            }
        }

        if (!insertList.isEmpty()) {
            sysMenuHeaderMapper.insert(insertList);
        }
        if (!updateList.isEmpty()) {
            sysMenuHeaderMapper.updateById(updateList);
        }
        log.debug("保存菜单表头配置：menuId={}, 新增={}, 更新={}", menuId, insertList.size(), updateList.size());
    }

    @Override
    public MenuHeaderDisplayResp getMenuHeaderDisplay(Long menuId) {
        if (menuId == null) {
            throw new BusinessException("菜单ID不能为空");
        }

        // 纯读取该菜单已保存的表头节点（数据隔离：subject_id + project_no）
        List<SysMenuHeader> stored =
                sysMenuHeaderMapper.selectList(
                        Wrappers.<SysMenuHeader>lambdaQuery()
                                .eq(SysMenuHeader::getMenuId, menuId)
                                .eq(SysMenuHeader::getSubjectId, AppContext.getSubjectId())
                                .eq(SysMenuHeader::getProjectNo, AppContext.getProjectNo())
                                .orderByAsc(SysMenuHeader::getSortValue, SysMenuHeader::getId));
        // 展示只输出已勾选的列，并保留其祖先菜单节点用于组装多级列头
        List<SysMenuHeader> nodes = retainCheckedWithAncestors(stored);
        if (nodes.isEmpty()) {
            return new MenuHeaderDisplayResp();
        }

        // 按父节点虚拟id分组（保留查询顺序，parent_id 为 null/0 的是顶层节点）
        Map<String, List<SysMenuHeader>> childrenByParent = new LinkedHashMap<>();
        List<SysMenuHeader> topNodes = new ArrayList<>();
        for (SysMenuHeader node : nodes) {
            String parentId = node.getParentId();
            if (parentId == null || ROOT_PARENT_ID.equals(parentId)) {
                topNodes.add(node);
            } else {
                childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(node);
            }
        }

        SysMenuHeader root = selectDisplayRoot(menuId, topNodes);
        if (root == null) {
            return new MenuHeaderDisplayResp();
        }
        return MenuHeaderDisplayResp.builder().header(toColumnResp(root, childrenByParent)).build();
    }

    /** 深度优先压平提交的表头节点树（父节点在前） */
    private void flatten(SaveMenuReq.MenuHeaderNode node, List<SaveMenuReq.MenuHeaderNode> nodes) {
        nodes.add(node);
        List<SaveMenuReq.MenuHeaderNode> children = node.getChildren();
        if (children == null) {
            return;
        }
        for (SaveMenuReq.MenuHeaderNode child : children) {
            flatten(child, nodes);
        }
    }

    /** 提交节点转实体：归属菜单统一取入参菜单ID，父引用为空时按顶层处理 */
    private SysMenuHeader toHeaderEntity(Long menuId, SaveMenuReq.MenuHeaderNode node) {
        SysMenuHeader entity = new SysMenuHeader();
        entity.setMenuId(menuId);
        entity.setVirId(node.getVirId());
        entity.setParentId(node.getParentId() != null ? node.getParentId() : ROOT_PARENT_ID);
        entity.setNodeType(node.getNodeType());
        entity.setSourceId(node.getSourceId());
        entity.setModuleId(node.getModuleId());
        entity.setLabel(node.getLabel());
        entity.setColors(toColorsJson(node.getColors()));
        entity.setWidth(node.getWidth());
        entity.setDataIndex(node.getDataIndex());
        entity.setSorted(node.getSorted());
        entity.setSearchType(node.getSearchType());
        entity.setEllipsis(node.getEllipsis());
        entity.setFixed(node.getFixed());
        entity.setChecked(node.getChecked());
        entity.setSortValue(node.getSortValue());
        return entity;
    }

    // ==================== 多级列头展示组装 ====================

    /**
     * 展示节点筛选：只保留已勾选（{@code checked=1}）的节点，并向上补齐其祖先节点
     *
     * <p>祖先节点不论是否勾选都保留，否则被勾选的下级列挂不到父节点上、多级列头组装不出来； 没有任何勾选节点时返回空列表。
     */
    private List<SysMenuHeader> retainCheckedWithAncestors(List<SysMenuHeader> nodes) {
        Map<String, SysMenuHeader> nodeByVirId = new LinkedHashMap<>();
        for (SysMenuHeader node : nodes) {
            nodeByVirId.putIfAbsent(node.getVirId(), node);
        }
        Set<String> retainedVirIds = new HashSet<>();
        for (SysMenuHeader node : nodes) {
            if (!CHECKED_YES.equals(node.getChecked())) {
                continue;
            }
            retainedVirIds.add(node.getVirId());
            // 逐级向上补齐祖先节点，遇到已保留的节点或父节点缺失即终止
            String parentId = node.getParentId();
            while (parentId != null && !ROOT_PARENT_ID.equals(parentId)) {
                SysMenuHeader parent = nodeByVirId.get(parentId);
                if (parent == null || !retainedVirIds.add(parentId)) {
                    break;
                }
                parentId = parent.getParentId();
            }
        }
        return nodes.stream()
                .filter(node -> retainedVirIds.contains(node.getVirId()))
                .collect(Collectors.toList());
    }

    /** 取顶层节点中的根节点，优先本菜单自身对应的菜单节点 */
    private SysMenuHeader selectDisplayRoot(Long menuId, List<SysMenuHeader> topNodes) {
        if (topNodes.isEmpty()) {
            return null;
        }
        for (SysMenuHeader node : topNodes) {
            if (isMenuNode(node) && menuId.equals(node.getSourceId())) {
                return node;
            }
        }
        for (SysMenuHeader node : topNodes) {
            if (isMenuNode(node)) {
                return node;
            }
        }
        return topNodes.getFirst();
    }

    /** 递归组装多级列头展示节点：菜单节点输出 sourceId/colors，字段节点输出字段展示配置 */
    private MenuHeaderColumnResp toColumnResp(
            SysMenuHeader node, Map<String, List<SysMenuHeader>> childrenByParent) {
        MenuHeaderColumnResp resp = new MenuHeaderColumnResp();
        resp.setLabel(node.getLabel());
        resp.setModuleId(node.getModuleId());
        if (isMenuNode(node)) {
            resp.setSourceId(node.getSourceId());
            resp.setColors(parseColors(node.getColors()));
        } else {
            resp.setFieldId(node.getSourceId());
            resp.setDataIndex(node.getDataIndex());
            resp.setWidth(node.getWidth());
            resp.setSorted(node.getSorted());
            resp.setSearchType(parseSearchType(node.getSearchType()));
            resp.setEllipsis(node.getEllipsis());
            resp.setFixed(node.getFixed());
            resp.setSortValue(node.getSortValue());
        }

        List<SysMenuHeader> children =
                childrenByParent.getOrDefault(node.getVirId(), Collections.emptyList());
        if (!children.isEmpty()) {
            resp.setChildren(
                    children.stream()
                            .map(child -> toColumnResp(child, childrenByParent))
                            .collect(Collectors.toList()));
        }
        return resp;
    }

    /** 序列化 colors（入参为颜色列表，库中存 JSON 数组串），空列表或失败返回 null */
    private String toColorsJson(List<String> colors) {
        if (colors == null || colors.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(colors);
        } catch (Exception e) {
            log.warn("菜单表头 colors 序列化失败: {}", colors, e);
            return null;
        }
    }

    /** 解析 colors（库中为 JSON 数组串），失败或空返回 null */
    private List<String> parseColors(String colors) {
        if (colors == null || colors.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(colors, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("菜单表头 colors 解析失败: {}", colors, e);
            return null;
        }
    }

    /** 解析 searchType（库中为 JSON 对象串），失败或空返回 null */
    private MenuHeaderSearchTypeResp parseSearchType(String searchType) {
        if (searchType == null || searchType.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(searchType, MenuHeaderSearchTypeResp.class);
        } catch (Exception e) {
            log.warn("菜单表头 searchType 解析失败: {}", searchType, e);
            return null;
        }
    }

    // ==================== 生成侧：菜单 + 模块字段 ====================

    /**
     * 生成侧节点列表（深度优先、父节点在前）
     *
     * <p>以该菜单为根递归其全部子级菜单，取每个菜单绑定模块（sys_module_field）下的字段：菜单节点虚拟id 为 {@code MENU_{菜单id}}，字段节点为
     * {@code Field_{字段id}}；所有节点的 menu_id 统一取最顶层菜单ID， 顶层节点 parent_id 为 {@code 0}，同级节点从 {@code 1}
     * 起维护排序值。
     */
    private List<SysMenuHeaderResp> buildGeneratedNodes(Long menuId) {
        SysMenu menu = selectMenuById(menuId);
        if (menu == null) {
            throw new BusinessException("菜单不存在");
        }

        // 收集菜单及其全部子级菜单（树形）
        Map<Long, List<SysMenu>> childrenByPid = new LinkedHashMap<>();
        collectChildren(childrenByPid, menuId);

        // 收集所有菜单绑定到的模块ID，一次性查询模块字段
        Set<Long> moduleIds = new HashSet<>();
        if (menu.getModuleId() != null) {
            moduleIds.add(menu.getModuleId());
        }
        for (List<SysMenu> children : childrenByPid.values()) {
            for (SysMenu child : children) {
                if (child.getModuleId() != null) {
                    moduleIds.add(child.getModuleId());
                }
            }
        }
        Map<Long, List<SysModuleField>> fieldsByModule = selectFieldsGroupByModule(moduleIds);

        // 先按树形生成，再压平为节点列表
        SysMenuHeaderResp root =
                toGeneratedNode(
                        menu,
                        menuId,
                        ROOT_PARENT_ID,
                        childrenByPid,
                        fieldsByModule,
                        SORT_VALUE_FIRST);
        List<SysMenuHeaderResp> nodes = new ArrayList<>();
        flatten(root, nodes);
        return nodes;
    }

    /**
     * 递归生成单个菜单节点（含其绑定模块下的字段与子级菜单节点）
     *
     * @param menu 当前菜单
     * @param rootMenuId 最顶层菜单ID（所有节点的 menu_id 均取该值）
     * @param parentVirId 父节点虚拟id（顶层为 {@code 0}）
     * @param sortValue 当前节点在同级中的排序值
     */
    private SysMenuHeaderResp toGeneratedNode(
            SysMenu menu,
            Long rootMenuId,
            String parentVirId,
            Map<Long, List<SysMenu>> childrenByPid,
            Map<Long, List<SysModuleField>> fieldsByModule,
            Integer sortValue) {
        String currentVirId = menuVirId(menu.getId());

        SysMenuHeaderResp resp = new SysMenuHeaderResp();
        resp.setMenuId(rootMenuId);
        resp.setNodeType(NODE_TYPE_MENU);
        resp.setVirId(currentVirId);
        resp.setParentId(parentVirId);
        resp.setSourceId(menu.getId());
        resp.setModuleId(menu.getModuleId());
        resp.setLabel(menu.getTitle());
        resp.setSorted(SORTED_YES);
        resp.setEllipsis(ELLIPSIS_NO);
        resp.setChecked(CHECKED_NO);
        resp.setSortValue(sortValue);

        List<SysMenuHeaderResp> children = new ArrayList<>();
        int childSortValue = SORT_VALUE_FIRST;

        // 当前菜单绑定模块下的全部字段
        List<SysModuleField> fields =
                fieldsByModule.getOrDefault(menu.getModuleId(), Collections.emptyList());
        for (SysModuleField field : fields) {
            SysMenuHeaderResp fieldResp = new SysMenuHeaderResp();
            fieldResp.setMenuId(rootMenuId);
            fieldResp.setNodeType(NODE_TYPE_FIELD);
            fieldResp.setVirId(fieldVirId(field.getId()));
            fieldResp.setParentId(currentVirId);
            fieldResp.setSourceId(field.getId());
            fieldResp.setModuleId(field.getModuleId());
            fieldResp.setDataIndex(buildDataIndex(field));
            fieldResp.setLabel(firstNotBlank(field.getDisplayName(), field.getColumnName()));
            fieldResp.setSorted(SORTED_YES);
            fieldResp.setEllipsis(ELLIPSIS_NO);
            fieldResp.setChecked(CHECKED_NO);
            fieldResp.setSortValue(childSortValue++);
            children.add(fieldResp);
        }

        // 递归挂载子级菜单节点
        List<SysMenu> subMenus = childrenByPid.getOrDefault(menu.getId(), Collections.emptyList());
        for (SysMenu subMenu : subMenus) {
            children.add(
                    toGeneratedNode(
                            subMenu,
                            rootMenuId,
                            currentVirId,
                            childrenByPid,
                            fieldsByModule,
                            childSortValue++));
        }
        resp.setChildren(children);
        return resp;
    }

    /** 深度优先压平为节点列表（父节点在子节点之前） */
    private void flatten(SysMenuHeaderResp node, List<SysMenuHeaderResp> nodes) {
        nodes.add(node);
        List<SysMenuHeaderResp> children = node.getChildren();
        if (children == null) {
            return;
        }
        for (SysMenuHeaderResp child : children) {
            flatten(child, nodes);
        }
    }

    // ==================== 与已保存配置的合并 ====================

    /** 读取该菜单已保存的表头节点（数据隔离：subject_id + project_no） */
    private List<SysMenuHeader> selectStoredNodes(Long menuId) {
        return sysMenuHeaderMapper.selectList(
                Wrappers.<SysMenuHeader>lambdaQuery()
                        .eq(SysMenuHeader::getMenuId, menuId)
                        .eq(SysMenuHeader::getSubjectId, AppContext.getSubjectId())
                        .eq(SysMenuHeader::getProjectNo, AppContext.getProjectNo())
                        .orderByAsc(SysMenuHeader::getSortValue, SysMenuHeader::getId));
    }

    /**
     * 合并同一虚拟id 的两侧数据：表中记录优先（保留用户配置），表内未配置的项取生成侧默认值
     *
     * @param generated 生成侧节点
     * @param stored 表中已保存的节点
     * @param generatedVirIds 生成侧全部虚拟id，用于判断表中父节点是否仍然有效
     */
    private SysMenuHeaderResp mergeWithStored(
            SysMenuHeaderResp generated, SysMenuHeader stored, Set<String> generatedVirIds) {
        SysMenuHeaderResp resp = new SysMenuHeaderResp();
        resp.setId(stored.getId());
        resp.setVirId(stored.getVirId());
        resp.setMenuId(stored.getMenuId() != null ? stored.getMenuId() : generated.getMenuId());
        resp.setNodeType(
                stored.getNodeType() != null ? stored.getNodeType() : generated.getNodeType());
        resp.setSourceId(
                stored.getSourceId() != null ? stored.getSourceId() : generated.getSourceId());
        resp.setModuleId(
                stored.getModuleId() != null ? stored.getModuleId() : generated.getModuleId());
        resp.setLabel(stored.getLabel() != null ? stored.getLabel() : generated.getLabel());
        resp.setDataIndex(
                stored.getDataIndex() != null ? stored.getDataIndex() : generated.getDataIndex());
        resp.setSorted(stored.getSorted() != null ? stored.getSorted() : generated.getSorted());
        resp.setEllipsis(
                stored.getEllipsis() != null ? stored.getEllipsis() : generated.getEllipsis());
        resp.setChecked(stored.getChecked() != null ? stored.getChecked() : generated.getChecked());
        resp.setSortValue(
                stored.getSortValue() != null ? stored.getSortValue() : generated.getSortValue());
        // 表头展示相关配置原样透传
        resp.setColors(parseColors(stored.getColors()));
        resp.setWidth(stored.getWidth());
        resp.setSearchType(stored.getSearchType());
        resp.setFixed(stored.getFixed());

        // parent_id 直接存父节点虚拟id，顶层统一为 0；表中父节点已不在当前菜单结构内时沿用生成侧父子关系
        String parentId = stored.getParentId();
        if (parentId == null
                || (!ROOT_PARENT_ID.equals(parentId) && !generatedVirIds.contains(parentId))) {
            parentId = generated.getParentId();
        }
        resp.setParentId(parentId);
        return resp;
    }

    // ==================== 树形组装 ====================

    /** 由节点列表组装表头树（parentId 指向父节点虚拟id），根节点固定为菜单自身对应的节点 */
    private SysMenuHeaderResp assembleTree(Long menuId, List<SysMenuHeaderResp> nodes) {
        Map<String, List<SysMenuHeaderResp>> childrenByParent = new LinkedHashMap<>();
        List<SysMenuHeaderResp> topNodes = new ArrayList<>();
        for (SysMenuHeaderResp node : nodes) {
            String parentId = node.getParentId();
            if (parentId == null || ROOT_PARENT_ID.equals(parentId)) {
                topNodes.add(node);
            } else {
                childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(node);
            }
        }
        if (topNodes.isEmpty()) {
            throw new BusinessException("菜单表头节点为空");
        }

        // 根节点取菜单自身对应的节点（生成侧必存在），其余顶层节点一并挂到根节点下
        String rootVirId = menuVirId(menuId);
        SysMenuHeaderResp root = null;
        for (SysMenuHeaderResp node : topNodes) {
            if (rootVirId.equals(node.getVirId())) {
                root = node;
                break;
            }
        }
        if (root == null) {
            root = topNodes.getFirst();
        }
        List<SysMenuHeaderResp> extraTopNodes = new ArrayList<>();
        for (SysMenuHeaderResp node : topNodes) {
            if (node != root) {
                extraTopNodes.add(node);
            }
        }
        fillChildren(root, childrenByParent, extraTopNodes);
        return root;
    }

    /** 递归挂载子节点：同级按排序值升序，排序值相同时保持生成/查询顺序 */
    private void fillChildren(
            SysMenuHeaderResp node,
            Map<String, List<SysMenuHeaderResp>> childrenByParent,
            List<SysMenuHeaderResp> extraChildren) {
        List<SysMenuHeaderResp> children = new ArrayList<>();
        if (extraChildren != null) {
            children.addAll(extraChildren);
        }
        children.addAll(childrenByParent.getOrDefault(node.getVirId(), Collections.emptyList()));
        if (children.isEmpty()) {
            // 菜单节点始终输出 children，字段节点无子节点时不输出
            node.setChildren(
                    Objects.equals(node.getNodeType(), NODE_TYPE_MENU) ? new ArrayList<>() : null);
            return;
        }
        children.sort(
                Comparator.comparing(
                        SysMenuHeaderResp::getSortValue, Comparator.nullsLast(Integer::compareTo)));
        for (SysMenuHeaderResp child : children) {
            fillChildren(child, childrenByParent, null);
        }
        node.setChildren(children);
    }

    // ==================== 通用查询工具 ====================

    private void collectChildren(Map<Long, List<SysMenu>> childrenByPid, Long parentId) {
        List<SysMenu> children = selectChildrenByPid(parentId);
        if (children.isEmpty()) {
            return;
        }
        childrenByPid.put(parentId, children);
        for (SysMenu child : children) {
            collectChildren(childrenByPid, child.getId());
        }
    }

    private List<SysMenu> selectChildrenByPid(Long pid) {
        Long subjectId = AppContext.getSubjectId();
        String projectNo = AppContext.getProjectNo();
        return sysMenuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getPid, pid)
                        .eq(subjectId != null, SysMenu::getSubjectId, subjectId)
                        .eq(projectNo != null, SysMenu::getProjectNo, projectNo)
                        .orderByAsc(SysMenu::getSortOrder)
                        .orderByAsc(SysMenu::getId));
    }

    private SysMenu selectMenuById(Long menuId) {
        Long subjectId = AppContext.getSubjectId();
        String projectNo = AppContext.getProjectNo();
        return sysMenuMapper.selectOne(
                new LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getId, menuId)
                        .eq(subjectId != null, SysMenu::getSubjectId, subjectId)
                        .eq(projectNo != null, SysMenu::getProjectNo, projectNo)
                        .last("LIMIT 1"));
    }

    private Map<Long, List<SysModuleField>> selectFieldsGroupByModule(Set<Long> moduleIds) {
        if (moduleIds == null || moduleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysModuleField> fields =
                sysModuleFieldMapper.selectList(
                        Wrappers.<SysModuleField>lambdaQuery()
                                .in(SysModuleField::getModuleId, moduleIds)
                                .orderByAsc(SysModuleField::getSortOrder)
                                .orderByAsc(SysModuleField::getId));
        return fields.stream()
                .collect(
                        Collectors.groupingBy(
                                SysModuleField::getModuleId,
                                LinkedHashMap::new,
                                Collectors.toList()));
    }

    // ==================== 小工具 ====================

    /** 菜单节点虚拟id：MENU_{菜单ID} */
    private String menuVirId(Long menuId) {
        return VIR_ID_PREFIX_MENU + "_" + menuId;
    }

    /** 字段节点虚拟id：Field_{字段ID} */
    private String fieldVirId(Long fieldId) {
        return VIR_ID_PREFIX_FIELD + "_" + fieldId;
    }

    /** 字段索引：模块id.表名.字段名，如 1001.info.company_name */
    private String buildDataIndex(SysModuleField field) {
        return field.getModuleId()
                + DATA_INDEX_SEPARATOR
                + field.getTableName()
                + DATA_INDEX_SEPARATOR
                + field.getColumnName();
    }

    private boolean isMenuNode(SysMenuHeader node) {
        return Objects.equals(node.getNodeType(), NODE_TYPE_MENU);
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
