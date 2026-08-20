package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysMenuApi;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/** 系统菜单 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysMenuService implements SysMenuApi {

    private final SysMenuMapper sysMenuMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final ReferenceCheckManager referenceCheckManager;

    /** 菜单类型：导航 */
    private static final Integer MENU_TYPE_NAV = 1;

    /** 顶层父级ID */
    private static final Long ROOT_PID = 0L;

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

    @DataAudit(
            module = "系统设置",
            subModule = "菜单配置",
            subModuleField = "#req.category",
            subModuleEnumClass = MenuCategoryEnum.class,
            operation = OperationType.UPDATE,
            tableName = "sys_menu",
            dataIdField = "#req.id")
    @Transactional
    @Override
    public SysMenuResp saveMenu(SysMenuSaveReq req) {
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

            // 处理排序值
            if (req.getSortOrder() == null) {
                // 前端未传排序值，自动设置为同级最大值+1
                Integer maxSortOrder = getMaxSortOrderByPid(pid, subjectId, projectNo);
                entity.setSortOrder(maxSortOrder == null ? 0 : maxSortOrder + 1);
            } else {
                // 前端传了排序值，检查是否与同级冲突
                boolean sortExists =
                        sysMenuMapper.exists(
                                Wrappers.<SysMenu>lambdaQuery()
                                        .eq(SysMenu::getPid, pid)
                                        .eq(SysMenu::getSortOrder, req.getSortOrder())
                                        .eq(SysMenu::getSubjectId, subjectId)
                                        .eq(SysMenu::getProjectNo, projectNo));
                if (sortExists) {
                    // 排序值冲突，将该位置及之后的节点排序值+1
                    adjustSortOrderOnInsert(pid, req.getSortOrder(), subjectId, projectNo);
                }
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
            Integer oldSortOrder = oldEntity.getSortOrder();

            entity = new SysMenu();
            BeanUtils.copyProperties(req, entity);

            // 处理排序值
            if (req.getSortOrder() == null) {
                // 前端未传排序值
                if (!pid.equals(oldPid)) {
                    // 父节点变化，设置为新父节点下的最大值+1
                    Integer maxSortOrder = getMaxSortOrderByPid(pid, subjectId, projectNo);
                    entity.setSortOrder(maxSortOrder == null ? 0 : maxSortOrder + 1);
                }
                // 父节点未变化，保持原排序值
            } else {
                // 前端传了排序值
                if (pid.equals(oldPid)) {
                    // 同父节点内，排序值变化
                    if (!req.getSortOrder().equals(oldSortOrder)) {
                        boolean sortExists =
                                sysMenuMapper.exists(
                                        Wrappers.<SysMenu>lambdaQuery()
                                                .eq(SysMenu::getPid, pid)
                                                .eq(SysMenu::getSortOrder, req.getSortOrder())
                                                .eq(SysMenu::getSubjectId, subjectId)
                                                .eq(SysMenu::getProjectNo, projectNo)
                                                .ne(SysMenu::getId, req.getId()));
                        if (sortExists) {
                            // 排序值冲突，调整其他节点
                            adjustSortOrderOnUpdate(
                                    req.getId(),
                                    pid,
                                    req.getSortOrder(),
                                    oldSortOrder,
                                    subjectId,
                                    projectNo);
                        }
                    }
                } else {
                    // 跨父节点，检查新父节点下是否有冲突
                    boolean sortExists =
                            sysMenuMapper.exists(
                                    Wrappers.<SysMenu>lambdaQuery()
                                            .eq(SysMenu::getPid, pid)
                                            .eq(SysMenu::getSortOrder, req.getSortOrder())
                                            .eq(SysMenu::getSubjectId, subjectId)
                                            .eq(SysMenu::getProjectNo, projectNo));
                    if (sortExists) {
                        // 新父节点下排序值冲突，调整
                        adjustSortOrderOnInsert(pid, req.getSortOrder(), subjectId, projectNo);
                    }
                }
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

    /**
     * 新增节点时调整排序值：将指定位置及之后的节点排序值+1
     *
     * @param pid 父节点ID
     * @param insertSortOrder 插入位置的排序值
     * @param subjectId 主体ID
     * @param projectNo 应用编码
     */
    private void adjustSortOrderOnInsert(
            Long pid, Integer insertSortOrder, Long subjectId, String projectNo) {
        List<SysMenu> siblings =
                sysMenuMapper.selectList(
                        Wrappers.<SysMenu>lambdaQuery()
                                .eq(SysMenu::getPid, pid)
                                .eq(SysMenu::getSubjectId, subjectId)
                                .eq(SysMenu::getProjectNo, projectNo)
                                .ge(SysMenu::getSortOrder, insertSortOrder)
                                .orderByAsc(SysMenu::getSortOrder));
        for (SysMenu sibling : siblings) {
            sibling.setSortOrder(sibling.getSortOrder() + 1);
            sysMenuMapper.updateById(sibling);
        }
    }

    /**
     * 更新节点时调整排序值（同父节点内）
     *
     * @param menuId 被更新节点ID
     * @param pid 父节点ID
     * @param newSortOrder 新排序值
     * @param oldSortOrder 原排序值
     * @param subjectId 主体ID
     * @param projectNo 应用编码
     */
    private void adjustSortOrderOnUpdate(
            Long menuId,
            Long pid,
            Integer newSortOrder,
            Integer oldSortOrder,
            Long subjectId,
            String projectNo) {
        if (oldSortOrder == null) {
            // 原来没有排序值，按插入处理
            adjustSortOrderOnInsert(pid, newSortOrder, subjectId, projectNo);
            return;
        }

        // 向前移动：原位置之后、新位置之前的节点 sortOrder + 1
        if (newSortOrder < oldSortOrder) {
            List<SysMenu> siblings =
                    sysMenuMapper.selectList(
                            Wrappers.<SysMenu>lambdaQuery()
                                    .eq(SysMenu::getPid, pid)
                                    .eq(SysMenu::getSubjectId, subjectId)
                                    .eq(SysMenu::getProjectNo, projectNo)
                                    .ne(SysMenu::getId, menuId)
                                    .ge(SysMenu::getSortOrder, newSortOrder)
                                    .lt(SysMenu::getSortOrder, oldSortOrder));
            for (SysMenu sibling : siblings) {
                sibling.setSortOrder(sibling.getSortOrder() + 1);
                sysMenuMapper.updateById(sibling);
            }
        }
        // 向后移动：原位置之前、新位置之后的节点 sortOrder - 1
        else if (newSortOrder > oldSortOrder) {
            List<SysMenu> siblings =
                    sysMenuMapper.selectList(
                            Wrappers.<SysMenu>lambdaQuery()
                                    .eq(SysMenu::getPid, pid)
                                    .eq(SysMenu::getSubjectId, subjectId)
                                    .eq(SysMenu::getProjectNo, projectNo)
                                    .ne(SysMenu::getId, menuId)
                                    .gt(SysMenu::getSortOrder, oldSortOrder)
                                    .le(SysMenu::getSortOrder, newSortOrder));
            for (SysMenu sibling : siblings) {
                sibling.setSortOrder(sibling.getSortOrder() - 1);
                sysMenuMapper.updateById(sibling);
            }
        }
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
