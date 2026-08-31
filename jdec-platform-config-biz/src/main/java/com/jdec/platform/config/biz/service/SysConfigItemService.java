package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.dto.request.SysConfigItemDragReq;
import com.jdec.platform.config.api.dto.request.SysConfigItemSaveReq;
import com.jdec.platform.config.api.dto.response.SysConfigItemTreeResp;
import com.jdec.platform.config.biz.entity.SysConfigCategory;
import com.jdec.platform.config.biz.entity.SysConfigItem;
import com.jdec.platform.config.biz.mapper.SysConfigCategoryMapper;
import com.jdec.platform.config.biz.mapper.SysConfigItemMapper;
import com.jdec.platform.shared.audit.annotation.DataAudit;
import com.jdec.platform.shared.audit.enums.OperationType;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/** 配置项 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_ENGINE)
public class SysConfigItemService {

    private final SysConfigItemMapper itemMapper;
    private final SysConfigCategoryMapper categoryMapper;

    private static final Long ROOT_PID = 0L;

    /**
     * 查询配置项（按分类ID）
     *
     * <p>根据分类的 format 字段自动判定返回树形或列表结构
     */
    public List<SysConfigItemTreeResp> getItemsByCategory(Long categoryId) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        SysConfigCategory category =
                categoryMapper.selectOne(
                        Wrappers.<SysConfigCategory>lambdaQuery()
                                .eq(SysConfigCategory::getId, categoryId)
                                .eq(SysConfigCategory::getProjectNo, projectNo)
                                .eq(SysConfigCategory::getSubjectId, subjectId));
        if (category == null) {
            throw new BusinessException("配置分类不存在");
        }

        List<SysConfigItem> list = queryItemsByCategory(categoryId, null);
        return "tree".equals(category.getFormat()) ? buildTree(list) : toRespList(list);
    }

    /**
     * 查询配置项（按分类alias）
     *
     * <p>根据分类的 format 字段自动判定返回树形或列表结构
     */
    public List<SysConfigItemTreeResp> getItemsByCategoryAlias(String categoryAlias) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        SysConfigCategory category =
                categoryMapper.selectOne(
                        Wrappers.<SysConfigCategory>lambdaQuery()
                                .eq(SysConfigCategory::getCategoryAlias, categoryAlias)
                                .eq(SysConfigCategory::getProjectNo, projectNo)
                                .eq(SysConfigCategory::getSubjectId, subjectId));
        if (category == null) {
            throw new BusinessException("配置分类不存在");
        }

        List<SysConfigItem> list = queryItemsByCategory(null, categoryAlias);
        return "tree".equals(category.getFormat()) ? buildTree(list) : toRespList(list);
    }

    private List<SysConfigItemTreeResp> toRespList(List<SysConfigItem> list) {
        return list.stream().map(this::toResp).collect(Collectors.toList());
    }

    @DataAudit(
            module = "系统设置",
            subModule = "通用配置-配置项管理",
            operation = OperationType.UPDATE,
            tableName = "sys_config_item",
            dataIdField = "#req.id")
    @Transactional
    public Long saveItem(SysConfigItemSaveReq req) {
        Long subjectId = AppContext.getSubjectId();
        String projectNo = AppContext.getProjectNo();
        Long pid = req.getPid() == null ? ROOT_PID : req.getPid();
        req.setPid(pid);

        // 校验：label 不能为空
        if (req.getLabel() == null || req.getLabel().trim().isEmpty()) {
            throw new BusinessException("配置项名称不能为空");
        }

        // 确定 categoryAlias：如果有父节点，从父节点继承；否则必须指定
        String categoryAlias = determineCategoryAlias(req, pid, subjectId);

        // 校验分类是否存在
        SysConfigCategory category =
                categoryMapper.selectOne(
                        Wrappers.<SysConfigCategory>lambdaQuery()
                                .eq(SysConfigCategory::getCategoryAlias, categoryAlias)
                                .eq(SysConfigCategory::getProjectNo, projectNo)
                                .eq(SysConfigCategory::getSubjectId, subjectId));
        if (category == null) {
            throw new BusinessException("配置分类不存在");
        }

        // 校验：同一分类下，同一父节点下，label 不能重复
        checkDuplicateLabel(categoryAlias, pid, req.getLabel(), subjectId, projectNo, req.getId());

        if (req.getId() == null) {
            // 新增
            SysConfigItem entity = new SysConfigItem();
            BeanUtils.copyProperties(req, entity);
            entity.setCategoryAlias(categoryAlias);
            entity.setSubjectId(subjectId);
            entity.setProjectNo(projectNo);
            calculatePathAndDepth(entity, pid);

            // 处理排序值
            if (req.getSort() == null) {
                // 前端未传排序值，自动设置为同级最大值+1
                Integer maxSort = getMaxSortByPid(categoryAlias, pid, subjectId);
                entity.setSort(maxSort == null ? 0 : maxSort + 1);
            } else {
                // 前端传了排序值，检查是否与同级冲突
                boolean sortExists =
                        itemMapper.exists(
                                Wrappers.<SysConfigItem>lambdaQuery()
                                        .eq(SysConfigItem::getCategoryAlias, categoryAlias)
                                        .eq(SysConfigItem::getPid, pid)
                                        .eq(SysConfigItem::getSort, req.getSort())
                                        .eq(SysConfigItem::getSubjectId, subjectId));
                if (sortExists) {
                    // 排序值冲突，将该位置及之后的节点排序值+1
                    adjustSortOnInsert(categoryAlias, pid, req.getSort(), subjectId);
                }
            }

            itemMapper.insert(entity);
            // 设置id到请求对象，用于审计切面重新解析dataId
            req.setId(entity.getId());
            return entity.getId();
        } else {
            // 编辑
            SysConfigItem existing = itemMapper.selectById(req.getId());
            if (existing == null
                    || !existing.getSubjectId().equals(subjectId)
                    || !existing.getProjectNo().equals(projectNo)) {
                throw new BusinessException("配置项不存在");
            }
            if (req.getId().equals(pid)) {
                throw new BusinessException("不能将自己设为父节点");
            }
            if (!pid.equals(ROOT_PID)) {
                checkCycle(req.getId(), pid, subjectId);
            }

            // 校验：不能修改 categoryAlias（子级必须跟随父级）
            if (!existing.getCategoryAlias().equals(categoryAlias)) {
                throw new BusinessException("不能修改配置项的分类，子级必须跟随父级分类");
            }

            Long oldPid = existing.getPid();
            Integer oldSort = existing.getSort();

            SysConfigItem entity = new SysConfigItem();
            BeanUtils.copyProperties(req, entity);
            entity.setCategoryAlias(categoryAlias);
            entity.setProjectNo(projectNo);
            calculatePathAndDepth(entity, pid);

            // 处理排序值
            if (req.getSort() == null) {
                // 前端未传排序值
                if (!pid.equals(oldPid)) {
                    // 父节点变化，设置为新父节点下的最大值+1
                    Integer maxSort = getMaxSortByPid(categoryAlias, pid, subjectId);
                    entity.setSort(maxSort == null ? 0 : maxSort + 1);
                }
                // 父节点未变化，保持原排序值
            } else {
                // 前端传了排序值
                if (pid.equals(oldPid)) {
                    // 同父节点内，排序值变化
                    if (!req.getSort().equals(oldSort)) {
                        boolean sortExists =
                                itemMapper.exists(
                                        Wrappers.<SysConfigItem>lambdaQuery()
                                                .eq(SysConfigItem::getCategoryAlias, categoryAlias)
                                                .eq(SysConfigItem::getPid, pid)
                                                .eq(SysConfigItem::getSort, req.getSort())
                                                .eq(SysConfigItem::getSubjectId, subjectId)
                                                .ne(SysConfigItem::getId, req.getId()));
                        if (sortExists) {
                            // 排序值冲突，调整其他节点
                            adjustSortOnUpdate(
                                    req.getId(),
                                    categoryAlias,
                                    pid,
                                    req.getSort(),
                                    oldSort,
                                    subjectId);
                        }
                    }
                } else {
                    // 跨父节点，检查新父节点下是否有冲突
                    boolean sortExists =
                            itemMapper.exists(
                                    Wrappers.<SysConfigItem>lambdaQuery()
                                            .eq(SysConfigItem::getCategoryAlias, categoryAlias)
                                            .eq(SysConfigItem::getPid, pid)
                                            .eq(SysConfigItem::getSort, req.getSort())
                                            .eq(SysConfigItem::getSubjectId, subjectId));
                    if (sortExists) {
                        // 新父节点下排序值冲突，调整
                        adjustSortOnInsert(categoryAlias, pid, req.getSort(), subjectId);
                    }
                }
            }

            itemMapper.updateById(entity);

            // 如果父节点变了，需要更新所有子节点的 path
            if (!existing.getPid().equals(pid)) {
                updateChildrenPath(req.getId(), subjectId);
            }

            // 停用时级联停用所有子节点
            if (Integer.valueOf(0).equals(req.getStatus())
                    && !Integer.valueOf(0).equals(existing.getStatus())) {
                Set<Long> childIds = collectChildrenIds(req.getId(), subjectId);
                if (!childIds.isEmpty()) {
                    itemMapper.update(
                            null,
                            Wrappers.<SysConfigItem>lambdaUpdate()
                                    .set(SysConfigItem::getStatus, 0)
                                    .in(SysConfigItem::getId, childIds));
                }
            }
            return null;
        }
    }

    private String determineCategoryAlias(SysConfigItemSaveReq req, Long pid, Long subjectId) {
        if (pid != null && !pid.equals(ROOT_PID)) {
            // 有父节点，从父节点继承 categoryAlias
            SysConfigItem parent = itemMapper.selectById(pid);
            if (parent == null || !parent.getSubjectId().equals(subjectId)) {
                throw new BusinessException("父节点不存在");
            }
            return parent.getCategoryAlias();
        } else {
            // 没有父节点，必须指定 categoryAlias
            if (req.getCategoryAlias() == null || req.getCategoryAlias().trim().isEmpty()) {
                throw new BusinessException("根节点配置项必须指定分类别名");
            }
            return req.getCategoryAlias();
        }
    }

    /** 校验同一分类下、同一父节点下，label 是否重复 */
    private void checkDuplicateLabel(
            String categoryAlias,
            Long pid,
            String label,
            Long subjectId,
            String projectNo,
            Long excludeId) {
        LambdaQueryWrapper<SysConfigItem> wrapper =
                new LambdaQueryWrapper<SysConfigItem>()
                        .eq(SysConfigItem::getCategoryAlias, categoryAlias)
                        .eq(SysConfigItem::getPid, pid)
                        .eq(SysConfigItem::getLabel, label)
                        .eq(SysConfigItem::getSubjectId, subjectId)
                        .eq(SysConfigItem::getProjectNo, projectNo);
        if (excludeId != null) {
            wrapper.ne(SysConfigItem::getId, excludeId);
        }
        long count = itemMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException("同级配置项名称已存在");
        }
    }

    @DataAudit(
            module = "系统设置",
            subModule = "通用配置-配置项管理",
            operation = OperationType.DELETE,
            deleteDisplayField = "label",
            entityClass = SysConfigItem.class,
            tableName = "sys_config_item",
            dataIdField = "#id")
    @Transactional
    public void deleteItem(Long id, boolean force) {
        Long subjectId = AppContext.getSubjectId();
        String projectNo = AppContext.getProjectNo();
        SysConfigItem item = itemMapper.selectById(id);
        if (item == null
                || !item.getSubjectId().equals(subjectId)
                || !item.getProjectNo().equals(projectNo)) {
            throw new BusinessException("配置项不存在");
        }
        Set<Long> childIds = collectChildrenIds(id, subjectId);
        int childCount = childIds.size();
        if (!force && childCount > 0) {
            throw new BusinessException("删除确认：该配置项下有 " + childCount + " 个子项将被同步删除；请确认后重新请求");
        }
        if (!childIds.isEmpty()) {
            itemMapper.deleteByIds(childIds);
        }
        itemMapper.deleteById(id);

        // 删除后重排同级节点的排序值
        reorderSiblingsAfterDelete(item.getCategoryAlias(), item.getPid(), subjectId);
    }

    private List<SysConfigItem> queryItemsByCategory(Long categoryId, String categoryAlias) {
        Long subjectId = AppContext.getSubjectId();
        String projectNo = AppContext.getProjectNo();

        // 如果传入 categoryId，需要先查询对应的 alias
        if (categoryId != null) {
            SysConfigCategory category =
                    categoryMapper.selectOne(
                            Wrappers.<SysConfigCategory>lambdaQuery()
                                    .eq(SysConfigCategory::getId, categoryId)
                                    .eq(SysConfigCategory::getProjectNo, projectNo)
                                    .eq(SysConfigCategory::getSubjectId, subjectId));
            if (category == null) {
                throw new BusinessException("配置分类不存在");
            }
            categoryAlias = category.getCategoryAlias();
        } else if (categoryAlias == null || categoryAlias.trim().isEmpty()) {
            throw new BusinessException("分类ID或alias至少提供一个");
        }

        LambdaQueryWrapper<SysConfigItem> wrapper =
                new LambdaQueryWrapper<SysConfigItem>()
                        .eq(SysConfigItem::getCategoryAlias, categoryAlias)
                        .eq(SysConfigItem::getSubjectId, subjectId)
                        .eq(SysConfigItem::getProjectNo, projectNo)
                        .orderByAsc(SysConfigItem::getSort)
                        .orderByAsc(SysConfigItem::getId);
        return itemMapper.selectList(wrapper);
    }

    private List<SysConfigItemTreeResp> buildTree(List<SysConfigItem> list) {
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        Map<Long, SysConfigItemTreeResp> nodeMap = new LinkedHashMap<>();
        for (SysConfigItem item : list) {
            SysConfigItemTreeResp resp = toResp(item);
            resp.setChildren(new ArrayList<>());
            nodeMap.put(item.getId(), resp);
        }
        List<SysConfigItemTreeResp> tree = new ArrayList<>();
        for (SysConfigItem item : list) {
            SysConfigItemTreeResp node = nodeMap.get(item.getId());
            Long pid = item.getPid();
            if (pid == null || pid.equals(ROOT_PID)) {
                tree.add(node);
            } else {
                SysConfigItemTreeResp parent = nodeMap.get(pid);
                if (parent != null && parent.getChildren() != null) {
                    parent.getChildren().add(node);
                } else {
                    tree.add(node);
                }
            }
        }
        return tree;
    }

    private SysConfigItemTreeResp toResp(SysConfigItem item) {
        SysConfigItemTreeResp resp = new SysConfigItemTreeResp();
        BeanUtils.copyProperties(item, resp);
        return resp;
    }

    private void calculatePathAndDepth(SysConfigItem entity, Long pid) {
        if (pid == null || pid.equals(ROOT_PID)) {
            entity.setPath("/");
            entity.setDepth(1);
        } else {
            SysConfigItem parent = itemMapper.selectById(pid);
            if (parent == null) {
                throw new BusinessException("父节点不存在");
            }
            entity.setPath(parent.getPath() + pid + "/");
            entity.setDepth(parent.getDepth() + 1);
        }
    }

    private void updateChildrenPath(Long parentId, Long subjectId) {
        List<SysConfigItem> children =
                itemMapper.selectList(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .eq(SysConfigItem::getPid, parentId)
                                .eq(SysConfigItem::getSubjectId, subjectId));
        for (SysConfigItem child : children) {
            calculatePathAndDepth(child, parentId);
            itemMapper.updateById(child);
            updateChildrenPath(child.getId(), subjectId);
        }
    }

    private Set<Long> collectChildrenIds(Long parentId, Long subjectId) {
        Set<Long> result = new HashSet<>();
        List<SysConfigItem> children =
                itemMapper.selectList(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .eq(SysConfigItem::getPid, parentId)
                                .eq(SysConfigItem::getSubjectId, subjectId));
        for (SysConfigItem child : children) {
            result.add(child.getId());
            result.addAll(collectChildrenIds(child.getId(), subjectId));
        }
        return result;
    }

    private void checkCycle(Long id, Long targetPid, Long subjectId) {
        if (id.equals(targetPid)) {
            throw new BusinessException("不能将节点移动到自身或其子节点下");
        }
        List<SysConfigItem> children =
                itemMapper.selectList(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .eq(SysConfigItem::getPid, id)
                                .eq(SysConfigItem::getSubjectId, subjectId));
        for (SysConfigItem child : children) {
            checkCycle(child.getId(), targetPid, subjectId);
        }
    }

    /**
     * 拖拽配置项
     *
     * <p>限制：item 不能拖到 category 层级，只能在 item 之间拖拽
     */
    @Transactional
    public void dragItem(SysConfigItemDragReq req) {
        Long subjectId = AppContext.getSubjectId();
        String projectNo = AppContext.getProjectNo();
        Long dragId = req.getDragId();
        Long targetPid = req.getTargetPid() == null ? ROOT_PID : req.getTargetPid();
        Integer targetSort = req.getTargetSort();

        // 校验：被拖拽节点必须存在
        SysConfigItem dragItem = itemMapper.selectById(dragId);
        if (dragItem == null
                || !dragItem.getSubjectId().equals(subjectId)
                || !dragItem.getProjectNo().equals(projectNo)) {
            throw new BusinessException("被拖拽的配置项不存在");
        }

        // 校验：不能拖到自己下面
        if (dragId.equals(targetPid)) {
            throw new BusinessException("不能将节点拖拽到自身下");
        }

        // 校验：目标父节点必须是 item（不能是 category）
        if (!targetPid.equals(ROOT_PID)) {
            SysConfigItem targetParent = itemMapper.selectById(targetPid);
            if (targetParent == null
                    || !targetParent.getSubjectId().equals(subjectId)
                    || !targetParent.getProjectNo().equals(projectNo)) {
                throw new BusinessException("目标父节点不存在");
            }

            // 校验：目标父节点必须与被拖拽节点属于同一分类
            if (!targetParent.getCategoryAlias().equals(dragItem.getCategoryAlias())) {
                throw new BusinessException("不能跨分类拖拽配置项");
            }

            // 校验：不能拖到自己的子节点下（避免循环依赖）
            checkCycle(dragId, targetPid, subjectId);
        }

        // 更新被拖拽节点的父节点
        dragItem.setPid(targetPid);
        calculatePathAndDepth(dragItem, targetPid);

        // 处理排序值
        if (targetSort == null) {
            // 前端未传顺序值，自动分配为同级最大值+1
            Integer maxSort = getMaxSortByPid(dragItem.getCategoryAlias(), targetPid, subjectId);
            dragItem.setSort(maxSort == null ? 0 : maxSort + 1);
            itemMapper.updateById(dragItem);
        } else {
            // 前端传了顺序值（从0开始），重新分配所有同级节点的排序
            adjustSortForDrag(dragItem, targetSort, subjectId);
        }

        // 更新所有子节点的 path 和 depth
        updateChildrenPath(dragId, subjectId);
    }

    /**
     * 处理拖拽时的排序调整
     *
     * @param entity 被拖拽的节点
     * @param targetIndex 目标位置的顺序值（从0开始）
     * @param subjectId 主体ID
     */
    private void adjustSortForDrag(SysConfigItem entity, Integer targetIndex, Long subjectId) {
        // 查询同级所有节点（排除当前拖拽节点）
        List<SysConfigItem> siblings =
                itemMapper.selectList(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .eq(SysConfigItem::getCategoryAlias, entity.getCategoryAlias())
                                .eq(SysConfigItem::getPid, entity.getPid())
                                .eq(SysConfigItem::getSubjectId, subjectId)
                                .ne(SysConfigItem::getId, entity.getId())
                                .orderByAsc(SysConfigItem::getSort));

        // 将拖拽节点插入到目标位置
        siblings.add(targetIndex, entity);

        // 批量更新所有节点的排序值（从0开始）
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).setSort(i);
        }
        // 使用 MyBatis-Plus 批量更新
        if (!siblings.isEmpty()) {
            itemMapper.updateById(siblings);
        }
    }

    /**
     * 获取指定分类、指定父节点下的最大排序值
     *
     * @param categoryAlias 分类别名
     * @param pid 父节点ID
     * @param subjectId 主体ID
     * @return 最大排序值，无数据时返回null
     */
    private Integer getMaxSortByPid(String categoryAlias, Long pid, Long subjectId) {
        List<SysConfigItem> items =
                itemMapper.selectList(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .eq(SysConfigItem::getCategoryAlias, categoryAlias)
                                .eq(SysConfigItem::getPid, pid)
                                .eq(SysConfigItem::getSubjectId, subjectId)
                                .orderByDesc(SysConfigItem::getSort)
                                .last("LIMIT 1"));
        return items.isEmpty() ? null : items.get(0).getSort();
    }

    /**
     * 新增节点时调整排序值：将指定位置及之后的节点排序值+1
     *
     * @param categoryAlias 分类别名
     * @param pid 父节点ID
     * @param insertSort 插入位置的排序值
     * @param subjectId 主体ID
     */
    private void adjustSortOnInsert(
            String categoryAlias, Long pid, Integer insertSort, Long subjectId) {
        List<SysConfigItem> siblings =
                itemMapper.selectList(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .eq(SysConfigItem::getCategoryAlias, categoryAlias)
                                .eq(SysConfigItem::getPid, pid)
                                .eq(SysConfigItem::getSubjectId, subjectId)
                                .ge(SysConfigItem::getSort, insertSort)
                                .orderByAsc(SysConfigItem::getSort));
        for (SysConfigItem sibling : siblings) {
            sibling.setSort(sibling.getSort() + 1);
            itemMapper.updateById(sibling);
        }
    }

    /**
     * 更新节点时调整排序值（同父节点内）
     *
     * @param itemId 被更新节点ID
     * @param categoryAlias 分类别名
     * @param pid 父节点ID
     * @param newSort 新排序值
     * @param oldSort 原排序值
     * @param subjectId 主体ID
     */
    private void adjustSortOnUpdate(
            Long itemId,
            String categoryAlias,
            Long pid,
            Integer newSort,
            Integer oldSort,
            Long subjectId) {
        if (oldSort == null) {
            // 原来没有排序值，按插入处理
            adjustSortOnInsert(categoryAlias, pid, newSort, subjectId);
            return;
        }

        // 向前移动：原位置之后、新位置之前的节点 sort + 1
        if (newSort < oldSort) {
            List<SysConfigItem> siblings =
                    itemMapper.selectList(
                            Wrappers.<SysConfigItem>lambdaQuery()
                                    .eq(SysConfigItem::getCategoryAlias, categoryAlias)
                                    .eq(SysConfigItem::getPid, pid)
                                    .eq(SysConfigItem::getSubjectId, subjectId)
                                    .ne(SysConfigItem::getId, itemId)
                                    .ge(SysConfigItem::getSort, newSort)
                                    .lt(SysConfigItem::getSort, oldSort));
            for (SysConfigItem sibling : siblings) {
                sibling.setSort(sibling.getSort() + 1);
                itemMapper.updateById(sibling);
            }
        }
        // 向后移动：原位置之前、新位置之后的节点 sort - 1
        else if (newSort > oldSort) {
            List<SysConfigItem> siblings =
                    itemMapper.selectList(
                            Wrappers.<SysConfigItem>lambdaQuery()
                                    .eq(SysConfigItem::getCategoryAlias, categoryAlias)
                                    .eq(SysConfigItem::getPid, pid)
                                    .eq(SysConfigItem::getSubjectId, subjectId)
                                    .ne(SysConfigItem::getId, itemId)
                                    .gt(SysConfigItem::getSort, oldSort)
                                    .le(SysConfigItem::getSort, newSort));
            for (SysConfigItem sibling : siblings) {
                sibling.setSort(sibling.getSort() - 1);
                itemMapper.updateById(sibling);
            }
        }
    }

    /**
     * 删除节点后重排同级节点的排序值
     *
     * @param categoryAlias 分类别名
     * @param pid 父节点ID
     * @param subjectId 主体ID
     */
    private void reorderSiblingsAfterDelete(String categoryAlias, Long pid, Long subjectId) {
        // 查询同级所有节点
        List<SysConfigItem> siblings =
                itemMapper.selectList(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .eq(SysConfigItem::getCategoryAlias, categoryAlias)
                                .eq(SysConfigItem::getPid, pid)
                                .eq(SysConfigItem::getSubjectId, subjectId)
                                .orderByAsc(SysConfigItem::getSort));

        if (siblings.isEmpty()) {
            return;
        }

        // 重新分配排序值（从0开始）
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).setSort(i);
        }
        itemMapper.updateById(siblings);
    }
}
