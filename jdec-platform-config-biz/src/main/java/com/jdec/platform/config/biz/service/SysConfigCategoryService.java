package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysConfigCategoryApi;
import com.jdec.platform.config.api.dto.request.SysConfigCategorySaveReq;
import com.jdec.platform.config.api.dto.request.SysConfigItemSaveReq;
import com.jdec.platform.config.api.dto.response.SysConfigCategoryResp;
import com.jdec.platform.config.api.dto.response.SysConfigItemTreeResp;
import com.jdec.platform.config.api.dto.response.SysConfigTreeNodeResp;
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

/** 配置分类 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysConfigCategoryService implements SysConfigCategoryApi {

    private final SysConfigCategoryMapper categoryMapper;
    private final SysConfigItemMapper itemMapper;
    private final SysConfigItemService itemService;

    @Override
    public List<SysConfigCategoryResp> list(Long moduleId) {
        String projectNo = AppContext.getProjectNo();
        LambdaQueryWrapper<SysConfigCategory> wrapper =
                new LambdaQueryWrapper<SysConfigCategory>()
                        .eq(SysConfigCategory::getProjectNo, projectNo)
                        .orderByAsc(SysConfigCategory::getSort)
                        .orderByAsc(SysConfigCategory::getId);
        if (moduleId != null) {
            wrapper.eq(SysConfigCategory::getModuleId, moduleId);
        }
        List<SysConfigCategory> list = categoryMapper.selectList(wrapper);
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream()
                .map(
                        entity -> {
                            SysConfigCategoryResp resp = new SysConfigCategoryResp();
                            BeanUtils.copyProperties(entity, resp);
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public List<SysConfigTreeNodeResp> mixedTree(Long moduleId) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();

        // 查询所有分类
        LambdaQueryWrapper<SysConfigCategory> categoryWrapper =
                new LambdaQueryWrapper<SysConfigCategory>()
                        .eq(SysConfigCategory::getProjectNo, projectNo)
                        .orderByAsc(SysConfigCategory::getSort)
                        .orderByAsc(SysConfigCategory::getId);
        if (moduleId != null) {
            categoryWrapper.eq(SysConfigCategory::getModuleId, moduleId);
        }
        List<SysConfigCategory> categories = categoryMapper.selectList(categoryWrapper);

        if (CollectionUtils.isEmpty(categories)) {
            return Collections.emptyList();
        }

        // 查询所有配置项
        List<Long> categoryIds =
                categories.stream().map(SysConfigCategory::getId).collect(Collectors.toList());
        List<SysConfigItem> items =
                itemMapper.selectList(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .in(SysConfigItem::getCategoryId, categoryIds)
                                .eq(SysConfigItem::getSubjectId, subjectId)
                                .orderByAsc(SysConfigItem::getSort)
                                .orderByAsc(SysConfigItem::getId));

        // 构建混合树
        return buildMixedTree(categories, items);
    }

    /** 构建混合树（Category + Item） */
    private List<SysConfigTreeNodeResp> buildMixedTree(
            List<SysConfigCategory> categories, List<SysConfigItem> items) {
        List<SysConfigTreeNodeResp> result = new ArrayList<>();

        // 将 Category 转换为树节点
        Map<Long, SysConfigTreeNodeResp> categoryNodeMap = new LinkedHashMap<>();
        for (SysConfigCategory category : categories) {
            SysConfigTreeNodeResp node = new SysConfigTreeNodeResp();
            node.setNodeType("category");
            node.setId(category.getId());
            node.setPid(0L);
            node.setLabel(category.getName());
            node.setDescription(category.getDescription());
            node.setFormat(category.getFormat());
            node.setSource(category.getSource());
            node.setSort(category.getSort());
            node.setStatus(category.getStatus());
            node.setDepth(1);
            node.setPath("/" + category.getId() + "/");
            node.setChildren(new ArrayList<>());
            categoryNodeMap.put(category.getId(), node);
            result.add(node);
        }

        // 将 Item 转换为树节点并挂载到对应的 Category 下
        Map<Long, SysConfigTreeNodeResp> itemNodeMap = new LinkedHashMap<>();
        for (SysConfigItem item : items) {
            SysConfigTreeNodeResp node = new SysConfigTreeNodeResp();
            node.setNodeType("item");
            node.setId(item.getId());
            node.setPid(item.getPid());
            node.setCategoryId(item.getCategoryId());
            node.setCategoryAlias(item.getCategoryAlias());
            node.setLabel(item.getLabel());
            node.setValue(item.getValue());
            node.setDescription(item.getDescription());
            node.setExtra(item.getExtra());
            node.setSource(item.getSource());
            node.setSort(item.getSort());
            node.setStatus(item.getStatus());
            node.setDepth(item.getDepth());
            node.setPath(item.getPath());
            node.setChildren(new ArrayList<>());
            itemNodeMap.put(item.getId(), node);
        }

        // 组装 Item 的树形结构
        for (SysConfigItem item : items) {
            SysConfigTreeNodeResp node = itemNodeMap.get(item.getId());
            Long pid = item.getPid();
            if (pid == null || pid == 0L) {
                // 挂载到
                SysConfigTreeNodeResp categoryNode = categoryNodeMap.get(item.getCategoryId());
                if (categoryNode != null) {
                    categoryNode.getChildren().add(node);
                }
            } else {
                // 挂载到父 Item 下
                SysConfigTreeNodeResp parentNode = itemNodeMap.get(pid);
                if (parentNode != null) {
                    parentNode.getChildren().add(node);
                } else {
                    // 如果找不到父节点，挂载到 Category 下
                    SysConfigTreeNodeResp categoryNode = categoryNodeMap.get(item.getCategoryId());
                    if (categoryNode != null) {
                        categoryNode.getChildren().add(node);
                    }
                }
            }
        }

        return result;
    }

    @Override
    public SysConfigCategoryResp getByAlias(String alias) {
        String projectNo = AppContext.getProjectNo();
        SysConfigCategory entity =
                categoryMapper.selectOne(
                        Wrappers.<SysConfigCategory>lambdaQuery()
                                .eq(SysConfigCategory::getAlias, alias)
                                .eq(SysConfigCategory::getProjectNo, projectNo));
        if (entity == null) {
            return null;
        }
        SysConfigCategoryResp resp = new SysConfigCategoryResp();
        BeanUtils.copyProperties(entity, resp);
        return resp;
    }

    @Override
    public void saveCategory(SysConfigCategorySaveReq req) {
        String projectNo = AppContext.getProjectNo();

        // 校验：name 和 alias 不能为空
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new BusinessException("分类名称不能为空");
        }
        if (req.getAlias() == null || req.getAlias().trim().isEmpty()) {
            throw new BusinessException("分类标识不能为空");
        }

        // 校验：alias 只能包含字母、数字、下划线
        if (!req.getAlias().matches("^[a-zA-Z0-9_]+$")) {
            throw new BusinessException("分类标识只能包含字母、数字、下划线");
        }

        if (req.getId() == null) {
            // 新增
            // 校验 alias 是否重复
            checkDuplicateAlias(req.getAlias(), projectNo, null);
            SysConfigCategory entity = new SysConfigCategory();
            BeanUtils.copyProperties(req, entity);
            entity.setProjectNo(projectNo);
            categoryMapper.insert(entity);
        } else {
            // 编辑
            boolean exists =
                    categoryMapper.exists(
                            Wrappers.<SysConfigCategory>lambdaQuery()
                                    .eq(SysConfigCategory::getId, req.getId())
                                    .eq(SysConfigCategory::getProjectNo, projectNo));
            if (!exists) {
                throw new BusinessException("配置分类不存在");
            }
            // 校验 alias 是否重复
            checkDuplicateAlias(req.getAlias(), projectNo, req.getId());
            SysConfigCategory entity = new SysConfigCategory();
            BeanUtils.copyProperties(req, entity);
            categoryMapper.updateById(entity);
        }
    }

    @Transactional
    @Override
    public void deleteCategory(Long id, boolean force) {
        String projectNo = AppContext.getProjectNo();
        boolean exists =
                categoryMapper.exists(
                        Wrappers.<SysConfigCategory>lambdaQuery()
                                .eq(SysConfigCategory::getId, id)
                                .eq(SysConfigCategory::getProjectNo, projectNo));
        if (!exists) {
            throw new BusinessException("配置分类不存在");
        }
        // 查询关联的配置项数量
        long itemCount =
                itemMapper.selectCount(
                        Wrappers.<SysConfigItem>lambdaQuery().eq(SysConfigItem::getCategoryId, id));
        if (!force && itemCount > 0) {
            throw new BusinessException(
                    "删除确认：该分类下有 " + itemCount + " 个配置项将被同步删除；请确认后携带 force=true 重新请求");
        }
        // 级联删除配置项
        itemMapper.delete(
                Wrappers.<SysConfigItem>lambdaQuery().eq(SysConfigItem::getCategoryId, id));
        // 删除分类
        categoryMapper.deleteById(id);
    }

    /** 校验 alias 是否重复 */
    private void checkDuplicateAlias(String alias, String projectNo, Long excludeId) {
        LambdaQueryWrapper<SysConfigCategory> wrapper =
                new LambdaQueryWrapper<SysConfigCategory>()
                        .eq(SysConfigCategory::getAlias, alias)
                        .eq(SysConfigCategory::getProjectNo, projectNo);
        if (excludeId != null) {
            wrapper.ne(SysConfigCategory::getId, excludeId);
        }
        long count = categoryMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException("分类标识 alias 已存在");
        }
    }

    // ==================== 配置项接口委托 ====================

    @Override
    public List<SysConfigItemTreeResp> treeByCategory(Long categoryId) {
        return itemService.treeByCategory(categoryId);
    }

    @Override
    public List<SysConfigItemTreeResp> treeByCategoryAlias(String categoryAlias) {
        return itemService.treeByCategoryAlias(categoryAlias);
    }

    @Override
    public List<SysConfigItemTreeResp> listByCategory(Long categoryId) {
        return itemService.listByCategory(categoryId);
    }

    @Override
    public List<SysConfigItemTreeResp> listByCategoryAlias(String categoryAlias) {
        return itemService.listByCategoryAlias(categoryAlias);
    }

    @Override
    public void saveItem(SysConfigItemSaveReq req) {
        itemService.saveItem(req);
    }

    @Override
    public void deleteItem(Long id, boolean force) {
        itemService.deleteItem(id, force);
    }
}
