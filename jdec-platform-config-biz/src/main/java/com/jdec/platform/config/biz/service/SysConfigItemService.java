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
@DataSource(DataSourceConstants.CONFIG_CENTER)
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

    /**
     * 单节点配置项新增/编辑：id 为空则新增，否则编辑。
     *
     * <p>供整棵混合树保存接口（{@link SysConfigCategoryService#saveItem}）经代理调用，以保留 {@code @DataAudit} 审计。
     * 排序完全由前端整棵树决定，后端不再搬移同级节点，仅在前端未传排序值时追加到同级末尾。
     */
    @DataAudit(
            module = "系统设置",
            subModule = "通用配置-配置项管理",
            operation = OperationType.UPDATE,
            tableName = "sys_config_item",
            dataIdField = "#req.id")
    @Transactional
    public Long saveItemNode(SysConfigItemSaveReq req) {
        Long subjectId = AppContext.getSubjectId();
        String projectNo = AppContext.getProjectNo();
        Long pid = req.getPid() == null ? ROOT_PID : req.getPid();
        req.setPid(pid);

        // 校验：label 不能为空
        if (req.getLabel() == null || req.getLabel().trim().isEmpty()) {
            throw new BusinessException("配置项名称不能为空");
        }

        // 确定 categoryAlias：如果有父节点，从父节点继承；否则必须指定
        String categoryAlias = determineCategoryAlias(req, pid, subjectId, projectNo);

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
            calculatePathAndDepth(entity, pid, subjectId, projectNo);

            // 排序由前端决定：已传则按原值直接入库，为空则追加到同级末尾（后端不再搬移其它同级节点）
            if (req.getSort() == null) {
                Integer maxSort = getMaxSortByPid(categoryAlias, pid, subjectId, projectNo);
                entity.setSort(maxSort == null ? 0 : maxSort + 1);
            }

            itemMapper.insert(entity);
            // 设置id到请求对象，用于审计切面重新解析dataId
            req.setId(entity.getId());
            return entity.getId();
        } else {
            // 编辑
            SysConfigItem existing =
                    itemMapper.selectOne(
                            Wrappers.<SysConfigItem>lambdaQuery()
                                    .eq(SysConfigItem::getId, req.getId())
                                    .eq(SysConfigItem::getProjectNo, projectNo)
                                    .eq(SysConfigItem::getSubjectId, subjectId));
            if (existing == null) {
                throw new BusinessException("配置项不存在");
            }
            if (req.getId().equals(pid)) {
                throw new BusinessException("不能将自己设为父节点");
            }
            if (!pid.equals(ROOT_PID)) {
                checkCycle(req.getId(), pid, subjectId, projectNo);
            }

            // 校验：不能修改 categoryAlias（子级必须跟随父级）
            if (!existing.getCategoryAlias().equals(categoryAlias)) {
                throw new BusinessException("不能修改配置项的分类，子级必须跟随父级分类");
            }

            Long oldPid = existing.getPid();

            SysConfigItem entity = new SysConfigItem();
            BeanUtils.copyProperties(req, entity);
            entity.setCategoryAlias(categoryAlias);
            // subject_id / project_no 为归属标识，不允许前端覆盖，更新时以当前上下文为准并作为 WHERE 条件
            entity.setSubjectId(null);
            entity.setProjectNo(null);
            calculatePathAndDepth(entity, pid, subjectId, projectNo);

            // 排序由前端决定：父级变化且未传排序值时追加到新父级末尾；已传则按原值直接入库（后端不再搬移其它同级节点）
            if (req.getSort() == null && !pid.equals(oldPid)) {
                Integer maxSort = getMaxSortByPid(categoryAlias, pid, subjectId, projectNo);
                entity.setSort(maxSort == null ? 0 : maxSort + 1);
            }

            itemMapper.update(
                    entity,
                    Wrappers.<SysConfigItem>lambdaUpdate()
                            .eq(SysConfigItem::getId, req.getId())
                            .eq(SysConfigItem::getProjectNo, projectNo)
                            .eq(SysConfigItem::getSubjectId, subjectId));

            // 如果父节点变了，需要更新所有子节点的 path
            if (!existing.getPid().equals(pid)) {
                updateChildrenPath(req.getId(), subjectId, projectNo);
            }

            // 停用时级联停用所有子节点
            if (Integer.valueOf(0).equals(req.getStatus())
                    && !Integer.valueOf(0).equals(existing.getStatus())) {
                Set<Long> childIds = collectChildrenIds(req.getId(), subjectId, projectNo);
                if (!childIds.isEmpty()) {
                    itemMapper.update(
                            null,
                            Wrappers.<SysConfigItem>lambdaUpdate()
                                    .set(SysConfigItem::getStatus, 0)
                                    .in(SysConfigItem::getId, childIds)
                                    .eq(SysConfigItem::getProjectNo, projectNo)
                                    .eq(SysConfigItem::getSubjectId, subjectId));
                }
            }
            return null;
        }
    }

    private String determineCategoryAlias(
            SysConfigItemSaveReq req, Long pid, Long subjectId, String projectNo) {
        if (pid != null && !pid.equals(ROOT_PID)) {
            // 有父节点，从父节点继承 categoryAlias（父节点必须属于当前主体与应用）
            SysConfigItem parent =
                    itemMapper.selectOne(
                            Wrappers.<SysConfigItem>lambdaQuery()
                                    .eq(SysConfigItem::getId, pid)
                                    .eq(SysConfigItem::getProjectNo, projectNo)
                                    .eq(SysConfigItem::getSubjectId, subjectId));
            if (parent == null) {
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
        SysConfigItem item =
                itemMapper.selectOne(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .eq(SysConfigItem::getId, id)
                                .eq(SysConfigItem::getProjectNo, projectNo)
                                .eq(SysConfigItem::getSubjectId, subjectId));
        if (item == null) {
            throw new BusinessException("配置项不存在");
        }
        Set<Long> childIds = collectChildrenIds(id, subjectId, projectNo);
        int childCount = childIds.size();
        if (!force && childCount > 0) {
            throw new BusinessException("删除确认：该配置项下有 " + childCount + " 个子项将被同步删除；请确认后重新请求");
        }
        if (!childIds.isEmpty()) {
            itemMapper.delete(
                    Wrappers.<SysConfigItem>lambdaQuery()
                            .in(SysConfigItem::getId, childIds)
                            .eq(SysConfigItem::getProjectNo, projectNo)
                            .eq(SysConfigItem::getSubjectId, subjectId));
        }
        itemMapper.delete(
                Wrappers.<SysConfigItem>lambdaQuery()
                        .eq(SysConfigItem::getId, id)
                        .eq(SysConfigItem::getProjectNo, projectNo)
                        .eq(SysConfigItem::getSubjectId, subjectId));

        // 删除后重排同级节点的排序值
        reorderSiblingsAfterDelete(item.getCategoryAlias(), item.getPid(), subjectId, projectNo);
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

    private void calculatePathAndDepth(
            SysConfigItem entity, Long pid, Long subjectId, String projectNo) {
        if (pid == null || pid.equals(ROOT_PID)) {
            entity.setPath("/");
            entity.setDepth(1);
        } else {
            SysConfigItem parent =
                    itemMapper.selectOne(
                            Wrappers.<SysConfigItem>lambdaQuery()
                                    .eq(SysConfigItem::getId, pid)
                                    .eq(SysConfigItem::getProjectNo, projectNo)
                                    .eq(SysConfigItem::getSubjectId, subjectId));
            if (parent == null) {
                throw new BusinessException("父节点不存在");
            }
            entity.setPath(parent.getPath() + pid + "/");
            entity.setDepth(parent.getDepth() + 1);
        }
    }

    private void updateChildrenPath(Long parentId, Long subjectId, String projectNo) {
        List<SysConfigItem> children =
                itemMapper.selectList(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .eq(SysConfigItem::getPid, parentId)
                                .eq(SysConfigItem::getProjectNo, projectNo)
                                .eq(SysConfigItem::getSubjectId, subjectId));
        for (SysConfigItem child : children) {
            calculatePathAndDepth(child, parentId, subjectId, projectNo);
            itemMapper.update(
                    null,
                    Wrappers.<SysConfigItem>lambdaUpdate()
                            .set(SysConfigItem::getPath, child.getPath())
                            .set(SysConfigItem::getDepth, child.getDepth())
                            .eq(SysConfigItem::getId, child.getId())
                            .eq(SysConfigItem::getProjectNo, projectNo)
                            .eq(SysConfigItem::getSubjectId, subjectId));
            updateChildrenPath(child.getId(), subjectId, projectNo);
        }
    }

    private Set<Long> collectChildrenIds(Long parentId, Long subjectId, String projectNo) {
        Set<Long> result = new HashSet<>();
        List<SysConfigItem> children =
                itemMapper.selectList(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .eq(SysConfigItem::getPid, parentId)
                                .eq(SysConfigItem::getProjectNo, projectNo)
                                .eq(SysConfigItem::getSubjectId, subjectId));
        for (SysConfigItem child : children) {
            result.add(child.getId());
            result.addAll(collectChildrenIds(child.getId(), subjectId, projectNo));
        }
        return result;
    }

    private void checkCycle(Long id, Long targetPid, Long subjectId, String projectNo) {
        if (id.equals(targetPid)) {
            throw new BusinessException("不能将节点移动到自身或其子节点下");
        }
        List<SysConfigItem> children =
                itemMapper.selectList(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .eq(SysConfigItem::getPid, id)
                                .eq(SysConfigItem::getProjectNo, projectNo)
                                .eq(SysConfigItem::getSubjectId, subjectId));
        for (SysConfigItem child : children) {
            checkCycle(child.getId(), targetPid, subjectId, projectNo);
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
        SysConfigItem dragItem =
                itemMapper.selectOne(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .eq(SysConfigItem::getId, dragId)
                                .eq(SysConfigItem::getProjectNo, projectNo)
                                .eq(SysConfigItem::getSubjectId, subjectId));
        if (dragItem == null) {
            throw new BusinessException("被拖拽的配置项不存在");
        }

        // 校验：不能拖到自己下面
        if (dragId.equals(targetPid)) {
            throw new BusinessException("不能将节点拖拽到自身下");
        }

        // 校验：目标父节点必须是 item（不能是 category）
        if (!targetPid.equals(ROOT_PID)) {
            SysConfigItem targetParent =
                    itemMapper.selectOne(
                            Wrappers.<SysConfigItem>lambdaQuery()
                                    .eq(SysConfigItem::getId, targetPid)
                                    .eq(SysConfigItem::getProjectNo, projectNo)
                                    .eq(SysConfigItem::getSubjectId, subjectId));
            if (targetParent == null) {
                throw new BusinessException("目标父节点不存在");
            }

            // 校验：目标父节点必须与被拖拽节点属于同一分类
            if (!targetParent.getCategoryAlias().equals(dragItem.getCategoryAlias())) {
                throw new BusinessException("不能跨分类拖拽配置项");
            }

            // 校验：不能拖到自己的子节点下（避免循环依赖）
            checkCycle(dragId, targetPid, subjectId, projectNo);
        }

        // 更新被拖拽节点的父节点
        dragItem.setPid(targetPid);
        calculatePathAndDepth(dragItem, targetPid, subjectId, projectNo);

        // 处理排序值
        if (targetSort == null) {
            // 前端未传顺序值，自动分配为同级最大值+1
            Integer maxSort =
                    getMaxSortByPid(dragItem.getCategoryAlias(), targetPid, subjectId, projectNo);
            dragItem.setSort(maxSort == null ? 0 : maxSort + 1);
            itemMapper.update(
                    null,
                    Wrappers.<SysConfigItem>lambdaUpdate()
                            .set(SysConfigItem::getPid, dragItem.getPid())
                            .set(SysConfigItem::getPath, dragItem.getPath())
                            .set(SysConfigItem::getDepth, dragItem.getDepth())
                            .set(SysConfigItem::getSort, dragItem.getSort())
                            .eq(SysConfigItem::getId, dragItem.getId())
                            .eq(SysConfigItem::getProjectNo, projectNo)
                            .eq(SysConfigItem::getSubjectId, subjectId));
        } else {
            // 前端传了顺序值（从0开始），重新分配所有同级节点的排序
            adjustSortForDrag(dragItem, targetSort, subjectId, projectNo);
        }

        // 更新所有子节点的 path 和 depth
        updateChildrenPath(dragId, subjectId, projectNo);
    }

    /**
     * 处理拖拽时的排序调整
     *
     * @param entity 被拖拽的节点
     * @param targetIndex 目标位置的顺序值（从0开始）
     * @param subjectId 主体ID
     */
    private void adjustSortForDrag(
            SysConfigItem entity, Integer targetIndex, Long subjectId, String projectNo) {
        // 查询同级所有节点（排除当前拖拽节点）
        List<SysConfigItem> siblings =
                itemMapper.selectList(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .eq(SysConfigItem::getCategoryAlias, entity.getCategoryAlias())
                                .eq(SysConfigItem::getPid, entity.getPid())
                                .eq(SysConfigItem::getProjectNo, projectNo)
                                .eq(SysConfigItem::getSubjectId, subjectId)
                                .ne(SysConfigItem::getId, entity.getId())
                                .orderByAsc(SysConfigItem::getSort));

        // 将拖拽节点插入到目标位置
        siblings.add(targetIndex, entity);

        // 重新分配所有节点的排序值（从0开始），逐节点按主体与应用限定更新
        for (int i = 0; i < siblings.size(); i++) {
            SysConfigItem sibling = siblings.get(i);
            itemMapper.update(
                    null,
                    Wrappers.<SysConfigItem>lambdaUpdate()
                            .set(SysConfigItem::getSort, i)
                            .eq(SysConfigItem::getId, sibling.getId())
                            .eq(SysConfigItem::getProjectNo, projectNo)
                            .eq(SysConfigItem::getSubjectId, subjectId));
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
    private Integer getMaxSortByPid(
            String categoryAlias, Long pid, Long subjectId, String projectNo) {
        List<SysConfigItem> items =
                itemMapper.selectList(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .eq(SysConfigItem::getCategoryAlias, categoryAlias)
                                .eq(SysConfigItem::getPid, pid)
                                .eq(SysConfigItem::getProjectNo, projectNo)
                                .eq(SysConfigItem::getSubjectId, subjectId)
                                .orderByDesc(SysConfigItem::getSort)
                                .last("LIMIT 1"));
        return items.isEmpty() ? null : items.get(0).getSort();
    }

    /**
     * 删除节点后重排同级节点的排序值
     *
     * @param categoryAlias 分类别名
     * @param pid 父节点ID
     * @param subjectId 主体ID
     */
    private void reorderSiblingsAfterDelete(
            String categoryAlias, Long pid, Long subjectId, String projectNo) {
        // 查询同级所有节点
        List<SysConfigItem> siblings =
                itemMapper.selectList(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .eq(SysConfigItem::getCategoryAlias, categoryAlias)
                                .eq(SysConfigItem::getPid, pid)
                                .eq(SysConfigItem::getProjectNo, projectNo)
                                .eq(SysConfigItem::getSubjectId, subjectId)
                                .orderByAsc(SysConfigItem::getSort));

        if (siblings.isEmpty()) {
            return;
        }

        // 重新分配排序值（从0开始），逐节点按主体与应用限定更新
        for (int i = 0; i < siblings.size(); i++) {
            SysConfigItem sibling = siblings.get(i);
            itemMapper.update(
                    null,
                    Wrappers.<SysConfigItem>lambdaUpdate()
                            .set(SysConfigItem::getSort, i)
                            .eq(SysConfigItem::getId, sibling.getId())
                            .eq(SysConfigItem::getProjectNo, projectNo)
                            .eq(SysConfigItem::getSubjectId, subjectId));
        }
    }
}
