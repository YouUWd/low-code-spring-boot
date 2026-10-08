package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysConfigCategoryApi;
import com.jdec.platform.config.api.dto.request.*;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.config.biz.entity.SysConfigCategory;
import com.jdec.platform.config.biz.entity.SysConfigItem;
import com.jdec.platform.config.biz.mapper.SysConfigCategoryMapper;
import com.jdec.platform.config.biz.mapper.SysConfigItemMapper;
import com.jdec.platform.config.biz.reference.ReferenceCheckService;
import com.jdec.platform.config.biz.reference.ReferenceViolation;
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

/** 配置分类 Service 实现 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysConfigCategoryService implements SysConfigCategoryApi {

    /** 混合树节点类型：配置分类 */
    private static final String NODE_TYPE_CATEGORY = "category";

    /** 混合树节点类型：配置项 */
    private static final String NODE_TYPE_ITEM = "item";

    private final SysConfigCategoryMapper categoryMapper;
    private final SysConfigItemMapper itemMapper;
    private final SysConfigItemService itemService;
    private final ReferenceCheckService referenceCheckService;

    /**
     * 延迟获取自身代理，避免构造循环依赖。
     *
     * <p>整棵树保存时经代理调用 {@link #saveCategoryNode(SysConfigCategorySaveReq)}，确保 {@code @DataAudit}
     * 等切面在节点级保存上生效。
     */
    private final ObjectProvider<SysConfigCategoryService> selfProvider;

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
                } /*else {
                      // 如果找不到父节点，挂载到 Category 下
                      SysConfigTreeNodeResp categoryNode =
                              categoryNodeMap.get(item.getCategoryAlias());
                      if (categoryNode != null) {
                          categoryNode.getChildren().add(node);
                      }
                  }*/
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

    @Transactional
    @Override
    public Long saveCategory(SaveConfigCategoryReq request) {
        if (request == null || request.getConfig() == null) {
            throw new BusinessException("配置分类信息不能为空");
        }
        SysConfigCategorySaveReq node = request.getConfig();
        boolean hasTree = !CollectionUtils.isEmpty(request.getTreeNodes());
        if (hasTree) {
            // 携带整棵树时，当前节点位置以 treeNodes 为准（分类无父子层级，仅回填同级排序）
            ConfigTreeNode own =
                    findTreeNode(request.getTreeNodes(), NODE_TYPE_CATEGORY, node.getId());
            if (own != null && node.getId() == null && own.getSort() != null) {
                node.setSort(own.getSort());
            }
        }
        // 经自身代理调用单节点保存，确保 @DataAudit 审计生效
        Long categoryId = selfProvider.getObject().saveCategoryNode(node);
        if (hasTree) {
            // 整棵混合树的父子关系与同级排序同步（分类与配置项分属两张表，按 nodeType 拆分处理）
            syncMixedTree(NODE_TYPE_CATEGORY, categoryId, request.getTreeNodes());
        }
        return categoryId;
    }

    /**
     * 单节点配置分类新增/编辑：id 为空则新增，否则编辑。
     *
     * <p>供整棵混合树保存接口经自身代理调用，以保留 {@code @DataAudit} 审计。
     */
    @DataAudit(
            module = "系统设置",
            subModule = "通用配置分类",
            operation = OperationType.UPDATE,
            tableName = "sys_config_category",
            dataIdField = "#req.id")
    @Transactional
    public Long saveCategoryNode(SysConfigCategorySaveReq req) {
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
            SysConfigCategory oldEntity =
                    categoryMapper.selectOne(
                            Wrappers.<SysConfigCategory>lambdaQuery()
                                    .eq(SysConfigCategory::getId, req.getId())
                                    .eq(SysConfigCategory::getProjectNo, projectNo)
                                    .eq(SysConfigCategory::getSubjectId, subjectId));
            if (oldEntity == null) {
                throw new BusinessException("配置分类不存在");
            }
            // 校验 categoryAlias 是否重复
            checkDuplicateAlias(req.getCategoryAlias(), projectNo, subjectId, req.getId());
            SysConfigCategory entity = new SysConfigCategory();
            BeanUtils.copyProperties(req, entity);
            // subject_id / project_no 为归属标识，不允许前端覆盖，更新时以当前上下文为准并作为 WHERE 条件
            entity.setSubjectId(null);
            entity.setProjectNo(null);
            categoryMapper.update(
                    entity,
                    Wrappers.<SysConfigCategory>lambdaUpdate()
                            .eq(SysConfigCategory::getId, req.getId())
                            .eq(SysConfigCategory::getProjectNo, projectNo)
                            .eq(SysConfigCategory::getSubjectId, subjectId));

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
        // 获取分类（限定当前主体与应用）
        SysConfigCategory category =
                categoryMapper.selectOne(
                        Wrappers.<SysConfigCategory>lambdaQuery()
                                .eq(SysConfigCategory::getId, id)
                                .eq(SysConfigCategory::getProjectNo, projectNo)
                                .eq(SysConfigCategory::getSubjectId, subjectId));
        if (category == null) {
            throw new BusinessException("配置分类不存在");
        }
        // 引用检查：分类通过 categoryAlias 被配置项引用，存在引用则禁止删除
        List<ReferenceViolation> violations =
                referenceCheckService.check(
                        SysConfigCategory.class,
                        "categoryAlias",
                        List.of(category.getCategoryAlias()));
        if (!violations.isEmpty()) {
            throw new PopException(
                    referenceCheckService.buildMessage(category.getLabel(), violations));
        }
        // 删除分类
        categoryMapper.delete(
                Wrappers.<SysConfigCategory>lambdaQuery()
                        .eq(SysConfigCategory::getId, id)
                        .eq(SysConfigCategory::getProjectNo, projectNo)
                        .eq(SysConfigCategory::getSubjectId, subjectId));
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

    @Transactional
    @Override
    public Long saveItem(SaveConfigItemReq request) {
        if (request == null || request.getConfig() == null) {
            throw new BusinessException("配置项信息不能为空");
        }
        SysConfigItemSaveReq node = request.getConfig();
        boolean hasTree = !CollectionUtils.isEmpty(request.getTreeNodes());
        if (hasTree) {
            // 携带整棵树时，当前节点位置以 treeNodes 为准，保证同级重名等单节点校验分组正确
            ConfigTreeNode own = findTreeNode(request.getTreeNodes(), NODE_TYPE_ITEM, node.getId());
            if (own != null) {
                if (own.getParentId() != null) {
                    node.setPid(own.getParentId());
                }
                if (node.getId() == null && own.getSort() != null) {
                    node.setSort(own.getSort());
                }
            }
        }
        // 经 itemService 代理调用单节点保存，确保 @DataAudit 审计生效
        Long itemId = itemService.saveItemNode(node);
        if (hasTree) {
            // 整棵混合树的父子关系与同级排序同步（分类与配置项分属两张表，按 nodeType 拆分处理）
            syncMixedTree(NODE_TYPE_ITEM, itemId, request.getTreeNodes());
        }
        return itemId;
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

    // ==================== 混合树同步 ====================

    /**
     * 在整棵混合树的扁平节点中查找与当前保存节点匹配的节点。
     *
     * <p>仅匹配相同 nodeType 的节点；当前节点为新增（id 为空或 0）时匹配 id 为空或 0 的节点。
     */
    private ConfigTreeNode findTreeNode(
            List<ConfigTreeNode> treeNodes, String nodeType, Long nodeId) {
        for (ConfigTreeNode node : treeNodes) {
            if (!nodeType.equals(node.getNodeType())) {
                continue;
            }
            boolean matched;
            if (nodeId == null) {
                matched = node.getId() == null || node.getId() == 0L;
            } else {
                matched = nodeId.equals(node.getId());
            }
            if (matched) {
                return node;
            }
        }
        return null;
    }

    /**
     * 同步整棵混合树的父子关系与同级排序。
     *
     * <p>先按 nodeType 将扁平节点拆分为分类与配置项两组，分别操作 sys_config_category 与 sys_config_item； 节点 id 为空或 0
     * 视为本次新增的当前节点（nodeType 与 savedNodeType 匹配时替换为保存后生成的真实 ID）； 仅对实际发生变化的节点执行更新，请求树中缺失的节点保持原样不做删除处理。
     */
    private void syncMixedTree(
            String savedNodeType, Long savedNodeId, List<ConfigTreeNode> treeNodes) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();

        // 1. 查询当前主体与应用下全部分类与配置项
        List<SysConfigCategory> allCategories =
                categoryMapper.selectList(
                        Wrappers.<SysConfigCategory>lambdaQuery()
                                .eq(SysConfigCategory::getProjectNo, projectNo)
                                .eq(SysConfigCategory::getSubjectId, subjectId));
        List<SysConfigItem> allItems =
                itemMapper.selectList(
                        Wrappers.<SysConfigItem>lambdaQuery()
                                .eq(SysConfigItem::getProjectNo, projectNo)
                                .eq(SysConfigItem::getSubjectId, subjectId));
        Map<Long, SysConfigCategory> categoryMap =
                allCategories.stream().collect(Collectors.toMap(SysConfigCategory::getId, c -> c));
        Map<Long, SysConfigItem> itemMap =
                allItems.stream().collect(Collectors.toMap(SysConfigItem::getId, i -> i));

        // 2. 按 nodeType 拆分并规整节点
        List<ConfigTreeNode> categoryNodes = new ArrayList<>();
        List<ConfigTreeNode> itemNodes = new ArrayList<>();
        for (ConfigTreeNode node : treeNodes) {
            if (NODE_TYPE_CATEGORY.equals(node.getNodeType())) {
                categoryNodes.add(normalizeNode(node, savedNodeType, savedNodeId));
            } else if (NODE_TYPE_ITEM.equals(node.getNodeType())) {
                itemNodes.add(normalizeNode(node, savedNodeType, savedNodeId));
            } else {
                throw new BusinessException(400, "未知的树节点类型: " + node.getNodeType());
            }
        }

        // 3. 校验归属：分类、配置项及其父节点均须属于当前主体与应用
        for (ConfigTreeNode node : categoryNodes) {
            if (!categoryMap.containsKey(node.getId())) {
                throw new BusinessException(400, "待调整树结构的配置分类不存在或无权操作: " + node.getId());
            }
        }
        for (ConfigTreeNode node : itemNodes) {
            if (!itemMap.containsKey(node.getId())) {
                throw new BusinessException(400, "待调整树结构的配置项不存在或无权操作: " + node.getId());
            }
            if (node.getParentId() != 0L && !itemMap.containsKey(node.getParentId())) {
                throw new BusinessException(400, "目标父配置项不存在或无权操作: " + node.getParentId());
            }
        }

        // 4. 防环校验：沿配置项父级链路向上遍历，防止出现环状依赖
        Map<Long, Long> nextParentMap = new HashMap<>();
        for (SysConfigItem item : allItems) {
            nextParentMap.put(item.getId(), item.getPid() != null ? item.getPid() : 0L);
        }
        for (ConfigTreeNode node : itemNodes) {
            nextParentMap.put(node.getId(), node.getParentId());
        }
        for (Long startId : nextParentMap.keySet()) {
            Set<Long> visited = new HashSet<>();
            Long cur = startId;
            while (cur != null && cur != 0L) {
                if (!visited.add(cur)) {
                    throw new BusinessException(400, "配置项树结构存在循环引用，调整失败");
                }
                cur = nextParentMap.get(cur);
            }
        }

        // 5. 分类仅维护同级排序（分类无父子层级）
        int categoryUpdated = 0;
        for (ConfigTreeNode node : categoryNodes) {
            SysConfigCategory current = categoryMap.get(node.getId());
            Integer currentSort = current.getSort() != null ? current.getSort() : 0;
            if (!Objects.equals(currentSort, node.getSort())) {
                categoryMapper.update(
                        null,
                        Wrappers.<SysConfigCategory>lambdaUpdate()
                                .set(SysConfigCategory::getSort, node.getSort())
                                .eq(SysConfigCategory::getId, current.getId())
                                .eq(SysConfigCategory::getProjectNo, projectNo)
                                .eq(SysConfigCategory::getSubjectId, subjectId));
                categoryUpdated++;
            }
        }

        // 6. 配置项同步父子关系与同级排序，差异比对后仅更新变化节点
        int itemUpdated = 0;
        boolean parentChanged = false;
        for (ConfigTreeNode node : itemNodes) {
            SysConfigItem current = itemMap.get(node.getId());
            Long currentPid = current.getPid() != null ? current.getPid() : 0L;
            Integer currentSort = current.getSort() != null ? current.getSort() : 0;
            if (!Objects.equals(currentPid, node.getParentId())
                    || !Objects.equals(currentSort, node.getSort())) {
                itemMapper.update(
                        null,
                        Wrappers.<SysConfigItem>lambdaUpdate()
                                .set(SysConfigItem::getPid, node.getParentId())
                                .set(SysConfigItem::getSort, node.getSort())
                                .eq(SysConfigItem::getId, current.getId())
                                .eq(SysConfigItem::getProjectNo, projectNo)
                                .eq(SysConfigItem::getSubjectId, subjectId));
                itemUpdated++;
                if (!Objects.equals(currentPid, node.getParentId())) {
                    parentChanged = true;
                }
                // 同步内存中的父子关系，供后续 path / depth 重算使用
                current.setPid(node.getParentId());
                current.setSort(node.getSort());
            }
        }

        // 7. 父子关系发生变化时重算 path / depth
        if (parentChanged) {
            recomputeItemPathAndDepth(allItems, subjectId, projectNo);
        }

        if (categoryUpdated > 0 || itemUpdated > 0) {
            log.info("同步更新了 {} 个配置分类、{} 个配置项的树节点位置与排序", categoryUpdated, itemUpdated);
        }
    }

    /** 规整树节点：新增节点 id 替换为保存后生成的真实 ID，父级与排序值做空值兜底 */
    private ConfigTreeNode normalizeNode(
            ConfigTreeNode node, String savedNodeType, Long savedNodeId) {
        ConfigTreeNode normalized = new ConfigTreeNode();
        normalized.setNodeType(node.getNodeType());
        Long id = node.getId() == null || node.getId() == 0L ? 0L : node.getId();
        if (id == 0L && savedNodeId != null && savedNodeType.equals(node.getNodeType())) {
            id = savedNodeId;
        }
        normalized.setId(id);
        normalized.setParentId(
                node.getParentId() == null || node.getParentId() == 0L ? 0L : node.getParentId());
        normalized.setSort(node.getSort() != null ? node.getSort() : 0);
        return normalized;
    }

    /** 依据最新的父子关系重算配置项的 path / depth，仅更新实际变化的节点 */
    private void recomputeItemPathAndDepth(
            List<SysConfigItem> allItems, Long subjectId, String projectNo) {
        Map<Long, String> oldPaths = new HashMap<>();
        Map<Long, Integer> oldDepths = new HashMap<>();
        for (SysConfigItem item : allItems) {
            oldPaths.put(item.getId(), item.getPath());
            oldDepths.put(item.getId(), item.getDepth());
        }
        // 组装子节点映射，pid 为空/0/自身/指向不存在的节点均按根节点处理
        Map<Long, List<SysConfigItem>> childrenMap = new HashMap<>();
        List<SysConfigItem> roots = new ArrayList<>();
        for (SysConfigItem item : allItems) {
            Long pid = item.getPid();
            if (pid == null
                    || pid == 0L
                    || pid.equals(item.getId())
                    || !oldPaths.containsKey(pid)) {
                item.setPath("/");
                item.setDepth(1);
                roots.add(item);
            } else {
                childrenMap.computeIfAbsent(pid, k -> new ArrayList<>()).add(item);
            }
        }
        Deque<SysConfigItem> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            SysConfigItem parent = queue.poll();
            for (SysConfigItem child :
                    childrenMap.getOrDefault(parent.getId(), Collections.emptyList())) {
                child.setPath(parent.getPath() + parent.getId() + "/");
                child.setDepth(parent.getDepth() + 1);
                queue.add(child);
            }
        }
        for (SysConfigItem item : allItems) {
            if (!Objects.equals(oldPaths.get(item.getId()), item.getPath())
                    || !Objects.equals(oldDepths.get(item.getId()), item.getDepth())) {
                itemMapper.update(
                        null,
                        Wrappers.<SysConfigItem>lambdaUpdate()
                                .set(SysConfigItem::getPath, item.getPath())
                                .set(SysConfigItem::getDepth, item.getDepth())
                                .eq(SysConfigItem::getId, item.getId())
                                .eq(SysConfigItem::getProjectNo, projectNo)
                                .eq(SysConfigItem::getSubjectId, subjectId));
            }
        }
    }
}
