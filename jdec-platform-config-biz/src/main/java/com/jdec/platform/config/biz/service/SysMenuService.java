package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysMenuApi;
import com.jdec.platform.config.api.dto.request.SaveMenuReq;
import com.jdec.platform.config.api.dto.request.SysMenuDragReq;
import com.jdec.platform.config.api.dto.request.SysMenuSaveReq;
import com.jdec.platform.config.api.dto.response.SysMenuResp;
import com.jdec.platform.config.api.dto.response.SysMenuTreeResp;
import com.jdec.platform.config.api.enums.MenuCategoryEnum;
import com.jdec.platform.config.biz.check.CheckConstant;
import com.jdec.platform.config.biz.check.ReferenceCheckManager;
import com.jdec.platform.config.biz.check.ReferenceContext;
import com.jdec.platform.config.biz.entity.SysMenu;
import com.jdec.platform.config.biz.mapper.SysMenuMapper;
import com.jdec.platform.config.biz.mapper.SysRoleMenuMapper;
import com.jdec.platform.shared.audit.annotation.DataAudit;
import com.jdec.platform.shared.audit.enums.OperationType;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.exception.PopException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/** 系统菜单 Service 实现 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysMenuService implements SysMenuApi {

    private final SysMenuMapper sysMenuMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final ReferenceCheckManager referenceCheckManager;
    private final SysMenuHeaderService sysMenuHeaderService;

    /** 菜单类型：导航 */
    private static final Integer MENU_TYPE_NAV = 1;

    /** 菜单类型：页面（仅该类型且关联详情菜单时，表头归属其详情菜单） */
    private static final Integer MENU_TYPE_PAGE = 2;

    /** 顶层父级ID */
    private static final Long ROOT_PID = 0L;

    /** 表头变更标记：菜单关联的详情菜单未变化 */
    private static final Integer HEADER_CHANGE_NONE = 0;

    /** 表头变更标记：菜单关联的详情菜单已切换（表头配置需重新生成） */
    private static final Integer HEADER_CHANGE_DETAIL_SWITCHED = 1;

    /**
     * 延迟获取自身代理，避免构造循环依赖。
     *
     * <p>整棵树保存时经代理调用 {@link #saveMenuNode(SysMenuSaveReq)}，确保 {@code @DataAudit} 等切面在节点级保存上生效。
     */
    private final ObjectProvider<SysMenuService> selfProvider;

    @Override
    public List<SysMenuTreeResp> tree(Integer category) {
        String projectNo = AppContext.getProjectNo();
        List<SysMenu> list =
                sysMenuMapper.selectList(
                        new LambdaQueryWrapper<SysMenu>()
                                .eq(category != null, SysMenu::getCategory, category)
                                .eq(SysMenu::getSubjectId, AppContext.getSubjectId())
                                .eq(SysMenu::getProjectNo, projectNo)
                                .orderByAsc(SysMenu::getSortOrder)
                                .orderByAsc(SysMenu::getId));
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        // 构建映射
        Map<Long, SysMenuTreeResp> nodeMap = new LinkedHashMap<>();
        for (SysMenu menu : list) {
            SysMenuTreeResp resp = new SysMenuTreeResp();
            BeanUtils.copyProperties(menu, resp);
            resp.setChildren(new ArrayList<>());
            nodeMap.put(menu.getId(), resp);
        }
        // 组装树
        List<SysMenuTreeResp> tree = new ArrayList<>();
        for (SysMenu menu : list) {
            SysMenuTreeResp node = nodeMap.get(menu.getId());
            Long pid = menu.getPid();
            if (pid == null || pid.equals(ROOT_PID)) {
                tree.add(node);
            } else {
                SysMenuTreeResp parent = nodeMap.get(pid);
                if (parent != null && parent.getChildren() != null) {
                    parent.getChildren().add(node);
                } else {
                    tree.add(node);
                }
            }
        }
        return tree;
    }

    @Override
    public List<SysMenuResp> list(Integer category) {
        String projectNo = AppContext.getProjectNo();
        List<SysMenu> list =
                sysMenuMapper.selectList(
                        new LambdaQueryWrapper<SysMenu>()
                                .eq(category != null, SysMenu::getCategory, category)
                                .eq(SysMenu::getSubjectId, AppContext.getSubjectId())
                                .eq(SysMenu::getProjectNo, projectNo)
                                .orderByAsc(SysMenu::getSortOrder)
                                .orderByAsc(SysMenu::getId));
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        // 转换为 DTO
        List<SysMenuResp> result = new ArrayList<>();
        for (SysMenu menu : list) {
            SysMenuResp resp = new SysMenuResp();
            BeanUtils.copyProperties(menu, resp);
            result.add(resp);
        }
        return result;
    }

    @Transactional
    @Override
    public SysMenuResp saveMenu(SaveMenuReq request) {
        if (request == null || request.getMenu() == null) {
            throw new BusinessException("菜单信息不能为空");
        }
        SysMenuSaveReq node = request.getMenu();
        boolean hasTree = !CollectionUtils.isEmpty(request.getTreeNodes());
        if (hasTree) {
            // 携带整棵树时，当前节点位置以 treeNodes 为准，保证同级重名等单节点校验分组正确
            fillOwnPositionFromTree(node, request.getTreeNodes());
        }
        // 表头记录归属菜单的判定与旧配置清理都必须在保存前取旧数据（保存后库中已是新的菜单类型/详情菜单ID）
        SysMenu oldMenu = selectMenuById(node.getId());
        Long previousHeaderMenuId =
                oldMenu == null ? null : sysMenuHeaderService.resolveHeaderMenuId(oldMenu);
        // 经自身代理调用单节点保存，确保 @DataAudit 审计生效
        SysMenuResp resp = selfProvider.getObject().saveMenuNode(node);
        if (hasTree) {
            // 整棵树父子关系与同级排序同步（参考模块 saveModule / 状态 saveStatus 的树节点拓扑同步逻辑）
            updateMenuTreeNodes(node.getId(), node.getCategory(), request.getTreeNodes());
        }
        // 多级表头配置随菜单一并保存：归属菜单与变更标记统一按表头归属解析规则判定
        Long headerMenuId = resolveHeaderMenuId(node);
        Integer headerChange =
                Objects.equals(previousHeaderMenuId, headerMenuId)
                        ? HEADER_CHANGE_NONE
                        : HEADER_CHANGE_DETAIL_SWITCHED;
        sysMenuHeaderService.saveMenuHeader(
                headerMenuId, previousHeaderMenuId, headerChange, request.getHeader());
        return resp;
    }

    /** 提交节点 → 表头归属菜单ID：页面类型且关联详情菜单时取详情菜单ID，其余（表等）取菜单自身ID */
    private Long resolveHeaderMenuId(SysMenuSaveReq node) {
        if (MENU_TYPE_PAGE.equals(node.getMenuType()) && node.getDetailId() != null) {
            return node.getDetailId();
        }
        return node.getId();
    }

    /** 取库中菜单记录（数据隔离：subject_id + project_no），id 为空或查不到返回 {@code null} */
    private SysMenu selectMenuById(Long id) {
        if (id == null) {
            return null;
        }
        return sysMenuMapper.selectOne(
                Wrappers.<SysMenu>lambdaQuery()
                        .eq(SysMenu::getId, id)
                        .eq(SysMenu::getSubjectId, AppContext.getSubjectId())
                        .eq(SysMenu::getProjectNo, AppContext.getProjectNo()));
    }

    /**
     * 单节点菜单新增/编辑：id 为空则新增，否则编辑。
     *
     * <p>供整棵菜单树保存接口经自身代理调用，以保留 {@code @DataAudit} 审计。
     */
    @DataAudit(
            module = "系统设置",
            subModule = "菜单配置",
            subModuleField = "#req.category",
            subModuleEnumClass = MenuCategoryEnum.class,
            operation = OperationType.UPDATE,
            tableName = "sys_menu",
            dataIdField = "#req.id")
    @Transactional
    public SysMenuResp saveMenuNode(SysMenuSaveReq req) {
        Long pid = req.getPid() == null ? ROOT_PID : req.getPid();
        req.setPid(pid);
        Long subjectId = AppContext.getSubjectId();
        String projectNo = AppContext.getProjectNo();

        // 校验：一级菜单类型必须为导航
        if (pid.equals(ROOT_PID) && !MENU_TYPE_NAV.equals(req.getMenuType())) {
            throw new BusinessException("一级菜单类型必须为导航");
        }

        // 保存前先查询旧数据（用于审计）
        SysMenu oldEntity = null;
        if (req.getId() != null) {
            oldEntity =
                    sysMenuMapper.selectOne(
                            Wrappers.<SysMenu>lambdaQuery()
                                    .eq(SysMenu::getId, req.getId())
                                    .eq(SysMenu::getSubjectId, subjectId)
                                    .eq(SysMenu::getProjectNo, projectNo));
            if (oldEntity == null) {
                throw new BusinessException("菜单不存在");
            }
        }

        SysMenu entity;
        if (req.getId() == null) {
            // 新增
            entity = new SysMenu();
            BeanUtils.copyProperties(req, entity);
            entity.setSubjectId(subjectId);
            entity.setProjectNo(projectNo);
            // 校验同级名称是否重复（按父级、分类、主体、应用隔离）
            checkDuplicateTitle(pid, req.getCategory(), req.getTitle(), subjectId, null);
            // 排序由前端决定：已传则按原值直接入库，为空则追加到同级末尾（后端不再搬移其它同级节点）
            if (req.getSortOrder() == null) {
                Integer maxSortOrder = getMaxSortOrderByPid(pid, subjectId, projectNo);
                entity.setSortOrder(maxSortOrder == null ? 0 : maxSortOrder + 1);
            }
            sysMenuMapper.insert(entity);
            // 设置id到请求对象，用于审计切面重新解析dataId
            req.setId(entity.getId());
            // 快照由审计切面自动创建，这里不需要手动创建
        } else {
            // 编辑
            // 不能把自己设为自己的父节点
            if (req.getId().equals(pid)) {
                throw new BusinessException("不能将自己设为父菜单");
            }
            // 校验是否形成了环（不能把自己子节点设为父节点）
            if (!pid.equals(ROOT_PID)) {
                checkCycle(req.getId(), pid, subjectId);
            }
            // 校验同级名称是否重复
            checkDuplicateTitle(pid, req.getCategory(), req.getTitle(), subjectId, req.getId());

            Long oldPid = oldEntity.getPid();

            entity = new SysMenu();
            BeanUtils.copyProperties(req, entity);

            // 排序由前端决定：父级变化且未传 sortOrder 时追加到新父级末尾；已传则按原值直接入库，后端不再搬移其它同级节点
            if (req.getSortOrder() == null && !pid.equals(oldPid)) {
                Integer maxSortOrder = getMaxSortOrderByPid(pid, subjectId, projectNo);
                entity.setSortOrder(maxSortOrder == null ? 0 : maxSortOrder + 1);
            }

            sysMenuMapper.updateById(entity);

            // 停用时级联停用所有子节点
            if (Integer.valueOf(0).equals(req.getEnabled())
                    && !Integer.valueOf(0).equals(oldEntity.getEnabled())) {
                Set<Long> childIds = collectChildrenIds(req.getId(), subjectId, projectNo);
                if (!childIds.isEmpty()) {
                    sysMenuMapper.update(
                            null,
                            Wrappers.<SysMenu>lambdaUpdate()
                                    .set(SysMenu::getEnabled, 0)
                                    .in(SysMenu::getId, childIds));
                }
            }
        }

        // 构建返回对象（将保存后的菜单信息返回给前端，新增时携带生成的ID）
        SysMenuResp resp = new SysMenuResp();
        BeanUtils.copyProperties(entity, resp);
        return resp;
    }

    /** 整棵树保存时，将当前节点在 treeNodes 中的位置回填到节点请求，保证单节点校验分组正确 */
    private void fillOwnPositionFromTree(
            SysMenuSaveReq node, List<SaveMenuReq.MenuTreeNode> treeNodes) {
        SaveMenuReq.MenuTreeNode own = findMenuTreeNode(treeNodes, node.getId());
        if (own == null) {
            return;
        }
        if (own.getParentId() != null) {
            node.setPid(own.getParentId());
        }
        if (node.getId() == null && own.getSortOrder() != null) {
            node.setSortOrder(own.getSortOrder());
        }
    }

    /** 在整棵树的扁平节点中查找与指定菜单ID匹配的节点（新增菜单按空或0匹配） */
    private SaveMenuReq.MenuTreeNode findMenuTreeNode(
            List<SaveMenuReq.MenuTreeNode> treeNodes, Long menuId) {
        for (SaveMenuReq.MenuTreeNode node : treeNodes) {
            boolean matched;
            if (menuId == null) {
                matched = node.getId() == null || node.getId() == 0L;
            } else {
                matched = menuId.equals(node.getId());
            }
            if (matched) {
                return node;
            }
        }
        return null;
    }

    /**
     * 同步整棵菜单树的父子关系与同级排序（参考模块 saveModule 的树节点拓扑同步）。
     *
     * <p>节点 id 为空或 0 视为本次新增的当前菜单，替换为保存后生成的真实ID；仅对实际发生变化的节点执行更新， 请求树中缺失的节点保持原样不做删除处理。
     */
    private void updateMenuTreeNodes(
            Long currentSavedMenuId, Integer category, List<SaveMenuReq.MenuTreeNode> treeNodes) {
        if (CollectionUtils.isEmpty(treeNodes)) {
            return;
        }
        Long subjectId = AppContext.getSubjectId();
        String projectNo = AppContext.getProjectNo();

        // 1. 查询当前主体、应用、分类下的全部菜单
        List<SysMenu> allMenus =
                sysMenuMapper.selectList(
                        Wrappers.<SysMenu>lambdaQuery()
                                .eq(SysMenu::getProjectNo, projectNo)
                                .eq(SysMenu::getSubjectId, subjectId)
                                .eq(category != null, SysMenu::getCategory, category));
        Map<Long, SysMenu> currentMap =
                allMenus.stream().collect(Collectors.toMap(SysMenu::getId, m -> m));

        // 2. 规整节点：id 为空或 0 表示本次新增的当前菜单，替换为真实生成的 ID
        List<SaveMenuReq.MenuTreeNode> normalizedNodes = new ArrayList<>();
        for (SaveMenuReq.MenuTreeNode node : treeNodes) {
            SaveMenuReq.MenuTreeNode normalized = new SaveMenuReq.MenuTreeNode();
            Long nid =
                    (node.getId() == null || node.getId() == 0L)
                            ? currentSavedMenuId
                            : node.getId();
            Long npid =
                    (node.getParentId() == null || node.getParentId() == 0L)
                            ? 0L
                            : node.getParentId();
            normalized.setId(nid);
            normalized.setParentId(npid);
            normalized.setSortOrder(node.getSortOrder() != null ? node.getSortOrder() : 0);
            normalizedNodes.add(normalized);
        }

        // 3. 校验节点均属于当前主体与应用，并组装最终的父级映射
        Map<Long, Long> nextParentMap = new HashMap<>();
        for (SysMenu menu : allMenus) {
            nextParentMap.put(menu.getId(), menu.getPid() != null ? menu.getPid() : 0L);
        }
        for (SaveMenuReq.MenuTreeNode node : normalizedNodes) {
            if (!currentMap.containsKey(node.getId())) {
                throw new BusinessException(400, "待调整树结构的菜单不存在或无权操作: " + node.getId());
            }
            if (node.getParentId() != 0L && !currentMap.containsKey(node.getParentId())) {
                throw new BusinessException(400, "目标父菜单不存在或无权操作: " + node.getParentId());
            }
            nextParentMap.put(node.getId(), node.getParentId());
        }

        // 4. 防环校验：沿父级链路向上遍历，防止出现环状依赖
        for (Long startId : nextParentMap.keySet()) {
            Set<Long> visited = new HashSet<>();
            Long cur = startId;
            while (cur != null && cur != 0L) {
                if (!visited.add(cur)) {
                    throw new BusinessException(400, "菜单树结构存在循环引用，调整失败");
                }
                cur = nextParentMap.get(cur);
            }
        }

        // 5. 差异比对：仅对实际发生父级或同级排序变化的节点执行更新
        List<SysMenu> updateList = new ArrayList<>();
        for (SaveMenuReq.MenuTreeNode node : normalizedNodes) {
            SysMenu current = currentMap.get(node.getId());
            if (current == null) {
                continue;
            }
            // 校验：移到顶层时必须是导航类型（与单节点保存、拖拽规则一致）
            if (node.getParentId() == 0L && !MENU_TYPE_NAV.equals(current.getMenuType())) {
                throw new BusinessException("一级菜单类型必须为导航");
            }
            Long currentPid = current.getPid() != null ? current.getPid() : 0L;
            Integer currentSortOrder = current.getSortOrder() != null ? current.getSortOrder() : 0;
            if (!Objects.equals(currentPid, node.getParentId())
                    || !Objects.equals(currentSortOrder, node.getSortOrder())) {
                SysMenu toUpdate = new SysMenu();
                toUpdate.setId(current.getId());
                toUpdate.setPid(node.getParentId());
                toUpdate.setSortOrder(node.getSortOrder());
                updateList.add(toUpdate);
            }
        }

        if (!updateList.isEmpty()) {
            updateList.forEach(sysMenuMapper::updateById);
            log.info("同步更新了 {} 个菜单树节点的位置与排序", updateList.size());
        }
    }

    @DataAudit(
            module = "系统设置",
            subModule = "菜单配置",
            subModuleField = "#category",
            operation = OperationType.DELETE,
            deleteDisplayField = "title",
            entityClass = SysMenu.class,
            tableName = "sys_menu",
            dataIdField = "#id")
    @Transactional
    @Override
    public void deleteMenu(Long id, String category) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        SysMenu menu =
                sysMenuMapper.selectOne(
                        Wrappers.<SysMenu>lambdaQuery()
                                .eq(SysMenu::getId, id)
                                .eq(SysMenu::getSubjectId, subjectId)
                                .eq(SysMenu::getProjectNo, projectNo));
        if (menu == null) {
            throw new BusinessException("菜单不存在");
        }
        // 引用检查
        ReferenceContext context =
                ReferenceContext.builder()
                        .targetType(CheckConstant.SYS_MENU)
                        .targetId(menu.getId())
                        .targetName(menu.getTitle())
                        .build();
        List<String> check = referenceCheckManager.check(context);
        if (!check.isEmpty()) {
            throw new PopException(String.join("\n", check));
        }
        // 删除菜单
        sysMenuMapper.deleteById(id);

        // 删除后重排同级节点的排序值
        reorderSiblingsAfterDelete(menu.getPid(), subjectId, projectNo);
    }

    @Transactional
    @Override
    public void dragMenu(SysMenuDragReq req) {
        Long id = req.getId();
        Long targetPid = req.getTargetPid() == null ? ROOT_PID : req.getTargetPid();
        Integer targetIndex = req.getSortOrder();
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();

        if (id == null) {
            throw new BusinessException("节点ID不能为空");
        }
        SysMenu node =
                sysMenuMapper.selectOne(
                        Wrappers.<SysMenu>lambdaQuery()
                                .eq(SysMenu::getId, id)
                                .eq(SysMenu::getSubjectId, subjectId)
                                .eq(SysMenu::getProjectNo, projectNo));
        if (node == null) {
            throw new BusinessException("菜单不存在");
        }
        // 不能拖到自己下面
        if (id.equals(targetPid)) {
            throw new BusinessException("不能将自己设为自己的父节点");
        }
        // 不能拖到自己的子节点下面（形成环）
        if (!targetPid.equals(ROOT_PID)) {
            checkCycle(id, targetPid, subjectId);
            boolean exists =
                    sysMenuMapper.exists(
                            Wrappers.<SysMenu>lambdaQuery()
                                    .eq(SysMenu::getId, targetPid)
                                    .eq(SysMenu::getSubjectId, subjectId)
                                    .eq(SysMenu::getProjectNo, projectNo));
            if (!exists) {
                throw new BusinessException("目标父菜单不存在");
            }
        }
        // 校验：拖到顶层时必须是导航类型
        if (targetPid.equals(ROOT_PID) && !MENU_TYPE_NAV.equals(node.getMenuType())) {
            throw new BusinessException("一级菜单类型必须为导航");
        }
        // 校验同级名称是否重复
        checkDuplicateTitle(targetPid, node.getCategory(), node.getTitle(), subjectId, id);

        // 更新节点的父级
        node.setPid(targetPid);

        // 处理排序值
        if (targetIndex == null) {
            // 前端未传顺序值，自动分配为同级最大值+1
            Integer maxSort = getMaxSortOrderByPid(targetPid, subjectId, projectNo);
            node.setSortOrder(maxSort == null ? 0 : maxSort + 1);
            sysMenuMapper.updateById(node);
        } else {
            // 前端传了顺序值（从0开始），重新分配所有同级节点的排序
            adjustSortOrderForDrag(node, targetIndex, subjectId, projectNo);
        }
    }

    /**
     * 处理拖拽时的排序调整
     *
     * @param entity 被拖拽的节点
     * @param targetIndex 目标位置的顺序值（从0开始）
     * @param subjectId 主体ID
     * @param projectNo 应用编码
     */
    private void adjustSortOrderForDrag(
            SysMenu entity, Integer targetIndex, Long subjectId, String projectNo) {
        // 查询同级所有节点（排除当前拖拽节点）
        List<SysMenu> siblings =
                sysMenuMapper.selectList(
                        Wrappers.<SysMenu>lambdaQuery()
                                .eq(SysMenu::getPid, entity.getPid())
                                .eq(SysMenu::getSubjectId, subjectId)
                                .eq(SysMenu::getProjectNo, projectNo)
                                .ne(SysMenu::getId, entity.getId())
                                .orderByAsc(SysMenu::getSortOrder));

        // 将拖拽节点插入到目标位置
        siblings.add(targetIndex, entity);

        // 批量更新所有节点的排序值（从0开始）
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).setSortOrder(i);
        }
        // 使用 MyBatis-Plus 批量更新
        if (!siblings.isEmpty()) {
            sysMenuMapper.updateById(siblings);
        }
    }

    /**
     * 获取指定父节点下的最大排序值
     *
     * @param pid 父节点ID
     * @param subjectId 主体ID
     * @param projectNo 应用编码
     * @return 最大排序值，无数据时返回null
     */
    private Integer getMaxSortOrderByPid(Long pid, Long subjectId, String projectNo) {
        List<SysMenu> menus =
                sysMenuMapper.selectList(
                        Wrappers.<SysMenu>lambdaQuery()
                                .eq(SysMenu::getPid, pid)
                                .eq(SysMenu::getSubjectId, subjectId)
                                .eq(SysMenu::getProjectNo, projectNo)
                                .orderByDesc(SysMenu::getSortOrder)
                                .last("LIMIT 1"));
        return menus.isEmpty() ? null : menus.get(0).getSortOrder();
    }

    /** 校验同级菜单名称是否重复 */
    private void checkDuplicateTitle(
            Long pid, Integer category, String title, Long subjectId, Long excludeId) {
        String projectNo = AppContext.getProjectNo();
        LambdaQueryWrapper<SysMenu> wrapper =
                new LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getPid, pid)
                        .eq(SysMenu::getCategory, category)
                        .eq(SysMenu::getTitle, title)
                        .eq(SysMenu::getSubjectId, subjectId)
                        .eq(SysMenu::getProjectNo, projectNo);
        if (excludeId != null) {
            wrapper.ne(SysMenu::getId, excludeId);
        }
        long count = sysMenuMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException("同级菜单名称已存在");
        }
    }

    /** 收集所有子节点ID（限定同主体、同应用） */
    private Set<Long> collectChildrenIds(Long parentId, Long subjectId, String projectNo) {
        Set<Long> result = new HashSet<>();
        List<SysMenu> children =
                sysMenuMapper.selectList(
                        Wrappers.<SysMenu>lambdaQuery()
                                .eq(SysMenu::getPid, parentId)
                                .eq(SysMenu::getSubjectId, subjectId)
                                .eq(SysMenu::getProjectNo, projectNo));
        for (SysMenu child : children) {
            result.add(child.getId());
            result.addAll(collectChildrenIds(child.getId(), subjectId, projectNo));
        }
        return result;
    }

    /** 检查是否形成环：targetPid 不能是 id 的子节点（包括自身） */
    private void checkCycle(Long id, Long targetPid, Long subjectId) {
        if (id.equals(targetPid)) {
            throw new BusinessException("不能将节点移动到自身或其子菜单下");
        }
        List<SysMenu> children =
                sysMenuMapper.selectList(
                        Wrappers.<SysMenu>lambdaQuery()
                                .eq(SysMenu::getPid, id)
                                .eq(SysMenu::getSubjectId, subjectId)
                                .eq(SysMenu::getProjectNo, AppContext.getProjectNo()));
        for (SysMenu child : children) {
            checkCycle(child.getId(), targetPid, subjectId);
        }
    }

    /**
     * 删除节点后重排同级节点的排序值
     *
     * @param pid 父节点ID
     * @param subjectId 主体ID
     * @param projectNo 应用编码
     */
    private void reorderSiblingsAfterDelete(Long pid, Long subjectId, String projectNo) {
        // 查询同级所有节点
        List<SysMenu> siblings =
                sysMenuMapper.selectList(
                        Wrappers.<SysMenu>lambdaQuery()
                                .eq(SysMenu::getPid, pid)
                                .eq(SysMenu::getSubjectId, subjectId)
                                .eq(SysMenu::getProjectNo, projectNo)
                                .orderByAsc(SysMenu::getSortOrder));

        if (siblings.isEmpty()) {
            return;
        }

        // 重新分配排序值（从0开始）
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).setSortOrder(i);
        }
        sysMenuMapper.updateById(siblings);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SysMenuResp getMenuById(Long Id) {
        SysMenu menu =
                sysMenuMapper.selectOne(
                        Wrappers.<SysMenu>lambdaQuery()
                                .eq(SysMenu::getId, Id)
                                .eq(SysMenu::getSubjectId, AppContext.getSubjectId())
                                .eq(SysMenu::getProjectNo, AppContext.getProjectNo()));
        if (menu == null) {
            throw new BusinessException("菜单不存在");
        }
        SysMenuResp resp = new SysMenuResp();
        BeanUtils.copyProperties(menu, resp);
        return resp;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SysMenuResp getMenuByParam(String param) {
        SysMenu menu =
                sysMenuMapper.selectOne(
                        Wrappers.<SysMenu>lambdaQuery()
                                .eq(SysMenu::getParam, param)
                                .eq(SysMenu::getSubjectId, AppContext.getSubjectId())
                                .eq(SysMenu::getProjectNo, AppContext.getProjectNo())
                                .last("LIMIT 1"));
        if (menu == null) {
            throw new BusinessException("菜单不存在");
        }
        SysMenuResp resp = new SysMenuResp();
        BeanUtils.copyProperties(menu, resp);
        return resp;
    }
}
