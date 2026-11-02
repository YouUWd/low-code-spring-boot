package com.jdec.platform.common.biz.header.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.common.api.UserMenuHeaderApi;
import com.jdec.platform.common.api.dto.request.SaveUserMenuHeaderReq;
import com.jdec.platform.common.api.dto.request.SaveUserMenuHeaderWidthReq;
import com.jdec.platform.common.biz.header.entity.UserMenuHeader;
import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
import com.jdec.platform.config.api.dto.response.SysMenuHeaderResp;
import com.jdec.platform.config.api.enums.MenuFieldPermissionScope;
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

    /** 用户个性化表头配置表名（业务库） */
    private static final String TABLE_USER_MENU_HEADER = "user_menu_header";

    /** 查询场景：列表 */
    private static final String TYPE_LIST = "list";

    /** 查询场景：详情 */
    private static final String TYPE_DETAIL = "detail";

    /** 操作场景：申请 */
    private static final String OPERATE_APPLY = "apply";

    /** 操作场景：编辑 */
    private static final String OPERATE_EDIT = "edit";

    /** 有审批链标记 */
    private static final Integer HAS_APPROVE_YES = 1;

    /** 表头节点类型：菜单 */
    private static final Integer NODE_TYPE_MENU = 0;

    /** 表头节点类型：字段 */
    private static final Integer NODE_TYPE_FIELD = 1;

    /** 表头节点已勾选标记（只有已勾选的列在列表可见，也才允许拖动列宽） */
    private static final Integer CHECKED_YES = 1;

    /** 列宽下限(px) */
    private static final int WIDTH_MIN = 1;

    /** 菜单节点虚拟id 前缀：MENU_菜单ID（与 config 侧生成规则一致） */
    private static final String VIR_ID_PREFIX_MENU = "MENU";

    /** 字段节点虚拟id 前缀：Field_字段ID（与 config 侧生成规则一致） */
    private static final String VIR_ID_PREFIX_FIELD = "Field";

    /** 表头节点列（别名与行模型属性同名，保证记录精准映射到 {@link UserMenuHeader}） */
    private static final List<Field<?>> USER_NODE_COLUMNS =
            List.of(
                    DSL.field(DSL.name("id")).as("id"),
                    DSL.field(DSL.name("user_id")).as("userId"),
                    DSL.field(DSL.name("vir_id")).as("virId"),
                    DSL.field(DSL.name("menu_id")).as("menuId"),
                    DSL.field(DSL.name("node_type")).as("nodeType"),
                    DSL.field(DSL.name("source_id")).as("sourceId"),
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
        SysMenuHeaderResp tree = openMenuHeaderApi.getMenuHeaderTree(menuId, roleId);
        // 角色对该菜单无任何权限：整树丢弃，不返回任何节点
        if (tree == null) {
            return null;
        }

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
    public MenuHeaderDisplayResp getUserMenuHeaderDisplay(
            Long userId,
            Long menuId,
            Long roleId,
            String type,
            String operate,
            Integer hasApprove,
            Long currentRoleId) {
        if (userId == null || menuId == null) {
            throw new BusinessException("用户ID与菜单ID不能为空");
        }

        // 表头记录按详情菜单ID 归属，将前端传入的列表菜单ID 转换后再查
        Long headerMenuId = openMenuHeaderApi.resolveHeaderMenuId(menuId);

        // 按入参解析本次的字段权限过滤范围（查看/申请/编辑的任意组合），config 侧据此过滤字段节点
        MenuFieldPermissionScope scope =
                resolvePermissionScope(type, operate, hasApprove, roleId, currentRoleId);

        // 模块侧最新可用节点（菜单及其子菜单 + 各菜单绑定模块下的字段 + sys_menu_header 已保存展示配置）构成默认树，
        // 本次展示的层级关系一律取该树动态结构；角色对该菜单无任何权限时该树为 null
        SysMenuHeaderResp sourceTree = openMenuHeaderApi.getMenuHeaderTree(menuId, roleId, scope);
        if (sourceTree == null) {
            return new MenuHeaderDisplayResp();
        }
        Map<String, SysMenuHeaderResp> sourceByVirId = indexByVirId(sourceTree);

        // 读取该用户在该菜单下已保存的个性化表头节点（数据隔离：user_id + menu_id + subject_id + project_no），
        // 按虚拟id 建索引；仅保留默认树中仍存在的节点（模块侧已删除的菜单/字段不返回给前端）
        Map<String, UserMenuHeader> storedByVirId =
                toMapByVirId(
                        selectUserNodes(jooqDataContextFactory.getContext(), userId, headerMenuId)
                                .stream()
                                .filter(node -> sourceByVirId.containsKey(node.getVirId()))
                                .collect(Collectors.toList()));

        // 以默认树为骨架逐节点合并后再组装多级列头：用户有记录的节点取用户配置（用户未勾选即隐藏），
        // 用户没有记录的节点（模块侧新增、或系统表头后来才勾选的列）回落到系统默认配置，
        // 与「用户表头查询」接口 getUserMenuHeaderTree 的合并口径保持一致
        return openMenuHeaderApi.buildMenuHeaderDisplay(mergeUserNodes(sourceTree, storedByVirId));
    }

    /**
     * 解析字段权限过滤范围（查看/申请/编辑的任意组合）
     *
     * <ul>
     *   <li>列表（{@code type=list}）：查看、申请、编辑任一为 1 即可见；
     *   <li>详情-申请（{@code type=detail, operate=apply}）：查看、申请任一为 1 即可见；
     *   <li>详情-编辑（{@code type=detail, operate=edit}）：无审批链（{@code hasApprove != 1}）或 {@code roleId}
     *       与 {@code currentRoleId} 相等时查看、编辑任一为 1 即可见，不相等时仅查看为 1 可见。
     * </ul>
     *
     * <p>未知的 {@code type}/{@code operate} 一律回退为「仅查看」。
     */
    private static MenuFieldPermissionScope resolvePermissionScope(
            String type, String operate, Integer hasApprove, Long roleId, Long currentRoleId) {
        if (TYPE_LIST.equals(type)) {
            return MenuFieldPermissionScope.ANY;
        }
        if (!TYPE_DETAIL.equals(type)) {
            return MenuFieldPermissionScope.VIEW_ONLY;
        }
        if (OPERATE_APPLY.equals(operate)) {
            return MenuFieldPermissionScope.VIEW_OR_APPLY;
        }
        if (OPERATE_EDIT.equals(operate)) {
            if (!HAS_APPROVE_YES.equals(hasApprove)) {
                return MenuFieldPermissionScope.VIEW_OR_EDIT;
            }
            return Objects.equals(roleId, currentRoleId)
                    ? MenuFieldPermissionScope.VIEW_OR_EDIT
                    : MenuFieldPermissionScope.VIEW_ONLY;
        }
        return MenuFieldPermissionScope.VIEW_ONLY;
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
    public void saveUserMenuHeaderWidth(SaveUserMenuHeaderWidthReq req) {
        if (req == null || req.getMenuId() == null) {
            throw new BusinessException("菜单ID不能为空");
        }
        if (req.getNodeType() == null || req.getSourceId() == null) {
            throw new BusinessException("节点类型与来源ID不能为空");
        }
        if (req.getWidth() == null || req.getWidth() < WIDTH_MIN) {
            throw new BusinessException("列宽必须为正整数(px)");
        }

        // 列宽是个人的展示偏好，用户与角色一律取当前请求上下文
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("未获取到当前登录用户");
        }

        // 用户表头记录按表头归属菜单ID 存放，将前端传入的列表菜单ID 转换后再读写
        Long headerMenuId = openMenuHeaderApi.resolveHeaderMenuId(req.getMenuId());
        String virId = buildVirId(req.getNodeType(), req.getSourceId());
        DSLContext dsl = jooqDataContextFactory.getContext();
        List<UserMenuHeader> stored = selectUserNodes(dsl, userId, headerMenuId);

        UserMenuHeader storedNode = matchUserNode(stored, virId, req.getDataIndex());
        if (storedNode != null) {
            // 命中该列已保存记录：节点类型与勾选状态都以库中记录为准（不由前端声明决定），只更新宽度
            checkResizable(storedNode.getNodeType(), storedNode.getChecked());
            updateUserNodeWidth(dsl, storedNode, req.getWidth());
            log.debug(
                    "保存用户个性化表头列宽：userId={}, menuId={}, virId={}, id={}, width={}",
                    userId,
                    headerMenuId,
                    storedNode.getVirId(),
                    storedNode.getId(),
                    req.getWidth());
            return;
        }

        // 该列还没有用户记录：整棵树未个性化，或个性化后模块侧新增 / 系统表头后来才勾选的列，
        // 统一按「用户表头查询」接口的同一口径取值（默认树为骨架 + 已有个性化按 virId 逐节点叠加）
        SysMenuHeaderResp sourceTree =
                openMenuHeaderApi.getMenuHeaderTree(req.getMenuId(), UserContext.getRoleId());
        if (sourceTree == null) {
            throw new BusinessException("当前角色在该菜单下没有可用的表头配置");
        }
        SysMenuHeaderResp tree = mergeUserNodes(sourceTree, toMapByVirId(stored));
        SysMenuHeaderResp target = findTreeNode(tree, virId, req.getDataIndex());
        if (target == null) {
            throw new BusinessException("表头节点不存在：" + virId);
        }
        checkResizable(target.getNodeType(), target.getChecked());
        // 宽度直接设进树节点，落库时随该节点一起写入
        target.setWidth(req.getWidth());

        if (stored.isEmpty()) {
            // 首次个性化：整棵用户表头树落库（与保存接口提交整棵树同一口径），目标列宽度一并写入
            SaveUserMenuHeaderReq saveReq = toSaveNode(tree);
            saveReq.setUserId(userId);
            saveReq.setMenuId(req.getMenuId());
            saveUserMenuHeader(saveReq);
            log.debug(
                    "首次个性化并保存列宽：userId={}, menuId={}, virId={}, width={}",
                    userId,
                    headerMenuId,
                    target.getVirId(),
                    req.getWidth());
        } else {
            // 已有个性化记录：只补插该列一条记录，其余节点保持原有记录不动
            insertUserNode(
                    dsl,
                    toSaveNode(target),
                    headerMenuId,
                    userId,
                    AppContext.getSubjectId(),
                    AppContext.getProjectNo(),
                    userId,
                    LocalDateTime.now());
            log.debug(
                    "补插用户表头列并保存列宽：userId={}, menuId={}, virId={}, width={}",
                    userId,
                    headerMenuId,
                    target.getVirId(),
                    req.getWidth());
        }
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
     * 按主键只更新该记录的列宽（其余展示配置保持原值）
     *
     * <p>数据隔离：{@code id + user_id + menu_id + subject_id + project_no}，记录已被其它请求删除/换绑时更新不命中并抛出提示。
     */
    private void updateUserNodeWidth(DSLContext dsl, UserMenuHeader storedNode, Integer width) {
        int updated =
                dsl.update(DSL.table(DSL.name(TABLE_USER_MENU_HEADER)))
                        .set(DSL.field(DSL.name("width")), width)
                        .set(DSL.field(DSL.name("updated_by")), UserContext.getUserId())
                        .set(DSL.field(DSL.name("updated_date")), LocalDateTime.now())
                        .where(
                                DSL.field(DSL.name("id"))
                                        .eq(storedNode.getId())
                                        .and(
                                                DSL.field(DSL.name("user_id"))
                                                        .eq(storedNode.getUserId()))
                                        .and(
                                                DSL.field(DSL.name("menu_id"))
                                                        .eq(storedNode.getMenuId()))
                                        .and(
                                                DSL.field(DSL.name("subject_id"))
                                                        .eq(AppContext.getSubjectId()))
                                        .and(
                                                DSL.field(DSL.name("project_no"))
                                                        .eq(AppContext.getProjectNo())))
                        .execute();
        if (updated == 0) {
            throw new BusinessException("列宽保存失败，请刷新后重试");
        }
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

    /** 提交节点转列值：colors 序列化为 JSON 串；父子层级/模块/展示名不落库（查询时动态生成） */
    private Map<Field<Object>, Object> toFieldValues(SaveUserMenuHeaderReq node) {
        Map<Field<Object>, Object> values = new LinkedHashMap<>();
        values.put(DSL.field(DSL.name("vir_id")), node.getVirId());
        values.put(DSL.field(DSL.name("node_type")), node.getNodeType());
        values.put(DSL.field(DSL.name("source_id")), node.getSourceId());
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

    /**
     * 表头树节点转保存请求节点：展示配置原样下移（层级/模块/展示名仍不落库），根节点的 userId 与列表菜单ID 由调用方补齐
     *
     * <p>不携带主键 {@code id}：走这里的都是库里没有该列记录的场景，整树落库按 {@code vir_id} 匹配即可。
     */
    private SaveUserMenuHeaderReq toSaveNode(SysMenuHeaderResp node) {
        SaveUserMenuHeaderReq req = new SaveUserMenuHeaderReq();
        req.setVirId(node.getVirId());
        req.setNodeType(node.getNodeType());
        req.setSourceId(node.getSourceId());
        req.setDataIndex(node.getDataIndex());
        req.setColors(node.getColors());
        req.setWidth(node.getWidth());
        req.setSorted(node.getSorted());
        req.setSearchType(node.getSearchType());
        req.setEllipsis(node.getEllipsis());
        req.setFixed(node.getFixed());
        req.setChecked(node.getChecked());
        req.setSortValue(node.getSortValue());
        List<SysMenuHeaderResp> children = node.getChildren();
        if (children != null) {
            req.setChildren(children.stream().map(this::toSaveNode).collect(Collectors.toList()));
        }
        return req;
    }

    // ==================== 可用节点池 ====================

    /**
     * 默认表头树按虚拟id 建索引（模块侧可用节点池）
     *
     * <p>传入的树由 config 侧按「菜单/字段生成 + {@code sys_menu_header} 合并」得到，节点全部来自模块侧生成 （仅 {@code
     * sys_menu_header} 中存在的历史节点已被丢弃），故其 {@code virId} 集合即当前模块侧真实存在的节点全集，
     * 且每个节点的展示名与关联字段标记已按源头解析，可直接作为用户表头节点的展示字段来源。
     */
    private Map<String, SysMenuHeaderResp> indexByVirId(SysMenuHeaderResp node) {
        Map<String, SysMenuHeaderResp> nodeByVirId = new LinkedHashMap<>();
        indexByVirId(node, nodeByVirId);
        return nodeByVirId;
    }

    private void indexByVirId(SysMenuHeaderResp node, Map<String, SysMenuHeaderResp> nodeByVirId) {
        nodeByVirId.putIfAbsent(node.getVirId(), node);
        List<SysMenuHeaderResp> children = node.getChildren();
        if (children == null) {
            return;
        }
        for (SysMenuHeaderResp child : children) {
            indexByVirId(child, nodeByVirId);
        }
    }

    /**
     * 按虚拟id 从表头树中定位节点，未命中时按字段索引兜底比对
     *
     * <p>树与库中的 {@code data_index} 都可能是「表名.字段名」或带模块ID 前缀的「模块ID.表名.字段名」，比对前统一去前缀。
     */
    private SysMenuHeaderResp findTreeNode(SysMenuHeaderResp tree, String virId, String dataIndex) {
        Map<String, SysMenuHeaderResp> nodesByVirId = indexByVirId(tree);
        SysMenuHeaderResp node = nodesByVirId.get(virId);
        if (node != null || !StringUtils.hasText(dataIndex)) {
            return node;
        }
        String expected = stripModulePrefix(dataIndex);
        return nodesByVirId.values().stream()
                .filter(item -> expected.equalsIgnoreCase(stripModulePrefix(item.getDataIndex())))
                .findFirst()
                .orElse(null);
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
        merged.setRelationWord(node.getRelationWord());
        merged.setBusinessTypeCode(node.getBusinessTypeCode());
        merged.setBusinessTypeName(node.getBusinessTypeName());
        merged.setUnitDataIndex(node.getUnitDataIndex());
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
        // 展示名与关联字段标记仍取默认树（源头），用户表不参与覆盖
        merged.setColors(resolveColors(userNode.getColors(), merged.getColors()));
        merged.setWidth(userNode.getWidth() != null ? userNode.getWidth() : merged.getWidth());
        merged.setDataIndex(
                stripModulePrefix(
                        userNode.getDataIndex() != null
                                ? userNode.getDataIndex()
                                : merged.getDataIndex()));
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

    /** 按虚拟id 从已保存记录中定位目标列，未命中时按字段索引兜底比对（同样先去模块ID 前缀） */
    private UserMenuHeader matchUserNode(
            List<UserMenuHeader> stored, String virId, String dataIndex) {
        UserMenuHeader node = toMapByVirId(stored).get(virId);
        if (node != null || !StringUtils.hasText(dataIndex)) {
            return node;
        }
        String expected = stripModulePrefix(dataIndex);
        return stored.stream()
                .filter(item -> expected.equalsIgnoreCase(stripModulePrefix(item.getDataIndex())))
                .findFirst()
                .orElse(null);
    }

    /** 节点虚拟id：菜单 {@code MENU_菜单ID}，字段 {@code Field_字段ID}，与生成侧规则一致 */
    private static String buildVirId(Integer nodeType, Long sourceId) {
        String prefix = NODE_TYPE_MENU.equals(nodeType) ? VIR_ID_PREFIX_MENU : VIR_ID_PREFIX_FIELD;
        return prefix + "_" + sourceId;
    }

    /** 只有「字段」且已勾选（{@code checked=1}）的列才能设置列宽：菜单节点无列宽概念，未勾选的列列表上不展示 */
    private static void checkResizable(Integer nodeType, Integer checked) {
        if (!NODE_TYPE_FIELD.equals(nodeType)) {
            throw new BusinessException("仅字段列支持设置列宽");
        }
        if (!CHECKED_YES.equals(checked)) {
            throw new BusinessException("该列未勾选显示，不能设置列宽");
        }
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

    /** 字段索引去掉前缀模块ID：1006.company_contact.id → company_contact.id（无数字前缀时原样返回） */
    private static String stripModulePrefix(String dataIndex) {
        if (dataIndex == null) {
            return null;
        }
        int dot = dataIndex.indexOf('.');
        if (dot > 0 && dataIndex.substring(0, dot).chars().allMatch(Character::isDigit)) {
            return dataIndex.substring(dot + 1);
        }
        return dataIndex;
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
}
