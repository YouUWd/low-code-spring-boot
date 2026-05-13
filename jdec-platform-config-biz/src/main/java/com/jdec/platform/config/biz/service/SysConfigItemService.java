package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.dto.request.SysConfigItemSaveReq;
import com.jdec.platform.config.api.dto.response.SysConfigItemTreeResp;
import com.jdec.platform.config.biz.entity.SysConfigCategory;
import com.jdec.platform.config.biz.entity.SysConfigItem;
import com.jdec.platform.config.biz.mapper.SysConfigCategoryMapper;
import com.jdec.platform.config.biz.mapper.SysConfigItemMapper;
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

    public List<SysConfigItemTreeResp> treeByCategory(Long categoryId) {
        List<SysConfigItem> list = queryItemsByCategory(categoryId, null);
        return buildTree(list);
    }

    public List<SysConfigItemTreeResp> treeByCategoryAlias(String categoryAlias) {
        List<SysConfigItem> list = queryItemsByCategory(null, categoryAlias);
        return buildTree(list);
    }

    public List<SysConfigItemTreeResp> listByCategory(Long categoryId) {
        List<SysConfigItem> list = queryItemsByCategory(categoryId, null);
        return list.stream().map(this::toResp).collect(Collectors.toList());
    }

    public List<SysConfigItemTreeResp> listByCategoryAlias(String categoryAlias) {
        List<SysConfigItem> list = queryItemsByCategory(null, categoryAlias);
        return list.stream().map(this::toResp).collect(Collectors.toList());
    }

    public void saveItem(SysConfigItemSaveReq req) {
        Long subjectId = AppContext.getSubjectId();
        Long pid = req.getPid() == null ? ROOT_PID : req.getPid();
        req.setPid(pid);

        // 校验：label 和 value 不能为空
        if (req.getLabel() == null || req.getLabel().trim().isEmpty()) {
            throw new BusinessException("配置项名称不能为空");
        }

        // 确定 categoryId：如果有父节点，从父节点继承；否则必须指定
        Long categoryId = determineCategoryId(req, pid, subjectId);
        req.setCategoryId(categoryId);

        // 校验分类是否存在
        SysConfigCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException("配置分类不存在");
        }

        // 校验：同一分类下，同一父节点下，label 不能重复
        checkDuplicateLabel(categoryId, pid, req.getLabel(), subjectId, req.getId());

        if (req.getId() == null) {
            // 新增
            SysConfigItem entity = new SysConfigItem();
            BeanUtils.copyProperties(req, entity);
            entity.setCategoryId(categoryId);
            entity.setCategoryAlias(category.getAlias());
            entity.setSubjectId(subjectId);
            calculatePathAndDepth(entity, pid);
            itemMapper.insert(entity);
        } else {
            // 编辑
            SysConfigItem existing = itemMapper.selectById(req.getId());
            if (existing == null || !existing.getSubjectId().equals(subjectId)) {
                throw new BusinessException("配置项不存在");
            }
            if (req.getId().equals(pid)) {
                throw new BusinessException("不能将自己设为父节点");
            }
            if (!pid.equals(ROOT_PID)) {
                checkCycle(req.getId(), pid, subjectId);
            }

            // 校验：不能修改 categoryId（子级必须跟随父级）
            if (!existing.getCategoryId().equals(categoryId)) {
                throw new BusinessException("不能修改配置项的分类，子级必须跟随父级分类");
            }

            SysConfigItem entity = new SysConfigItem();
            BeanUtils.copyProperties(req, entity);
            entity.setCategoryId(categoryId);
            entity.setCategoryAlias(category.getAlias());
            calculatePathAndDepth(entity, pid);
            itemMapper.updateById(entity);

            // 如果父节点变了，需要更新所有子节点的 path
            if (!existing.getPid().equals(pid)) {
                updateChildrenPath(req.getId(), subjectId);
            }
        }
    }

    /** 确定 categoryId：如果有父节点，从父节点继承；否则必须指定 */
    private Long determineCategoryId(SysConfigItemSaveReq req, Long pid, Long subjectId) {
        if (pid != null && !pid.equals(ROOT_PID)) {
            // 有父节点，从父节点继承 categoryId
            SysConfigItem parent = itemMapper.selectById(pid);
            if (parent == null || !parent.getSubjectId().equals(subjectId)) {
                throw new BusinessException("父节点不存在");
            }
            return parent.getCategoryId();
        } else {
            // 没有父节点，必须指定 categoryId
            if (req.getCategoryId() == null) {
                throw new BusinessException("根节点配置项必须指定分类");
            }
            return req.getCategoryId();
        }
    }

    /** 校验同一分类下、同一父节点下，label 是否重复 */
    private void checkDuplicateLabel(
            Long categoryId, Long pid, String label, Long subjectId, Long excludeId) {
        LambdaQueryWrapper<SysConfigItem> wrapper =
                new LambdaQueryWrapper<SysConfigItem>()
                        .eq(SysConfigItem::getCategoryId, categoryId)
                        .eq(SysConfigItem::getPid, pid)
                        .eq(SysConfigItem::getLabel, label)
                        .eq(SysConfigItem::getSubjectId, subjectId);
        if (excludeId != null) {
            wrapper.ne(SysConfigItem::getId, excludeId);
        }
        long count = itemMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException("同级配置项名称已存在");
        }
    }

    @Transactional
    public void deleteItem(Long id, boolean force) {
        Long subjectId = AppContext.getSubjectId();
        SysConfigItem item = itemMapper.selectById(id);
        if (item == null || !item.getSubjectId().equals(subjectId)) {
            throw new BusinessException("配置项不存在");
        }
        Set<Long> childIds = collectChildrenIds(id, subjectId);
        int childCount = childIds.size();
        if (!force && childCount > 0) {
            throw new BusinessException(
                    "删除确认：该配置项下有 " + childCount + " 个子项将被同步删除；请确认后携带 force=true 重新请求");
        }
        if (!childIds.isEmpty()) {
            itemMapper.deleteByIds(childIds);
        }
        itemMapper.deleteById(id);
    }

    private List<SysConfigItem> queryItemsByCategory(Long categoryId, String categoryAlias) {
        Long subjectId = AppContext.getSubjectId();
        LambdaQueryWrapper<SysConfigItem> wrapper =
                new LambdaQueryWrapper<SysConfigItem>()
                        .eq(SysConfigItem::getSubjectId, subjectId)
                        .orderByAsc(SysConfigItem::getSort)
                        .orderByAsc(SysConfigItem::getId);
        if (categoryId != null) {
            wrapper.eq(SysConfigItem::getCategoryId, categoryId);
        } else if (categoryAlias != null) {
            wrapper.eq(SysConfigItem::getCategoryAlias, categoryAlias);
        } else {
            throw new BusinessException("分类ID或alias至少提供一个");
        }
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
}
