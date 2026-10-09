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
import com.jdec.platform.config.biz.entity.*;
import com.jdec.platform.config.biz.mapper.*;
import com.jdec.platform.config.open.api.OpenMenuHeaderApi;
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
import org.springframework.util.StringUtils;

/** 列表表头节点 Service 实现 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysMenuHeaderService implements SysMenuHeaderApi, OpenMenuHeaderApi {

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

    /** 关联字段标记：是（sys_field.relation_business_no 有值） */
    private static final Integer RELATION_WORD_YES = 1;

    /** 关联字段标记：否 */
    private static final Integer RELATION_WORD_NO = 0;

    /** sys_field 未删除标记 */
    private static final Integer FIELD_DELETED_NO = 0;

    /** 变更标记：菜单关联的详情菜单已切换（表头配置尚未更新入库），0 表示未变化 */
    private static final Integer CHANGE_DETAIL_SWITCHED = 1;

    /** 字段可查阅标记：sys_role_module_field_permission.view = 1 */
    private static final Integer VIEW_YES = 1;

    /** 菜单类型：页面（仅该类型且关联详情菜单时，表头归属其详情菜单） */
    private static final Integer MENU_TYPE_PAGE = 2;

    /** 角色权限过滤开关：角色菜单/字段权限数据尚未配置完成，暂时关闭（按最大权限返回），配置就绪后置为 true 即可恢复 */
    private static final boolean ROLE_PERMISSION_FILTER_ENABLED = true;

    private final SysMenuHeaderMapper sysMenuHeaderMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysModuleFieldMapper sysModuleFieldMapper;
    private final SysFieldMapper sysFieldMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysRoleModuleFieldPermissionMapper sysRoleModuleFieldPermissionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public SysMenuHeaderResp getMenuHeaderTree(Long menuId, Integer change) {
        return getMenuHeaderTree(menuId, change, null);
    }

    @Override
    public SysMenuHeaderResp getMenuHeaderTree(Long menuId, Integer change, Long roleId) {
        if (menuId == null) {
            throw new BusinessException("菜单ID不能为空");
        }

        // 角色权限过滤暂时关闭：一律按最大权限返回，角色权限数据配置就绪后打开开关即可
        Long effectiveRoleId = ROLE_PERMISSION_FILTER_ENABLED ? roleId : null;

        // 入参为列表菜单ID，表头记录归属其绑定的详情菜单
        Long headerMenuId = resolveHeaderMenuId(menuId);

        // 生成侧：以表头归属菜单为根，递归全部子级菜单，取各菜单绑定模块下的字段（effectiveRoleId 非空时按角色过滤）
        List<SysMenuHeaderResp> generatedNodes = buildGeneratedNodes(headerMenuId, effectiveRoleId);

        // 根菜单无权限（或整树裁剪后为空）：整树丢弃，返回 null
        if (generatedNodes.isEmpty()) {
            return null;
        }

        // 菜单关联的详情菜单已切换（表头配置尚未更新入库）：只用生成侧，不读表
        if (CHANGE_DETAIL_SWITCHED.equals(change)) {
            return assembleTree(headerMenuId, generatedNodes);
        }

        // 详情菜单未变化：与 sys_menu_header 已保存配置按虚拟id 合并
        Set<String> generatedVirIds =
                generatedNodes.stream()
                        .map(SysMenuHeaderResp::getVirId)
                        .collect(Collectors.toSet());
        Map<String, SysMenuHeader> storedByVirId = new LinkedHashMap<>();
        for (SysMenuHeader stored : selectStoredNodes(headerMenuId)) {
            storedByVirId.putIfAbsent(stored.getVirId(), stored);
        }
        List<SysMenuHeaderResp> mergedNodes = new ArrayList<>();
        for (SysMenuHeaderResp node : generatedNodes) {
            SysMenuHeader stored = storedByVirId.get(node.getVirId());
            // 两边都有同一虚拟id → 取表中记录（保留用户配置）；仅生成侧有 → 用生成侧默认值
            mergedNodes.add(stored != null ? mergeWithStored(node, stored, generatedVirIds) : node);
        }

        // 仅表中有的节点不再返回（丢弃）；递归子菜单生成的节点直接返回，不落库、不带主键 id
        return assembleTree(headerMenuId, mergedNodes);
    }

    /**
     * 菜单ID → 表头归属菜单ID
     *
     * <p>仅当菜单类型为「页面」且关联了详情菜单时，表头记录归属其绑定的详情菜单；「表」等其它类型（含未关联详情菜单的页面）直接以该菜单ID 为归属，不再转换。
     */
    @Override
    public Long resolveHeaderMenuId(Long menuId) {
        SysMenu menu = selectMenuById(menuId);
        if (menu == null) {
            throw new BusinessException("菜单不存在");
        }
        return resolveHeaderMenuId(menu);
    }

    /** 菜单实体 → 表头归属菜单ID（规则同 {@link #resolveHeaderMenuId(Long)}，供调用方复用已加载/待保存的菜单数据，避免重复查库） */
    public Long resolveHeaderMenuId(SysMenu menu) {
        if (MENU_TYPE_PAGE.equals(menu.getMenuType()) && menu.getDetailId() != null) {
            return menu.getDetailId();
        }
        return menu.getId();
    }

    /**
     * 随菜单一并保存多级表头配置（由 {@code SysMenuService#saveMenu} 内部调用）
     *
     * <p>表头记录按「归属菜单ID」存放，归属菜单由 {@link #resolveHeaderMenuId(SysMenu)} 统一解析：菜单类型为「页面」且关联了详情菜单时
     * 取该详情菜单ID，其余（「表」等）取菜单自身ID。
     *
     * <ul>
     *   <li>{@code change=1}（归属菜单已切换）：先删除变更前归属菜单（{@code previousMenuId}）在 {@code sys_menu_header}
     *       中的全部记录（一条 SQL），再按本次提交的节点树以 {@code menuId} 为归属重新落库。
     *   <li>{@code change=0}（归属菜单未变化）：将 {@code header} 树压平后与 {@code menuId} 已保存节点按 {@code virId}
     *       比对，已存在的节点按主键批量更新，未存在的节点批量新增。
     * </ul>
     *
     * <p>两种情形都以本次提交的整棵树为准：{@code menuId} 下已保存、但 {@code header} 中未提交的节点一并删除（一条 SQL）。
     *
     * <p>{@code header} 为空时只做上述清理、不做落库。数据隔离按 {@code subject_id} + {@code project_no}，所有写操作均为批量执行。
     *
     * @param menuId 本次表头记录的归属菜单ID（由 {@code SysMenuService} 按归属规则解析后传入）
     * @param previousMenuId 变更前表头记录的归属菜单ID，无变更或新增菜单时为 {@code null}
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
        List<SysMenuHeader> storedNodes = selectStoredNodes(menuId);
        Map<String, Long> storedIdByVirId = new HashMap<>();
        for (SysMenuHeader storedNode : storedNodes) {
            storedIdByVirId.putIfAbsent(storedNode.getVirId(), storedNode.getId());
        }

        // 已保存的节点按主键批量更新，未保存的节点批量新增；命中的已存记录主键收集起来用于反推需删除的记录
        List<SysMenuHeader> insertList = new ArrayList<>();
        List<SysMenuHeader> updateList = new ArrayList<>();
        Set<Long> keptIds = new HashSet<>();
        for (SaveMenuReq.MenuHeaderNode node : nodes) {
            if (node.getVirId() == null || node.getVirId().trim().isEmpty()) {
                throw new BusinessException("表头节点虚拟id不能为空");
            }
            SysMenuHeader entity = toHeaderEntity(menuId, node);
            Long storedId = storedIdByVirId.get(node.getVirId());
            if (storedId != null) {
                entity.setId(storedId);
                updateList.add(entity);
                keptIds.add(storedId);
            } else {
                entity.setSubjectId(subjectId);
                entity.setProjectNo(projectNo);
                insertList.add(entity);
            }
        }

        // 整棵树以本次提交为准：库中已保存、但本次未提交的节点一并删除（一条 SQL）
        List<Long> removedIds =
                storedNodes.stream()
                        .map(SysMenuHeader::getId)
                        .filter(id -> id != null && !keptIds.contains(id))
                        .collect(Collectors.toList());
        if (!removedIds.isEmpty()) {
            sysMenuHeaderMapper.delete(
                    Wrappers.<SysMenuHeader>lambdaQuery()
                            .eq(SysMenuHeader::getMenuId, menuId)
                            .eq(SysMenuHeader::getSubjectId, subjectId)
                            .eq(SysMenuHeader::getProjectNo, projectNo)
                            .in(SysMenuHeader::getId, removedIds));
        }

        if (!insertList.isEmpty()) {
            sysMenuHeaderMapper.insert(insertList);
        }
        if (!updateList.isEmpty()) {
            sysMenuHeaderMapper.updateById(updateList);
        }
        log.debug(
                "保存菜单表头配置：menuId={}, 新增={}, 更新={}, 删除={}",
                menuId,
                insertList.size(),
                updateList.size(),
                removedIds.size());
    }

    @Override
    public MenuHeaderDisplayResp getMenuHeaderDisplay(Long menuId) {
        return getMenuHeaderDisplay(menuId, null);
    }

    @Override
    public MenuHeaderDisplayResp getMenuHeaderDisplay(Long menuId, Long roleId) {
        if (menuId == null) {
            throw new BusinessException("菜单ID不能为空");
        }

        // 表头记录按归属菜单存放，先按统一规则解析出归属菜单ID
        Long headerMenuId = resolveHeaderMenuId(menuId);

        // 角色权限过滤开关统一在此收敛（与 getMenuHeaderTree 一致）
        Long effectiveRoleId = ROLE_PERMISSION_FILTER_ENABLED ? roleId : null;

        // 生成侧最新数据（菜单及其子菜单 + 各菜单绑定模块下的字段）构成可用节点池，
        // 每次返回前都据此比对：sys_menu_header 中模块侧已不存在的节点不返回给前端；
        // 展示名与关联字段标记一律取生成侧（源头），不取库中存储值
        // effectiveRoleId 非空时节点池按角色权限过滤：无权限的菜单及其子级、非 view=1 的字段均不在池内
        Map<String, SysMenuHeaderResp> generatedByVirId =
                indexByVirId(buildGeneratedNodes(headerMenuId, effectiveRoleId));

        // 纯读取该菜单已保存的表头节点（数据隔离：subject_id + project_no），仅保留节点池中仍存在的节点
        List<SysMenuHeader> stored =
                sysMenuHeaderMapper
                        .selectList(
                                Wrappers.<SysMenuHeader>lambdaQuery()
                                        .eq(SysMenuHeader::getMenuId, headerMenuId)
                                        .eq(SysMenuHeader::getSubjectId, AppContext.getSubjectId())
                                        .eq(SysMenuHeader::getProjectNo, AppContext.getProjectNo())
                                        .orderByAsc(
                                                SysMenuHeader::getSortValue, SysMenuHeader::getId))
                        .stream()
                        .filter(node -> generatedByVirId.containsKey(node.getVirId()))
                        .collect(Collectors.toList());
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

        SysMenuHeader root = selectDisplayRoot(headerMenuId, topNodes);
        if (root == null) {
            return new MenuHeaderDisplayResp();
        }
        return MenuHeaderDisplayResp.builder()
                .header(toColumnResp(root, childrenByParent, generatedByVirId))
                .build();
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
        // search_type 在库中为 JSON 列，空串不是合法 JSON，统一归一为 null
        entity.setSearchType(
                StringUtils.hasText(node.getSearchType()) ? node.getSearchType() : null);
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

    /**
     * 递归组装多级列头展示节点：菜单节点输出 sourceId/colors，字段节点输出字段展示配置
     *
     * <p>展示名与关联字段标记取生成侧（源头：菜单标题 / 模块字段显示名、sys_field 关联配置），不取库中存储值。
     */
    private MenuHeaderColumnResp toColumnResp(
            SysMenuHeader node,
            Map<String, List<SysMenuHeader>> childrenByParent,
            Map<String, SysMenuHeaderResp> generatedByVirId) {
        SysMenuHeaderResp generated = generatedByVirId.get(node.getVirId());
        MenuHeaderColumnResp resp = new MenuHeaderColumnResp();
        resp.setLabel(generated != null ? generated.getLabel() : node.getLabel());
        resp.setModuleId(node.getModuleId());
        if (isMenuNode(node)) {
            resp.setSourceId(node.getSourceId());
            resp.setColors(parseColors(node.getColors()));
        } else {
            resp.setFieldId(node.getSourceId());
            resp.setDataIndex(stripModulePrefix(node.getDataIndex()));
            resp.setRelationWord(generated != null ? generated.getRelationWord() : null);
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
                            .map(child -> toColumnResp(child, childrenByParent, generatedByVirId))
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

    /** 生成侧节点按虚拟id 建索引（可用节点池），供展示/合并时过滤模块侧已不存在的历史节点并取源头展示字段 */
    private Map<String, SysMenuHeaderResp> indexByVirId(List<SysMenuHeaderResp> nodes) {
        return nodes.stream()
                .collect(
                        Collectors.toMap(
                                SysMenuHeaderResp::getVirId,
                                node -> node,
                                (first, second) -> first,
                                LinkedHashMap::new));
    }

    /**
     * 生成侧节点列表（深度优先、父节点在前）
     *
     * <p>以该菜单为根递归其全部子级菜单，取每个菜单绑定模块（sys_module_field）下的字段：菜单节点虚拟id 为 {@code MENU_{菜单id}}，字段节点为
     * {@code Field_{字段id}}；所有节点的 menu_id 统一取最顶层菜单ID， 顶层节点 parent_id 为 {@code 0}，同级节点从 {@code 1}
     * 起维护排序值。
     *
     * <p>{@code roleId} 非空时按角色权限过滤：
     *
     * <ul>
     *   <li>根菜单自身无权限时整树丢弃，返回空列表；
     *   <li>无权限的子级菜单不参与遍历（其子级一并丢弃，不因下级有权限而保留）；
     *   <li>非 {@code view=1} 的字段不生成节点；
     *   <li>菜单节点若既无字段节点也无子级菜单节点，则连同该节点一起丢弃（有权限但没子级也不保留）。
     * </ul>
     */
    private List<SysMenuHeaderResp> buildGeneratedNodes(Long menuId, Long roleId) {
        SysMenu menu = selectMenuById(menuId);
        if (menu == null) {
            throw new BusinessException("菜单不存在");
        }

        // 角色有权限的菜单ID集合（roleId 为空则不过滤）
        Set<Long> permittedMenuIds = selectPermittedMenuIds(roleId);

        // 根菜单自身无权限：整树丢弃，无权限的菜单连同其全部子级都不返回
        if (permittedMenuIds != null && !permittedMenuIds.contains(menuId)) {
            return Collections.emptyList();
        }

        // 收集菜单及其全部子级菜单（树形）
        Map<Long, List<SysMenu>> childrenByPid = new LinkedHashMap<>();
        collectChildren(childrenByPid, menuId, permittedMenuIds);

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
        Map<Long, List<SysModuleField>> fieldsByModule =
                selectFieldsGroupByModule(moduleIds, roleId);

        // 生成侧节点已带齐展示所需的源头数据：菜单标题、模块字段显示名、是否关联字段（sys_field）
        Set<String> relationFieldIndexes = selectRelationFieldIndexes(fieldsByModule);

        // 先按树形生成，再压平为节点列表；
        // 角色过滤时同步裁剪「既无字段节点也无子级菜单」的空菜单节点（有权限但没子级 → 不返回）
        boolean pruneEmptyMenus = permittedMenuIds != null;
        SysMenuHeaderResp root =
                toGeneratedNode(
                        menu,
                        menuId,
                        ROOT_PARENT_ID,
                        childrenByPid,
                        fieldsByModule,
                        relationFieldIndexes,
                        SORT_VALUE_FIRST,
                        pruneEmptyMenus);
        if (root == null) {
            return Collections.emptyList();
        }
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
     * @param relationFieldIndexes 关联字段索引集合（sys_field 中配置了 relation_business_no 的 表名.字段名）
     * @param sortValue 当前节点在同级中的排序值
     * @param pruneEmptyMenus 是否丢弃「既无字段也无子级菜单」的空菜单节点（角色过滤时为 {@code true}）
     * @return 生成的菜单节点；{@code pruneEmptyMenus=true} 且该菜单无任何子节点时返回 {@code null}
     */
    private SysMenuHeaderResp toGeneratedNode(
            SysMenu menu,
            Long rootMenuId,
            String parentVirId,
            Map<Long, List<SysMenu>> childrenByPid,
            Map<Long, List<SysModuleField>> fieldsByModule,
            Set<String> relationFieldIndexes,
            Integer sortValue,
            boolean pruneEmptyMenus) {
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
            String dataIndex = buildDataIndex(field);
            SysMenuHeaderResp fieldResp = new SysMenuHeaderResp();
            fieldResp.setMenuId(rootMenuId);
            fieldResp.setNodeType(NODE_TYPE_FIELD);
            fieldResp.setVirId(fieldVirId(field.getId()));
            fieldResp.setParentId(currentVirId);
            fieldResp.setSourceId(field.getId());
            fieldResp.setModuleId(field.getModuleId());
            fieldResp.setDataIndex(dataIndex);
            fieldResp.setLabel(firstNotBlank(field.getDisplayName(), field.getColumnName()));
            fieldResp.setRelationWord(
                    relationFieldIndexes.contains(dataIndex)
                            ? RELATION_WORD_YES
                            : RELATION_WORD_NO);
            fieldResp.setSorted(SORTED_YES);
            fieldResp.setEllipsis(ELLIPSIS_NO);
            fieldResp.setChecked(CHECKED_NO);
            fieldResp.setSortValue(childSortValue++);
            children.add(fieldResp);
        }

        // 递归挂载子级菜单节点
        List<SysMenu> subMenus = childrenByPid.getOrDefault(menu.getId(), Collections.emptyList());
        for (SysMenu subMenu : subMenus) {
            SysMenuHeaderResp subNode =
                    toGeneratedNode(
                            subMenu,
                            rootMenuId,
                            currentVirId,
                            childrenByPid,
                            fieldsByModule,
                            relationFieldIndexes,
                            childSortValue++,
                            pruneEmptyMenus);
            if (subNode != null) {
                children.add(subNode);
            }
        }

        // 该菜单既无字段节点也无子级菜单节点：角色过滤时整节点丢弃（有权限但没子级也不需要留下）
        if (pruneEmptyMenus && children.isEmpty()) {
            return null;
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
        // 展示名与关联字段标记一律取生成侧（源头），不取库中存储值
        resp.setLabel(generated.getLabel());
        resp.setRelationWord(generated.getRelationWord());
        resp.setDataIndex(
                stripModulePrefix(
                        stored.getDataIndex() != null
                                ? stored.getDataIndex()
                                : generated.getDataIndex()));
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

    private void collectChildren(
            Map<Long, List<SysMenu>> childrenByPid, Long parentId, Set<Long> permittedMenuIds) {
        List<SysMenu> children = selectChildrenByPid(parentId);
        // 按角色权限过滤无权限的子级菜单（permittedMenuIds 为空表示不过滤）
        if (permittedMenuIds != null) {
            children =
                    children.stream()
                            .filter(child -> permittedMenuIds.contains(child.getId()))
                            .collect(Collectors.toList());
        }
        if (children.isEmpty()) {
            return;
        }
        childrenByPid.put(parentId, children);
        for (SysMenu child : children) {
            collectChildren(childrenByPid, child.getId(), permittedMenuIds);
        }
    }

    /** 角色有权限的菜单ID集合；roleId 为空返回 null 表示不做菜单权限过滤 */
    @Override
    public Set<Long> selectPermittedMenuIds(Long roleId) {
        if (roleId == null) {
            return null;
        }
        return sysRoleMenuMapper
                .selectList(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, roleId))
                .stream()
                .map(SysRoleMenu::getMenuId)
                .collect(Collectors.toSet());
    }

    /** 角色可查阅（view=1）的字段ID集合；roleId 为空返回 null 表示不做字段权限过滤 */
    @Override
    public Set<Long> selectReadableFieldIds(Long roleId, Collection<Long> fieldIds) {
        if (roleId == null) {
            return null;
        }
        if (fieldIds.isEmpty()) {
            return Collections.emptySet();
        }
        return sysRoleModuleFieldPermissionMapper
                .selectList(
                        Wrappers.<SysRoleModuleFieldPermission>lambdaQuery()
                                .eq(SysRoleModuleFieldPermission::getRoleId, roleId)
                                .in(SysRoleModuleFieldPermission::getModuleFieldId, fieldIds)
                                .eq(SysRoleModuleFieldPermission::getView, VIEW_YES))
                .stream()
                .map(SysRoleModuleFieldPermission::getModuleFieldId)
                .collect(Collectors.toSet());
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

    /** 按模块查询字段并按模块分组；roleId 非空时仅保留角色 view=1 的字段 */
    private Map<Long, List<SysModuleField>> selectFieldsGroupByModule(
            Set<Long> moduleIds, Long roleId) {
        if (moduleIds == null || moduleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysModuleField> fields =
                sysModuleFieldMapper.selectList(
                        Wrappers.<SysModuleField>lambdaQuery()
                                .in(SysModuleField::getModuleId, moduleIds)
                                .orderByAsc(SysModuleField::getSortOrder)
                                .orderByAsc(SysModuleField::getId));
        // 按角色字段权限过滤（无记录即无权限，直接丢弃）
        Set<Long> readableFieldIds =
                selectReadableFieldIds(
                        roleId,
                        fields.stream().map(SysModuleField::getId).collect(Collectors.toList()));
        if (readableFieldIds != null) {
            fields =
                    fields.stream()
                            .filter(field -> readableFieldIds.contains(field.getId()))
                            .collect(Collectors.toList());
        }
        return fields.stream()
                .collect(
                        Collectors.groupingBy(
                                SysModuleField::getModuleId,
                                LinkedHashMap::new,
                                Collectors.toList()));
    }

    // ==================== 小工具 ====================

    /**
     * 查询「已配置关联业务编号」的字段索引集合（{@code 表名.字段名}）
     *
     * <p>用模块字段的物理表名/列名到 {@code sys_field} 匹配（数据隔离：{@code subject_id + project_no}，仅未删除）， {@code
     * relation_business_no} 有值即为关联字段。
     */
    private Set<String> selectRelationFieldIndexes(Map<Long, List<SysModuleField>> fieldsByModule) {
        Set<String> tableNames = new HashSet<>();
        for (List<SysModuleField> fields : fieldsByModule.values()) {
            for (SysModuleField field : fields) {
                if (field.getTableName() != null) {
                    tableNames.add(field.getTableName());
                }
            }
        }
        if (tableNames.isEmpty()) {
            return Collections.emptySet();
        }
        Long subjectId = AppContext.getSubjectId();
        String projectNo = AppContext.getProjectNo();
        List<SysField> fields =
                sysFieldMapper.selectList(
                        Wrappers.<SysField>lambdaQuery()
                                .in(SysField::getTableName, tableNames)
                                .eq(SysField::getDeleted, FIELD_DELETED_NO)
                                .eq(subjectId != null, SysField::getSubjectId, subjectId)
                                .eq(projectNo != null, SysField::getProjectNo, projectNo));
        Set<String> indexes = new HashSet<>();
        for (SysField field : fields) {
            if (StringUtils.hasText(field.getRelationBusinessNo())
                    && field.getTableName() != null
                    && field.getColumnName() != null) {
                indexes.add(field.getTableName() + DATA_INDEX_SEPARATOR + field.getColumnName());
            }
        }
        return indexes;
    }

    /** 菜单节点虚拟id：MENU_{菜单ID} */
    private String menuVirId(Long menuId) {
        return VIR_ID_PREFIX_MENU + "_" + menuId;
    }

    /** 字段节点虚拟id：Field_{字段ID} */
    private String fieldVirId(Long fieldId) {
        return VIR_ID_PREFIX_FIELD + "_" + fieldId;
    }

    /** 字段索引：表名.字段名，如 info.company_name（不带模块ID） */
    private String buildDataIndex(SysModuleField field) {
        return field.getTableName() + DATA_INDEX_SEPARATOR + field.getColumnName();
    }

    /** 字段索引去掉前缀模块ID：1006.company_contact.id → company_contact.id（无数字前缀时原样返回） */
    private static String stripModulePrefix(String dataIndex) {
        if (dataIndex == null) {
            return null;
        }
        int dot = dataIndex.indexOf(DATA_INDEX_SEPARATOR);
        if (dot > 0 && dataIndex.substring(0, dot).chars().allMatch(Character::isDigit)) {
            return dataIndex.substring(dot + 1);
        }
        return dataIndex;
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
