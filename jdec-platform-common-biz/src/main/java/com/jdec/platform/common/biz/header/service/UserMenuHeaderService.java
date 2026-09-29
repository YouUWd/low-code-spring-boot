package com.jdec.platform.common.biz.header.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.common.api.UserMenuHeaderApi;
import com.jdec.platform.common.api.dto.request.SaveUserMenuHeaderReq;
import com.jdec.platform.common.biz.header.entity.UserMenuHeader;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
import com.jdec.platform.config.api.dto.response.MenuHeaderSearchTypeResp;
import com.jdec.platform.config.api.dto.response.SysMenuHeaderResp;
import com.jdec.platform.config.open.api.OpenMenuHeaderApi;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import com.jdec.platform.shared.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 用户自定义表头配置 Service */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserMenuHeaderService implements UserMenuHeaderApi {

    /** 表头变更标记：菜单关联的详情菜单未变化 */
    private static final Integer CHANGE_NONE = 0;

    /** 用户个性化表头配置表名（业务库） */
    private static final String TABLE_USER_MENU_HEADER = "user_menu_header";

    /** 节点类型：菜单 */
    private static final Integer NODE_TYPE_MENU = 0;

    /** 顶层节点的父节点虚拟id */
    private static final String ROOT_PARENT_ID = "0";

    /** 已勾选 */
    private static final Integer CHECKED_YES = 1;

    /** 表头节点列（别名与行模型属性同名，保证记录精准映射到 {@link UserMenuHeader}） */
    private static final List<Field<?>> USER_NODE_COLUMNS =
            List.of(
                    DSL.field(DSL.name("id")).as("id"),
                    DSL.field(DSL.name("user_id")).as("userId"),
                    DSL.field(DSL.name("vir_id")).as("virId"),
                    DSL.field(DSL.name("menu_id")).as("menuId"),
                    DSL.field(DSL.name("parent_id")).as("parentId"),
                    DSL.field(DSL.name("node_type")).as("nodeType"),
                    DSL.field(DSL.name("source_id")).as("sourceId"),
                    DSL.field(DSL.name("module_id")).as("moduleId"),
                    DSL.field(DSL.name("label")).as("label"),
                    DSL.field(DSL.name("colors")).as("colors"),
                    DSL.field(DSL.name("width")).as("width"),
                    DSL.field(DSL.name("data_index")).as("dataIndex"),
                    DSL.field(DSL.name("sorted")).as("sorted"),
                    DSL.field(DSL.name("search_type")).as("searchType"),
                    DSL.field(DSL.name("ellipsis")).as("ellipsis"),
                    DSL.field(DSL.name("fixed")).as("fixed"),
                    DSL.field(DSL.name("checked")).as("checked"),
                    DSL.field(DSL.name("sort_value")).as("sortValue"));

    private final JooqDataContextFactory jooqDataContextFactory;
    private final OpenMenuHeaderApi openMenuHeaderApi;
    private final ObjectMapper objectMapper;

    @Override
    public SysMenuHeaderResp getUserMenuHeaderTree(Long userId, Long menuId, Long roleId) {
        if (userId == null || menuId == null) {
            throw new BusinessException("用户ID与菜单ID不能为空");
        }

        // 表头记录（sys_menu_header / user_menu_header）均按详情菜单ID 归属，将前端传入的列表菜单ID 转换后再查
        Long headerMenuId = openMenuHeaderApi.resolveHeaderMenuId(menuId);

        // 默认表头树：菜单/字段生成 + sys_menu_header 已保存配置 + 角色权限过滤（config 侧实现）
        SysMenuHeaderResp tree = openMenuHeaderApi.getMenuHeaderTree(menuId, CHANGE_NONE, roleId);

        // 用户个性化配置：该用户在此菜单下没有配置时直接返回默认表头树
        Map<String, UserMenuHeader> userNodesByVirId =
                toMapByVirId(
                        selectUserNodes(jooqDataContextFactory.getContext(), userId, headerMenuId));
        if (userNodesByVirId.isEmpty()) {
            return tree;
        }

        // 以默认树节点集合为骨架合并：用户有则覆盖展示配置，用户没有则保留默认值
        return mergeUserNodes(tree, userNodesByVirId);
    }

    @Override
    public MenuHeaderDisplayResp getUserMenuHeaderDisplay(Long userId, Long menuId, Long roleId) {
        if (userId == null || menuId == null) {
            throw new BusinessException("用户ID与菜单ID不能为空");
        }

        // 表头记录按详情菜单ID 归属，将前端传入的列表菜单ID 转换后再查
        Long headerMenuId = openMenuHeaderApi.resolveHeaderMenuId(menuId);

        // 模块侧最新可用节点（菜单及其子菜单 + 各菜单绑定模块下的字段）构成可用节点池，
        // 每次返回前都据此比对：user_menu_header 中模块侧已不存在的节点不返回给前端
        Set<String> poolVirIds =
                collectVirIds(openMenuHeaderApi.getMenuHeaderTree(menuId, CHANGE_NONE, roleId));

        // 读取该用户在该菜单下已保存的个性化表头节点（数据隔离：user_id + menu_id + subject_id + project_no），
        // 仅保留节点池中仍存在的节点
        List<UserMenuHeader> stored =
                selectUserNodes(jooqDataContextFactory.getContext(), userId, headerMenuId).stream()
                        .filter(node -> poolVirIds.contains(node.getVirId()))
                        .collect(Collectors.toList());

        // 该用户在该菜单下没有任何有效个性化配置（无记录或记录指向的菜单/字段均已不存在）：回退取 config 侧默认表头配置
        if (stored.isEmpty()) {
            return openMenuHeaderApi.getMenuHeaderDisplay(menuId);
        }

        // 角色权限过滤（角色权限数据尚未配置完成，先注释；权限就绪后去掉注释即可按角色过滤菜单与字段）
        // stored = filterByRolePermission(stored, headerMenuId, roleId);

        // 展示只输出已勾选的列，并保留其祖先菜单节点用于组装多级列头
        List<UserMenuHeader> nodes = retainCheckedWithAncestors(stored);
        if (nodes.isEmpty()) {
            return new MenuHeaderDisplayResp();
        }

        // 按父节点虚拟id分组（保留查询顺序，parent_id 为 null/0 的是顶层节点）
        Map<String, List<UserMenuHeader>> childrenByParent = new LinkedHashMap<>();
        List<UserMenuHeader> topNodes = new ArrayList<>();
        for (UserMenuHeader node : nodes) {
            String parentId = node.getParentId();
            if (parentId == null || ROOT_PARENT_ID.equals(parentId)) {
                topNodes.add(node);
            } else {
                childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(node);
            }
        }

        UserMenuHeader root = selectDisplayRoot(headerMenuId, topNodes);
        if (root == null) {
            return new MenuHeaderDisplayResp();
        }
        return MenuHeaderDisplayResp.builder().header(toColumnResp(root, childrenByParent)).build();
    }

    @Override
    public void saveUserMenuHeader(SaveUserMenuHeaderReq req) {
        if (req == null || req.getUserId() == null) {
            throw new BusinessException("用户ID不能为空");
        }
        if (req.getMenuId() == null) {
            throw new BusinessException("菜单ID不能为空");
        }

        // 用户表头记录按详情菜单ID 归属，将前端传入的列表菜单ID 转换后再落库
        Long headerMenuId = openMenuHeaderApi.resolveHeaderMenuId(req.getMenuId());

        // 提交的树压平为节点列表（深度优先、父节点在前），先整体校验再落库，避免开着事务才发现参数非法
        List<SaveUserMenuHeaderReq> nodes = new ArrayList<>();
        flatten(req, nodes);
        for (SaveUserMenuHeaderReq node : nodes) {
            if (node.getVirId() == null || node.getVirId().trim().isEmpty()) {
                throw new BusinessException("表头节点虚拟id不能为空");
            }
            checkSearchTypeJson(node.getSearchType());
        }

        Long subjectId = AppContext.getSubjectId();
        String projectNo = AppContext.getProjectNo();
        Long currentUserId = UserContext.getUserId();
        LocalDateTime now = LocalDateTime.now();

        // 用户表头在业务库：Spring 事务管理器绑定在 @Primary 的 DynamicRoutingDataSource 上，
        // 与该 projectNo 解析出的物理库不是同一个 key，Spring 事务既无法参与也无法回滚（还会连到默认库），
        // 故改用 jOOQ 自带事务，使本次批量新增/更新共用同一连接、异常整体回滚
        AtomicInteger inserted = new AtomicInteger();
        AtomicInteger updated = new AtomicInteger();
        AtomicInteger removed = new AtomicInteger();
        // 本次提交命中的已保存记录主键，用于反推需删除的记录（整棵树以本次提交为准）
        Set<Long> keptIds = new HashSet<>();
        jooqDataContextFactory
                .getContext(projectNo)
                .transaction(
                        configuration -> {
                            DSLContext tx = DSL.using(configuration);

                            // 一次性查出该用户在该菜单下已保存的节点，按主键/虚拟id 建索引，避免循环内查库
                            List<UserMenuHeader> stored =
                                    selectUserNodes(tx, req.getUserId(), headerMenuId);
                            Map<Long, UserMenuHeader> storedById =
                                    stored.stream()
                                            .filter(node -> node.getId() != null)
                                            .collect(
                                                    Collectors.toMap(
                                                            UserMenuHeader::getId,
                                                            node -> node,
                                                            (first, second) -> first));
                            Map<String, UserMenuHeader> storedByVirId = toMapByVirId(stored);

                            // 已保存的节点按主键更新，未保存的节点新增
                            for (SaveUserMenuHeaderReq node : nodes) {
                                UserMenuHeader storedNode =
                                        node.getId() != null ? storedById.get(node.getId()) : null;
                                if (storedNode == null) {
                                    storedNode = storedByVirId.get(node.getVirId());
                                }
                                if (storedNode != null) {
                                    updateUserNode(
                                            tx,
                                            storedNode.getId(),
                                            node,
                                            subjectId,
                                            projectNo,
                                            currentUserId,
                                            now);
                                    keptIds.add(storedNode.getId());
                                    updated.incrementAndGet();
                                } else {
                                    insertUserNode(
                                            tx,
                                            node,
                                            headerMenuId,
                                            req.getUserId(),
                                            subjectId,
                                            projectNo,
                                            currentUserId,
                                            now);
                                    inserted.incrementAndGet();
                                }
                            }

                            // 整棵树以本次提交为准：库中已保存、但本次未提交的节点一并删除
                            List<Long> removedIds =
                                    stored.stream()
                                            .map(UserMenuHeader::getId)
                                            .filter(id -> id != null && !keptIds.contains(id))
                                            .collect(Collectors.toList());
                            if (!removedIds.isEmpty()) {
                                deleteUserNodes(
                                        tx,
                                        removedIds,
                                        req.getUserId(),
                                        headerMenuId,
                                        subjectId,
                                        projectNo);
                                removed.set(removedIds.size());
                            }
                        });
        log.debug(
                "保存用户个性化表头配置：userId={}, menuId={}, 新增={}, 更新={}, 删除={}",
                req.getUserId(),
                headerMenuId,
                inserted.get(),
                updated.get(),
                removed.get());
    }

    @Override
    public void resetUserMenuHeader(Long userId, Long menuId) {
        if (userId == null || menuId == null) {
            throw new BusinessException("用户ID与菜单ID不能为空");
        }

        // 用户表头记录按详情菜单ID 归属，与保存/查询保持一致
        Long headerMenuId = openMenuHeaderApi.resolveHeaderMenuId(menuId);

        // 单条删除语句本身即原子，无需事务（Spring 事务也覆盖不到该物理业务库）
        int deleted =
                jooqDataContextFactory
                        .getContext()
                        .deleteFrom(DSL.table(DSL.name(TABLE_USER_MENU_HEADER)))
                        .where(DSL.field(DSL.name("user_id")).eq(userId))
                        .and(DSL.field(DSL.name("menu_id")).eq(headerMenuId))
                        .and(DSL.field(DSL.name("subject_id")).eq(AppContext.getSubjectId()))
                        .and(DSL.field(DSL.name("project_no")).eq(AppContext.getProjectNo()))
                        .execute();
        log.debug("恢复默认用户个性化表头：userId={}, menuId={}, 删除={}", userId, headerMenuId, deleted);
    }

    // ==================== 用户表头读写 ====================

    /**
     * 查询用户在指定菜单（详情菜单ID）下的个性化表头节点
     *
     * <p>数据隔离：{@code user_id + menu_id + subject_id + project_no}，按 {@code sort_value}、{@code id}
     * 升序。
     */
    private List<UserMenuHeader> selectUserNodes(DSLContext dsl, Long userId, Long menuId) {
        Table<Record> table = DSL.table(DSL.name(TABLE_USER_MENU_HEADER));
        List<UserMenuHeader> nodes =
                dsl.select(USER_NODE_COLUMNS)
                        .from(table)
                        .where(DSL.field(DSL.name("user_id")).eq(userId))
                        .and(DSL.field(DSL.name("menu_id")).eq(menuId))
                        .and(DSL.field(DSL.name("subject_id")).eq(AppContext.getSubjectId()))
                        .and(DSL.field(DSL.name("project_no")).eq(AppContext.getProjectNo()))
                        .orderBy(
                                DSL.field(DSL.name("sort_value")).asc(),
                                DSL.field(DSL.name("id")).asc())
                        .fetchInto(UserMenuHeader.class);
        log.debug("查询用户个性化表头配置：userId={}, menuId={}, rows={}", userId, menuId, nodes.size());
        return nodes;
    }

    /** 新增用户表头节点（归属菜单与用户由入参决定，主体与项目取当前上下文） */
    private void insertUserNode(
            DSLContext dsl,
            SaveUserMenuHeaderReq node,
            Long menuId,
            Long userId,
            Long subjectId,
            String projectNo,
            Long currentUserId,
            LocalDateTime now) {
        Map<Field<Object>, Object> values = toFieldValues(node);
        values.put(DSL.field(DSL.name("user_id")), userId);
        values.put(DSL.field(DSL.name("menu_id")), menuId);
        values.put(DSL.field(DSL.name("subject_id")), subjectId);
        values.put(DSL.field(DSL.name("project_no")), projectNo);
        values.put(DSL.field(DSL.name("created_by")), currentUserId);
        values.put(DSL.field(DSL.name("created_date")), now);
        values.put(DSL.field(DSL.name("updated_by")), currentUserId);
        values.put(DSL.field(DSL.name("updated_date")), now);

        dsl.insertInto(DSL.table(DSL.name(TABLE_USER_MENU_HEADER))).set(values).execute();
    }

    /** 按主键更新用户表头节点（数据隔离：id + subject_id + project_no） */
    private void updateUserNode(
            DSLContext dsl,
            Long id,
            SaveUserMenuHeaderReq node,
            Long subjectId,
            String projectNo,
            Long currentUserId,
            LocalDateTime now) {
        Map<Field<Object>, Object> values = toFieldValues(node);
        values.put(DSL.field(DSL.name("updated_by")), currentUserId);
        values.put(DSL.field(DSL.name("updated_date")), now);

        dsl.update(DSL.table(DSL.name(TABLE_USER_MENU_HEADER)))
                .set(values)
                .where(
                        DSL.field(DSL.name("id"))
                                .eq(id)
                                .and(DSL.field(DSL.name("subject_id")).eq(subjectId))
                                .and(DSL.field(DSL.name("project_no")).eq(projectNo)))
                .execute();
    }

    /**
     * 批量删除用户表头节点（整棵树同步：库中多出、前端本次未提交的节点）
     *
     * <p>数据隔离：{@code id} 集合 + {@code user_id + menu_id + subject_id + project_no}。
     */
    private void deleteUserNodes(
            DSLContext dsl,
            Collection<Long> ids,
            Long userId,
            Long menuId,
            Long subjectId,
            String projectNo) {
        dsl.deleteFrom(DSL.table(DSL.name(TABLE_USER_MENU_HEADER)))
                .where(DSL.field(DSL.name("id")).in(ids))
                .and(DSL.field(DSL.name("user_id")).eq(userId))
                .and(DSL.field(DSL.name("menu_id")).eq(menuId))
                .and(DSL.field(DSL.name("subject_id")).eq(subjectId))
                .and(DSL.field(DSL.name("project_no")).eq(projectNo))
                .execute();
    }

    /** 提交节点转列值：colors 序列化为 JSON 串，父引用为空时按顶层处理 */
    private Map<Field<Object>, Object> toFieldValues(SaveUserMenuHeaderReq node) {
        Map<Field<Object>, Object> values = new LinkedHashMap<>();
        values.put(DSL.field(DSL.name("vir_id")), node.getVirId());
        values.put(
                DSL.field(DSL.name("parent_id")),
                node.getParentId() != null ? node.getParentId() : ROOT_PARENT_ID);
        values.put(DSL.field(DSL.name("node_type")), node.getNodeType());
        values.put(DSL.field(DSL.name("source_id")), node.getSourceId());
        values.put(DSL.field(DSL.name("module_id")), node.getModuleId());
        values.put(DSL.field(DSL.name("label")), node.getLabel());
        values.put(DSL.field(DSL.name("colors")), toColorsJson(node.getColors()));
        values.put(DSL.field(DSL.name("width")), node.getWidth());
        values.put(DSL.field(DSL.name("data_index")), node.getDataIndex());
        values.put(DSL.field(DSL.name("sorted")), node.getSorted());
        // search_type 在库中为 JSON 列，空串不是合法 JSON，统一归一为 null
        values.put(
                DSL.field(DSL.name("search_type")),
                StringUtils.hasText(node.getSearchType()) ? node.getSearchType() : null);
        values.put(DSL.field(DSL.name("ellipsis")), node.getEllipsis());
        values.put(DSL.field(DSL.name("fixed")), node.getFixed());
        values.put(DSL.field(DSL.name("checked")), node.getChecked());
        values.put(DSL.field(DSL.name("sort_value")), node.getSortValue());
        return values;
    }

    /** 深度优先压平提交的表头节点树（父节点在前） */
    private void flatten(SaveUserMenuHeaderReq node, List<SaveUserMenuHeaderReq> nodes) {
        nodes.add(node);
        List<SaveUserMenuHeaderReq> children = node.getChildren();
        if (children == null) {
            return;
        }
        for (SaveUserMenuHeaderReq child : children) {
            flatten(child, nodes);
        }
    }

    // ==================== 角色权限过滤（暂未启用） ====================

    /*
     * 按角色权限过滤用户个性化表头节点：菜单节点按 sys_role_menu 过滤（本菜单自身的根节点始终保留），
     * 字段节点按 sys_role_module_field_permission 仅保留 view=1 的字段（无记录即丢弃）。
     * 角色权限数据尚未配置完成，先整体注释；权限数据就绪后去掉注释并放开调用即可。
     *
    private List<UserMenuHeader> filterByRolePermission(
            List<UserMenuHeader> nodes, Long menuId, Long roleId) {
        if (roleId == null) {
            return nodes;
        }
        Set<Long> permittedMenuIds = openMenuHeaderApi.selectPermittedMenuIds(roleId);
        Set<Long> readableFieldIds =
                openMenuHeaderApi.selectReadableFieldIds(
                        roleId,
                        nodes.stream()
                                .filter(node -> !isMenuNode(node))
                                .map(UserMenuHeader::getSourceId)
                                .collect(Collectors.toList()));
        return nodes.stream()
                .filter(
                        node -> {
                            if (menuId.equals(node.getSourceId())) {
                                return true;
                            }
                            if (isMenuNode(node)) {
                                return permittedMenuIds != null
                                        && permittedMenuIds.contains(node.getSourceId());
                            }
                            return readableFieldIds != null
                                    && readableFieldIds.contains(node.getSourceId());
                        })
                .collect(Collectors.toList());
    }
    */

    // ==================== 可用节点池 ====================

    /**
     * 表头树节点虚拟id 集合（模块侧可用节点池）
     *
     * <p>传入的树由 config 侧按「菜单/字段生成 + {@code sys_menu_header} 合并」得到，节点全部来自模块侧生成 （仅 {@code
     * sys_menu_header} 中存在的历史节点已被丢弃），故其 {@code virId} 集合即当前模块侧真实存在的节点全集。
     */
    private Set<String> collectVirIds(SysMenuHeaderResp node) {
        Set<String> virIds = new HashSet<>();
        collectVirIds(node, virIds);
        return virIds;
    }

    private void collectVirIds(SysMenuHeaderResp node, Set<String> virIds) {
        virIds.add(node.getVirId());
        List<SysMenuHeaderResp> children = node.getChildren();
        if (children == null) {
            return;
        }
        for (SysMenuHeaderResp child : children) {
            collectVirIds(child, virIds);
        }
    }

    // ==================== 多级列头展示组装 ====================

    /**
     * 展示节点筛选：只保留已勾选（{@code checked=1}）的节点，并向上补齐其祖先节点
     *
     * <p>祖先节点不论是否勾选都保留，否则被勾选的下级列挂不到父节点上、多级列头组装不出来； 没有任何勾选节点时返回空列表。
     */
    private List<UserMenuHeader> retainCheckedWithAncestors(List<UserMenuHeader> nodes) {
        Map<String, UserMenuHeader> nodeByVirId = toMapByVirId(nodes);
        Set<String> retainedVirIds = new HashSet<>();
        for (UserMenuHeader node : nodes) {
            if (!CHECKED_YES.equals(node.getChecked())) {
                continue;
            }
            retainedVirIds.add(node.getVirId());
            // 逐级向上补齐祖先节点，遇到已保留的节点或父节点缺失即终止
            String parentId = node.getParentId();
            while (parentId != null && !ROOT_PARENT_ID.equals(parentId)) {
                UserMenuHeader parent = nodeByVirId.get(parentId);
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
    private UserMenuHeader selectDisplayRoot(Long menuId, List<UserMenuHeader> topNodes) {
        if (topNodes.isEmpty()) {
            return null;
        }
        for (UserMenuHeader node : topNodes) {
            if (isMenuNode(node) && menuId.equals(node.getSourceId())) {
                return node;
            }
        }
        for (UserMenuHeader node : topNodes) {
            if (isMenuNode(node)) {
                return node;
            }
        }
        return topNodes.getFirst();
    }

    /** 递归组装多级列头展示节点：菜单节点输出 sourceId/colors，字段节点输出字段展示配置 */
    private MenuHeaderColumnResp toColumnResp(
            UserMenuHeader node, Map<String, List<UserMenuHeader>> childrenByParent) {
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

        List<UserMenuHeader> children =
                childrenByParent.getOrDefault(node.getVirId(), Collections.emptyList());
        if (!children.isEmpty()) {
            resp.setChildren(
                    children.stream()
                            .map(child -> toColumnResp(child, childrenByParent))
                            .collect(Collectors.toList()));
        }
        return resp;
    }

    // ==================== 与默认表头树的合并 ====================

    /**
     * 递归合并用户个性化配置
     *
     * <p>以默认树为准遍历：两边 {@code virId} 相同取用户记录（展示配置覆盖默认值）；仅默认树有的节点保留默认值； 用户表中多出的 {@code virId}
     * 不参与遍历、自然被丢弃。合并后同级按 {@code sortValue} 升序重排，支持用户自定义列顺序。
     */
    private SysMenuHeaderResp mergeUserNodes(
            SysMenuHeaderResp node, Map<String, UserMenuHeader> userNodesByVirId) {
        UserMenuHeader userNode = userNodesByVirId.get(node.getVirId());
        SysMenuHeaderResp merged = mergeNode(node, userNode);

        List<SysMenuHeaderResp> children = node.getChildren();
        if (children == null) {
            return merged;
        }
        List<SysMenuHeaderResp> mergedChildren =
                children.stream()
                        .map(child -> mergeUserNodes(child, userNodesByVirId))
                        .collect(Collectors.toList());
        mergedChildren.sort(
                Comparator.comparing(
                        SysMenuHeaderResp::getSortValue, Comparator.nullsLast(Integer::compareTo)));
        merged.setChildren(mergedChildren);
        return merged;
    }

    /**
     * 同一虚拟id 合并：菜单与字段的层级/来源结构取默认树节点，展示配置取用户记录（用户值为空时回退默认值）
     *
     * <p>主键id 只有 {@code user_menu_header} 中的记录才保留；来自其它地方（生成侧 / {@code sys_menu_header}）的 节点即使带 id
     * 也一律去掉，保证前端回传的 id 只可能是用户表主键。
     */
    private SysMenuHeaderResp mergeNode(SysMenuHeaderResp node, UserMenuHeader userNode) {
        SysMenuHeaderResp merged = new SysMenuHeaderResp();
        merged.setVirId(node.getVirId());
        merged.setParentId(node.getParentId());
        merged.setMenuId(node.getMenuId());
        merged.setNodeType(node.getNodeType());
        merged.setSourceId(node.getSourceId());
        merged.setModuleId(node.getModuleId());
        merged.setLabel(node.getLabel());
        merged.setColors(node.getColors());
        merged.setWidth(node.getWidth());
        merged.setDataIndex(node.getDataIndex());
        merged.setSorted(node.getSorted());
        merged.setSearchType(node.getSearchType());
        merged.setEllipsis(node.getEllipsis());
        merged.setFixed(node.getFixed());
        merged.setChecked(node.getChecked());
        merged.setSortValue(node.getSortValue());

        // 默认表头没有该虚拟id 的用户配置：不带主键id 直接返回
        if (userNode == null) {
            return merged;
        }
        merged.setId(userNode.getId());
        merged.setLabel(userNode.getLabel() != null ? userNode.getLabel() : merged.getLabel());
        merged.setColors(resolveColors(userNode.getColors(), merged.getColors()));
        merged.setWidth(userNode.getWidth() != null ? userNode.getWidth() : merged.getWidth());
        merged.setDataIndex(
                userNode.getDataIndex() != null ? userNode.getDataIndex() : merged.getDataIndex());
        merged.setSorted(userNode.getSorted() != null ? userNode.getSorted() : merged.getSorted());
        merged.setSearchType(
                userNode.getSearchType() != null
                        ? userNode.getSearchType()
                        : merged.getSearchType());
        merged.setEllipsis(
                userNode.getEllipsis() != null ? userNode.getEllipsis() : merged.getEllipsis());
        merged.setFixed(userNode.getFixed() != null ? userNode.getFixed() : merged.getFixed());
        merged.setChecked(
                userNode.getChecked() != null ? userNode.getChecked() : merged.getChecked());
        merged.setSortValue(
                userNode.getSortValue() != null ? userNode.getSortValue() : merged.getSortValue());
        return merged;
    }

    // ==================== 小工具 ====================

    /** 按虚拟id 建索引（保留首次出现的记录） */
    private Map<String, UserMenuHeader> toMapByVirId(List<UserMenuHeader> nodes) {
        Map<String, UserMenuHeader> nodesByVirId = new LinkedHashMap<>();
        for (UserMenuHeader node : nodes) {
            nodesByVirId.putIfAbsent(node.getVirId(), node);
        }
        return nodesByVirId;
    }

    /**
     * 校验 searchType 是合法 JSON（库中 {@code search_type} 为 JSON 列），空值允许（落库时归一为 null）
     *
     * <p>该列为 JSON 类型后，空串与非法 JSON 都会被 MySQL 直接拒绝并抛出原始 SQL 异常，故在落库前先拦下， 给出可读的提示。
     */
    private void checkSearchTypeJson(String searchType) {
        if (!StringUtils.hasText(searchType)) {
            return;
        }
        try {
            objectMapper.readTree(searchType);
        } catch (Exception e) {
            throw new BusinessException("搜索控件类型(searchType)不是合法的 JSON：" + searchType);
        }
    }

    /** 序列化 colors（入参为颜色列表，库中存 JSON 数组串），空列表或失败返回 null */
    private String toColorsJson(List<String> colors) {
        if (colors == null || colors.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(colors);
        } catch (Exception e) {
            log.warn("用户表头 colors 序列化失败: {}", colors, e);
            return null;
        }
    }

    /** 解析用户配置的颜色（库中存 JSON 数组串），为空或解析失败时回退默认值 */
    private List<String> resolveColors(String userColors, List<String> defaultValue) {
        if (userColors == null || userColors.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(userColors, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("用户表头 colors 解析失败: {}", userColors, e);
            return defaultValue;
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
            log.warn("用户表头 colors 解析失败: {}", colors, e);
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
            log.warn("用户表头 searchType 解析失败: {}", searchType, e);
            return null;
        }
    }

    private boolean isMenuNode(UserMenuHeader node) {
        return Objects.equals(node.getNodeType(), NODE_TYPE_MENU);
    }
}
