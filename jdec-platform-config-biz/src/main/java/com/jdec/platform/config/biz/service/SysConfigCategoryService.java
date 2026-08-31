package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysConfigCategoryApi;
import com.jdec.platform.config.api.dto.request.SysConfigCategorySaveReq;
import com.jdec.platform.config.api.dto.request.SysConfigItemDragReq;
import com.jdec.platform.config.api.dto.request.SysConfigItemSaveReq;
import com.jdec.platform.config.api.dto.response.*;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/** 配置分类 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_ENGINE)
public class SysConfigCategoryService implements SysConfigCategoryApi {

    private final SysConfigCategoryMapper categoryMapper;
    private final SysConfigItemMapper itemMapper;
    private final SysConfigItemService itemService;

    @Override
    public List<SysConfigCategoryResp> list() {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        LambdaQueryWrapper<SysConfigCategory> wrapper =
                new LambdaQueryWrapper<SysConfigCategory>()
                        .eq(SysConfigCategory::getProjectNo, projectNo)
                        .eq(SysConfigCategory::getSubjectId, subjectId)
                        .orderByAsc(SysConfigCategory::getSort)
                        .orderByAsc(SysConfigCategory::getId);
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
    public List<SysConfigTreeNodeResp> mixedTree() {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();

        // 查询所有分类
        LambdaQueryWrapper<SysConfigCategory> categoryWrapper =
                new LambdaQueryWrapper<SysConfigCategory>()
                        .eq(SysConfigCategory::getProjectNo, projectNo)
                        .eq(SysConfigCategory::getSubjectId, subjectId)
                        .orderByAsc(SysConfigCategory::getSort)
                        .orderByAsc(SysConfigCategory::getId);
        List<SysConfigCategory> categories = categoryMapper.selectList(categoryWrapper);

        if (CollectionUtils.isEmpty(categories)) {
            return Collections.emptyList();
        }

        // 查询所有配置项（按 categoryAlias 关联）
        List<String> categoryAliases =
                categories.stream()
                        .map(SysConfigCategory::getCategoryAlias)
                        .collect(Collectors.toList());
        List<SysConfigItem> items =
                itemMapper.selectList(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .in(SysConfigItem::getCategoryAlias, categoryAliases)
                                .eq(SysConfigItem::getSubjectId, subjectId)
                                .eq(SysConfigItem::getProjectNo, projectNo)
                                .orderByAsc(SysConfigItem::getSort)
                                .orderByAsc(SysConfigItem::getId));

        // 构建混合树
        return buildMixedTree(categories, items);
    }

    @Override
    public List<AllConfigResp> getAllConfigs() {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();

        // 查询所有分类
        LambdaQueryWrapper<SysConfigCategory> categoryWrapper =
                new LambdaQueryWrapper<SysConfigCategory>()
                        .eq(SysConfigCategory::getProjectNo, projectNo)
                        .eq(SysConfigCategory::getSubjectId, subjectId)
                        .orderByAsc(SysConfigCategory::getSort)
                        .orderByAsc(SysConfigCategory::getId);
        List<SysConfigCategory> categories = categoryMapper.selectList(categoryWrapper);

        if (CollectionUtils.isEmpty(categories)) {
            return Collections.emptyList();
        }

        // 查询所有配置项（按 categoryAlias 关联）
        List<String> categoryAliases =
                categories.stream()
                        .map(SysConfigCategory::getCategoryAlias)
                        .collect(Collectors.toList());
        List<SysConfigItem> items =
                itemMapper.selectList(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .in(SysConfigItem::getCategoryAlias, categoryAliases)
                                .eq(SysConfigItem::getSubjectId, subjectId)
                                .eq(SysConfigItem::getProjectNo, projectNo)
                                .orderByAsc(SysConfigItem::getSort)
                                .orderByAsc(SysConfigItem::getId));

        // 按分类分组配置项
        Map<String, List<SysConfigItem>> itemsByCategory =
                items.stream().collect(Collectors.groupingBy(SysConfigItem::getCategoryAlias));

        // 构建响应
        return categories.stream()
                .map(
                        category -> {
                            AllConfigResp resp = new AllConfigResp();
                            resp.setCategoryAlias(category.getCategoryAlias());
                            resp.setFormat(category.getFormat());

                            // 获取该分类下的配置项
                            List<SysConfigItem> categoryItems =
                                    itemsByCategory.getOrDefault(
                                            category.getCategoryAlias(), Collections.emptyList());

                            // 转换为 VO
                            List<AllConfigResp.ConfigItemVO> itemVOs =
                                    categoryItems.stream()
                                            .map(
                                                    item -> {
                                                        AllConfigResp.ConfigItemVO vo =
                                                                new AllConfigResp.ConfigItemVO();
                                                        vo.setId(item.getId());
                                                        vo.setPid(item.getPid());
                                                        vo.setPath(item.getPath());
                                                        vo.setDepth(item.getDepth());
                                                        vo.setLabel(item.getLabel());
                                                        vo.setValue(item.getValue());
                                                        vo.setDescription(item.getDescription());
                                                        vo.setExtra(item.getExtra());
                                                        vo.setSort(item.getSort());
                                                        vo.setStatus(item.getStatus());
                                                        vo.setSubjectId(item.getSubjectId());
                                                        return vo;
                                                    })
                                            .collect(Collectors.toList());

                            resp.setItems(itemVOs);
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    /** 构建混合树（Category + Item） */
    private List<SysConfigTreeNodeResp> buildMixedTree(
            List<SysConfigCategory> categories, List<SysConfigItem> items) {
        List<SysConfigTreeNodeResp> result = new ArrayList<>();

        // 将 Category 转换为树节点，并建立 alias -> node 的映射
        Map<String, SysConfigTreeNodeResp> categoryNodeMap = new LinkedHashMap<>();
        for (SysConfigCategory category : categories) {
            SysConfigTreeNodeResp node = new SysConfigTreeNodeResp();
            node.setNodeType("category");
            node.setId(category.getId());
            node.setNodeId("category-" + category.getId());
            node.setPid(0L);
            node.setParentNodeId("0");
            node.setCategoryAlias(category.getCategoryAlias());
            node.setLabel(category.getLabel());
            node.setDescription(category.getDescription());
            node.setFormat(category.getFormat());
            node.setSource(category.getSource());
            node.setSort(category.getSort());
            node.setStatus(category.getStatus());
            node.setDepth(1);
            node.setPath("/" + category.getId() + "/");
            node.setChildren(new ArrayList<>());
            categoryNodeMap.put(category.getCategoryAlias(), node);
            result.add(node);
        }

        // 将 Item 转换为树节点
        Map<Long, SysConfigTreeNodeResp> itemNodeMap = new LinkedHashMap<>();
        for (SysConfigItem item : items) {
            SysConfigTreeNodeResp node = new SysConfigTreeNodeResp();
            node.setNodeType("item");
            node.setId(item.getId());
            node.setNodeId("item-" + item.getId());
            node.setPid(item.getPid());
            node.setParentNodeId(
                    item.getPid() == null || item.getPid() == 0L ? "0" : "item-" + item.getPid());
            node.setCategoryAlias(item.getCategoryAlias());
            node.setLabel(item.getLabel());
            node.setValue(item.getValue());
            node.setDescription(item.getDescription());
            node.setExtra(item.getExtra());
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
                // 挂载到 Category 下（通过 categoryAlias）
                SysConfigTreeNodeResp categoryNode = categoryNodeMap.get(item.getCategoryAlias());
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
                    SysConfigTreeNodeResp categoryNode =
                            categoryNodeMap.get(item.getCategoryAlias());
                    if (categoryNode != null) {
                        categoryNode.getChildren().add(node);
                    }
                }
            }
        }

        return result;
    }

    @Override
    public SysConfigCategoryResp getByCategoryAlias(String categoryAlias) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        SysConfigCategory entity =
                categoryMapper.selectOne(
                        Wrappers.<SysConfigCategory>lambdaQuery()
                                .eq(SysConfigCategory::getCategoryAlias, categoryAlias)
                                .eq(SysConfigCategory::getProjectNo, projectNo)
                                .eq(SysConfigCategory::getSubjectId, subjectId));
        if (entity == null) {
            return null;
        }
        SysConfigCategoryResp resp = new SysConfigCategoryResp();
        BeanUtils.copyProperties(entity, resp);
        return resp;
    }

    @DataAudit(
            module = "系统设置",
            subModule = "通用配置分类",
            operation = OperationType.UPDATE,
            tableName = "sys_config_category",
            dataIdField = "#req.id")
    @Transactional
    @Override
    public Long saveCategory(SysConfigCategorySaveReq req) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();

        // 校验：name 和 alias 不能为空
        if (req.getLabel() == null || req.getLabel().trim().isEmpty()) {
            throw new BusinessException("分类名称不能为空");
        }
        if (req.getCategoryAlias() == null || req.getCategoryAlias().trim().isEmpty()) {
            throw new BusinessException("分类标识不能为空");
        }

        // 校验：categoryAlias 只能包含字母、数字、下划线
        if (!req.getCategoryAlias().matches("^[a-zA-Z0-9_]+$")) {
            throw new BusinessException("分类标识只能包含字母、数字、下划线");
        }

        if (req.getId() == null) {
            // 新增
            // 校验 categoryAlias 是否重复
            checkDuplicateAlias(req.getCategoryAlias(), projectNo, subjectId, null);
            SysConfigCategory entity = new SysConfigCategory();
            BeanUtils.copyProperties(req, entity);
            entity.setProjectNo(projectNo);
            entity.setSubjectId(subjectId);
            categoryMapper.insert(entity);
            // 设置id到请求对象，用于审计切面重新解析dataId
            req.setId(entity.getId());
            return entity.getId();
        } else {
            // 编辑
            boolean exists =
                    categoryMapper.exists(
                            Wrappers.<SysConfigCategory>lambdaQuery()
                                    .eq(SysConfigCategory::getId, req.getId())
                                    .eq(SysConfigCategory::getProjectNo, projectNo)
                                    .eq(SysConfigCategory::getSubjectId, subjectId));
            if (!exists) {
                throw new BusinessException("配置分类不存在");
            }
            // 校验 categoryAlias 是否重复
            checkDuplicateAlias(req.getCategoryAlias(), projectNo, subjectId, req.getId());
            SysConfigCategory oldEntity = categoryMapper.selectById(req.getId());
            SysConfigCategory entity = new SysConfigCategory();
            BeanUtils.copyProperties(req, entity);
            categoryMapper.updateById(entity);

            // 分类停用时，级联停用该分类下所有配置项
            if (Integer.valueOf(0).equals(req.getStatus())
                    && !Integer.valueOf(0).equals(oldEntity.getStatus())) {
                itemMapper.update(
                        null,
                        Wrappers.<SysConfigItem>lambdaUpdate()
                                .set(SysConfigItem::getStatus, 0)
                                .eq(SysConfigItem::getCategoryAlias, oldEntity.getCategoryAlias())
                                .eq(SysConfigItem::getProjectNo, projectNo)
                                .eq(SysConfigItem::getSubjectId, subjectId));
            }
            return null;
        }
    }

    @DataAudit(
            module = "系统设置",
            subModule = "通用配置-分类管理",
            operation = OperationType.DELETE,
            tableName = "sys_config_category",
            deleteDisplayField = "label",
            entityClass = SysConfigCategory.class,
            dataIdField = "#id")
    @Transactional
    @Override
    public void deleteCategory(Long id, boolean force) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        boolean exists =
                categoryMapper.exists(
                        Wrappers.<SysConfigCategory>lambdaQuery()
                                .eq(SysConfigCategory::getId, id)
                                .eq(SysConfigCategory::getProjectNo, projectNo)
                                .eq(SysConfigCategory::getSubjectId, subjectId));
        if (!exists) {
            throw new BusinessException("配置分类不存在");
        }
        // 获取分类 alias
        SysConfigCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException("配置分类不存在");
        }
        // 查询关联的配置项数量（通过 categoryAlias）
        long itemCount =
                itemMapper.selectCount(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .eq(SysConfigItem::getCategoryAlias, category.getCategoryAlias())
                                .eq(SysConfigItem::getProjectNo, projectNo)
                                .eq(SysConfigItem::getSubjectId, subjectId));
        if (!force && itemCount > 0) {
            throw new BusinessException("删除确认：该分类下有 " + itemCount + " 个配置项将被同步删除；请确认后重新请求");
        }
        // 级联删除配置项（通过 categoryAlias）
        itemMapper.delete(
                Wrappers.<SysConfigItem>lambdaQuery()
                        .eq(SysConfigItem::getCategoryAlias, category.getCategoryAlias())
                        .eq(SysConfigItem::getProjectNo, projectNo)
                        .eq(SysConfigItem::getSubjectId, subjectId));
        // 删除分类
        categoryMapper.deleteById(id);
    }

    /** 校验 categoryAlias 是否重复 */
    private void checkDuplicateAlias(
            String alias, String projectNo, Long subjectId, Long excludeId) {
        LambdaQueryWrapper<SysConfigCategory> wrapper =
                new LambdaQueryWrapper<SysConfigCategory>()
                        .eq(SysConfigCategory::getCategoryAlias, alias)
                        .eq(SysConfigCategory::getProjectNo, projectNo)
                        .eq(SysConfigCategory::getSubjectId, subjectId);
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
    public List<SysConfigItemTreeResp> getItemsByCategory(Long categoryId) {
        return itemService.getItemsByCategory(categoryId);
    }

    @Override
    public List<SysConfigItemTreeResp> getItemsByCategoryAlias(String categoryAlias) {
        return itemService.getItemsByCategoryAlias(categoryAlias);
    }

    @Override
    public Long saveItem(SysConfigItemSaveReq req) {
        return itemService.saveItem(req);
    }

    @Override
    public void deleteItem(Long id, boolean force) {
        itemService.deleteItem(id, force);
    }

    @Override
    public void dragItem(SysConfigItemDragReq req) {
        itemService.dragItem(req);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SysConfigItemResp getConfigItemsByValue(String categoryAlias, String itemValue) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        SysConfigItem item =
                itemMapper.selectOne(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .eq(SysConfigItem::getCategoryAlias, categoryAlias)
                                .eq(SysConfigItem::getValue, itemValue)
                                .eq(SysConfigItem::getProjectNo, projectNo)
                                .eq(SysConfigItem::getSubjectId, subjectId)
                                .last("limit 1"));
        if (Objects.isNull(item)) {
            return null;
        }
        SysConfigItemResp resp = new SysConfigItemResp();
        BeanUtils.copyProperties(item, resp);
        return resp;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SysConfigItemResp getConfigItemsById(Long id) {
        SysConfigItem item = itemMapper.selectById(id);
        if (Objects.isNull(item)) {
            return null;
        }
        SysConfigItemResp resp = new SysConfigItemResp();
        BeanUtils.copyProperties(item, resp);
        return resp;
    }
}
