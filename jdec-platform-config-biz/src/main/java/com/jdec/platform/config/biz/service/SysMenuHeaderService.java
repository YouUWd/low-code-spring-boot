package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysMenuHeaderApi;
import com.jdec.platform.config.api.dto.request.SysMenuHeaderReq;
import com.jdec.platform.config.api.dto.response.SysMenuHeaderResp;
import com.jdec.platform.config.biz.entity.SysMenu;
import com.jdec.platform.config.biz.entity.SysMenuHeader;
import com.jdec.platform.config.biz.entity.SysModuleField;
import com.jdec.platform.config.biz.mapper.SysMenuHeaderMapper;
import com.jdec.platform.config.biz.mapper.SysMenuMapper;
import com.jdec.platform.config.biz.mapper.SysModuleFieldMapper;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 列表表头节点 Service 实现 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysMenuHeaderService implements SysMenuHeaderApi {

    /** 节点类型：菜单 */
    private static final Integer NODE_TYPE_MENU = 0;

    /** 节点类型：字段 */
    private static final Integer NODE_TYPE_FIELD = 1;

    /** 未选中 */
    private static final Integer CHECKED_NO = 0;

    /** 不省略 */
    private static final Integer ELLIPSIS_NO = 0;

    /** 可排序 */
    private static final Integer SORTED_YES = 1;

    /** 同级排序值起始值 */
    private static final Integer SORT_VALUE_FIRST = 1;

    /** 虚拟id前缀：菜单 */
    private static final String VIR_ID_PREFIX_MENU = "MENU";

    /** 虚拟id前缀：字段 */
    private static final String VIR_ID_PREFIX_FIELD = "Field";

    /** 顶层节点的父节点虚拟id */
    private static final String ROOT_PARENT_ID = "0";

    private final SysMenuHeaderMapper sysMenuHeaderMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysModuleFieldMapper sysModuleFieldMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysMenuHeaderResp getMenuHeaderTree(Long menuId) {
        if (menuId == null) {
            throw new BusinessException("菜单ID不能为空");
        }

        // 1. 优先读取该菜单已保存的表头节点配置
        List<SysMenuHeader> storedNodes =
                sysMenuHeaderMapper.selectList(
                        Wrappers.<SysMenuHeader>lambdaQuery()
                                .eq(SysMenuHeader::getMenuId, menuId)
                                .eq(SysMenuHeader::getSubjectId, AppContext.getSubjectId())
                                .eq(SysMenuHeader::getProjectNo, AppContext.getProjectNo())
                                .orderByAsc(SysMenuHeader::getSortValue, SysMenuHeader::getId));
        if (!storedNodes.isEmpty()) {
            return buildFromStored(menuId, storedNodes);
        }

        // 2. 无配置（首次查询）：以菜单本身为根，按菜单绑定的模块字段生成默认表头树
        SysMenuHeaderResp root = buildDefault(menuId);

        // 3. 首次生成的默认树落库（父引用为父节点虚拟id、顶层为 0，同级按顺序维护排序值），随后原样返回
        root.setSortValue(SORT_VALUE_FIRST);
        persistDefaultTree(root);
        return root;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMenuHeader(SysMenuHeaderReq req) {
        if (req == null) {
            throw new BusinessException("请求参数不能为空");
        }
        if (req.getMenuId() == null) {
            throw new BusinessException("所属菜单ID不能为空");
        }

        // 顶层节点（无 parentId 时）设置为 0
        if (req.getParentId() == null) {
            req.setParentId(ROOT_PARENT_ID);
        }

        // 递归保存节点及其子节点
        saveNodeRecursive(req, req.getParentId());
    }

    /**
     * 递归保存节点及其子节点
     *
     * @param req 节点请求对象
     * @param parentVirId 父节点虚拟id（顶层为 0）
     * @return 保存后的节点ID
     */
    private Long saveNodeRecursive(SysMenuHeaderReq req, String parentVirId) {
        SysMenuHeader entity = new SysMenuHeader();

        // 当前节点虚拟id（优先取请求值，否则按来源推导）
        String virId = resolveVirId(req);

        // 设置基本信息
        entity.setMenuId(req.getMenuId());
        entity.setParentId(parentVirId);
        entity.setVirId(virId);
        entity.setNodeType(req.getNodeType());
        entity.setSourceMenuId(req.getSourceMenuId());
        entity.setSourceFieldId(req.getSourceFieldId());
        entity.setLabel(req.getLabel());
        entity.setColors(req.getColors());
        entity.setWidth(req.getWidth());
        entity.setField(req.getField());
        entity.setSorted(req.getSorted());
        entity.setSearchType(req.getSearchType());
        entity.setEllipsis(req.getEllipsis());
        entity.setFixed(req.getFixed());
        entity.setChecked(req.getChecked());
        entity.setSortValue(req.getSortValue());

        // 设置上下文信息
        entity.setSubjectId(AppContext.getSubjectId());

        Long savedId;
        if (req.getId() != null) {
            // 更新操作
            entity.setId(req.getId());
            sysMenuHeaderMapper.updateById(entity);
            savedId = req.getId();
            log.debug("更新菜单表头节点，ID: {}", savedId);
        } else {
            // 新增操作
            sysMenuHeaderMapper.insert(entity);
            savedId = entity.getId();
            log.debug("新增菜单表头节点，ID: {}", savedId);
        }

        // 递归保存子节点
        if (req.getChildren() != null && !req.getChildren().isEmpty()) {
            for (SysMenuHeaderReq child : req.getChildren()) {
                // 子节点的 menuId 继承父节点（如果子节点未指定）
                if (child.getMenuId() == null) {
                    child.setMenuId(req.getMenuId());
                }
                // 递归保存子节点，传入当前节点虚拟id作为父引用
                saveNodeRecursive(child, virId);
            }
        }

        return savedId;
    }

    /** 解析节点虚拟id：优先取请求值，其次按 nodeType + 来源主键推导 */
    private String resolveVirId(SysMenuHeaderReq req) {
        if (req.getVirId() != null && !req.getVirId().trim().isEmpty()) {
            return req.getVirId();
        }
        if (Objects.equals(req.getNodeType(), NODE_TYPE_MENU) && req.getSourceMenuId() != null) {
            return menuVirId(req.getSourceMenuId());
        }
        if (Objects.equals(req.getNodeType(), NODE_TYPE_FIELD) && req.getSourceFieldId() != null) {
            return fieldVirId(req.getSourceFieldId());
        }
        return null;
    }

    // ==================== 已保存配置的组装 ====================

    private SysMenuHeaderResp buildFromStored(Long menuId, List<SysMenuHeader> nodes) {
        // 按父节点虚拟id分组（保留查询顺序，parent_id 为 null/0 的是顶层节点）
        Map<String, List<SysMenuHeader>> childrenByParent = new LinkedHashMap<>();
        List<SysMenuHeader> topNodes = new ArrayList<>();
        for (SysMenuHeader node : nodes) {
            String parentId = node.getParentId();
            if (parentId == null || ROOT_PARENT_ID.equals(parentId)) {
                topNodes.add(node);
            } else {
                childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(node);
            }
        }

        // 顶层仅一条 type=0 节点且即为本菜单自身时，直接作为根节点
        if (topNodes.size() == 1
                && isMenuNode(topNodes.getFirst())
                && menuId.equals(topNodes.getFirst().getSourceMenuId())) {
            return toStoredNodeResp(topNodes.getFirst(), childrenByParent);
        }

        // 其余情况以菜单本身作为根节点，把顶层配置挂到其 children 下
        SysMenu menu = selectMenuById(menuId);
        SysMenuHeaderResp root = new SysMenuHeaderResp();
        root.setMenuId(menuId);
        root.setNodeType(NODE_TYPE_MENU);
        root.setVirId(menuVirId(menuId));
        root.setParentId(ROOT_PARENT_ID);
        root.setSourceMenuId(menuId);
        root.setLabel(menu != null ? menu.getTitle() : null);
        root.setChecked(CHECKED_NO);
        root.setChildren(new ArrayList<>());
        for (SysMenuHeader top : topNodes) {
            root.getChildren().add(toStoredNodeResp(top, childrenByParent));
        }
        return root;
    }

    private SysMenuHeaderResp toStoredNodeResp(
            SysMenuHeader node, Map<String, List<SysMenuHeader>> childrenByParent) {
        SysMenuHeaderResp resp = new SysMenuHeaderResp();
        resp.setId(node.getId());
        resp.setMenuId(node.getMenuId());
        resp.setNodeType(node.getNodeType());
        resp.setVirId(node.getVirId());
        // parent_id 直接存父节点虚拟id，顶层统一为 0
        resp.setParentId(node.getParentId() != null ? node.getParentId() : ROOT_PARENT_ID);
        resp.setSourceMenuId(node.getSourceMenuId());
        resp.setSourceFieldId(node.getSourceFieldId());
        // label 直接取数据库存储值，不做反查映射
        resp.setLabel(node.getLabel());
        // 表头展示相关配置原样透传
        resp.setColors(node.getColors());
        resp.setWidth(node.getWidth());
        resp.setField(node.getField());
        resp.setSorted(node.getSorted());
        resp.setSearchType(node.getSearchType());
        resp.setEllipsis(node.getEllipsis());
        resp.setFixed(node.getFixed());
        resp.setChecked(node.getChecked());
        resp.setSortValue(node.getSortValue());

        // 子节点按父节点虚拟id分组
        List<SysMenuHeader> children =
                childrenByParent.getOrDefault(node.getVirId(), Collections.emptyList());
        if (!children.isEmpty() || isMenuNode(node)) {
            List<SysMenuHeaderResp> childResps =
                    children.stream()
                            .map(c -> toStoredNodeResp(c, childrenByParent))
                            .collect(Collectors.toList());
            resp.setChildren(childResps);
        }
        return resp;
    }

    // ==================== 首次查询的默认树生成 ====================

    private SysMenuHeaderResp buildDefault(Long menuId) {
        SysMenu menu = selectMenuById(menuId);
        if (menu == null) {
            throw new BusinessException("菜单不存在");
        }

        // 收集菜单及其全部子级菜单（树形）
        Map<Long, List<SysMenu>> childrenByPid = new LinkedHashMap<>();
        collectChildren(childrenByPid, menuId);

        // 收集所有菜单绑定到的模块ID，一次性查询模块字段
        Set<Long> moduleIds = new HashSet<>();
        if (menu.getModuleId() != null) {
            moduleIds.add(menu.getModuleId());
        }
        for (List<SysMenu> children : childrenByPid.values()) {
            for (SysMenu child : children) {
                if (child.getModuleId() != null) {
                    moduleIds.add(child.getModuleId());
                }
            }
        }
        Map<Long, List<SysModuleField>> fieldsByModule = selectFieldsGroupByModule(moduleIds);

        // 所有节点的 menu_id 统一取最顶层菜单ID，最顶层节点即本菜单自身
        return toDefaultMenu(menu, menuId, null, childrenByPid, fieldsByModule);
    }

    /**
     * 递归生成默认表头树节点
     *
     * @param menu 当前菜单
     * @param rootMenuId 最顶层菜单ID（所有节点的 menu_id 均取该值）
     * @param parentVirId 父节点虚拟id（顶层为 {@code null}，输出为 0）
     */
    private SysMenuHeaderResp toDefaultMenu(
            SysMenu menu,
            Long rootMenuId,
            String parentVirId,
            Map<Long, List<SysMenu>> childrenByPid,
            Map<Long, List<SysModuleField>> fieldsByModule) {
        String currentVirId = menuVirId(menu.getId());

        SysMenuHeaderResp resp = new SysMenuHeaderResp();
        resp.setMenuId(rootMenuId);
        resp.setNodeType(NODE_TYPE_MENU);
        resp.setVirId(currentVirId);
        resp.setParentId(parentVirId != null ? parentVirId : ROOT_PARENT_ID);
        resp.setSourceMenuId(menu.getId());
        resp.setLabel(menu.getTitle());
        resp.setSorted(SORTED_YES);
        resp.setEllipsis(ELLIPSIS_NO);
        resp.setChecked(CHECKED_NO);
        List<SysMenuHeaderResp> children = new ArrayList<>();

        // 当前菜单绑定模块下的全部字段
        List<SysModuleField> fields =
                fieldsByModule.getOrDefault(menu.getModuleId(), Collections.emptyList());
        for (SysModuleField field : fields) {
            SysMenuHeaderResp fieldResp = new SysMenuHeaderResp();
            fieldResp.setMenuId(rootMenuId);
            fieldResp.setNodeType(NODE_TYPE_FIELD);
            fieldResp.setVirId(fieldVirId(field.getId()));
            fieldResp.setParentId(currentVirId);
            fieldResp.setSourceFieldId(field.getId());
            fieldResp.setField(field.getColumnName());
            fieldResp.setLabel(firstNotBlank(field.getDisplayName(), field.getColumnName()));
            fieldResp.setSorted(SORTED_YES);
            fieldResp.setEllipsis(ELLIPSIS_NO);
            fieldResp.setChecked(CHECKED_NO);
            children.add(fieldResp);
        }

        // 递归挂载子级菜单节点
        List<SysMenu> subMenus = childrenByPid.getOrDefault(menu.getId(), Collections.emptyList());
        for (SysMenu subMenu : subMenus) {
            children.add(
                    toDefaultMenu(
                            subMenu, rootMenuId, currentVirId, childrenByPid, fieldsByModule));
        }
        resp.setChildren(children);
        return resp;
    }

    /**
     * 将首次生成的默认表头树递归落库
     *
     * <p>父节点引用（parent_id）直接落父节点虚拟id，顶层为 {@code 0}；同级节点按顺序从 {@code 1} 起维护
     * sort_value。节点按先根后子、深度优先顺序插入，因此生成的数据库主键递增，可辅助还原同级顺序。
     *
     * @param node 当前节点（含 virId/parentId/展示字段）
     */
    private void persistDefaultTree(SysMenuHeaderResp node) {
        SysMenuHeader entity = new SysMenuHeader();
        entity.setMenuId(node.getMenuId());
        entity.setParentId(node.getParentId());
        entity.setNodeType(node.getNodeType());
        entity.setVirId(node.getVirId());
        entity.setSourceMenuId(node.getSourceMenuId());
        entity.setSourceFieldId(node.getSourceFieldId());
        entity.setLabel(node.getLabel());
        entity.setColors(node.getColors());
        entity.setWidth(node.getWidth());
        entity.setField(node.getField());
        entity.setSorted(node.getSorted());
        entity.setSearchType(node.getSearchType());
        entity.setEllipsis(node.getEllipsis());
        entity.setFixed(node.getFixed());
        entity.setChecked(node.getChecked());
        entity.setSortValue(node.getSortValue());
        entity.setSubjectId(AppContext.getSubjectId());

        sysMenuHeaderMapper.insert(entity);
        node.setId(entity.getId());

        List<SysMenuHeaderResp> children = node.getChildren();
        if (children != null && !children.isEmpty()) {
            int sortValue = SORT_VALUE_FIRST;
            for (SysMenuHeaderResp child : children) {
                // 同级按顺序维护排序值
                child.setSortValue(sortValue++);
                persistDefaultTree(child);
            }
        }
    }

    // ==================== 通用查询工具 ====================

    private void collectChildren(Map<Long, List<SysMenu>> childrenByPid, Long parentId) {
        List<SysMenu> children = selectChildrenByPid(parentId);
        if (children.isEmpty()) {
            return;
        }
        childrenByPid.put(parentId, children);
        for (SysMenu child : children) {
            collectChildren(childrenByPid, child.getId());
        }
    }

    private List<SysMenu> selectChildrenByPid(Long pid) {
        Long subjectId = AppContext.getSubjectId();
        String projectNo = AppContext.getProjectNo();
        return sysMenuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getPid, pid)
                        .eq(subjectId != null, SysMenu::getSubjectId, subjectId)
                        .eq(projectNo != null, SysMenu::getProjectNo, projectNo)
                        .orderByAsc(SysMenu::getSortOrder)
                        .orderByAsc(SysMenu::getId));
    }

    private SysMenu selectMenuById(Long menuId) {
        Long subjectId = AppContext.getSubjectId();
        String projectNo = AppContext.getProjectNo();
        return sysMenuMapper.selectOne(
                new LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getId, menuId)
                        .eq(subjectId != null, SysMenu::getSubjectId, subjectId)
                        .eq(projectNo != null, SysMenu::getProjectNo, projectNo)
                        .last("LIMIT 1"));
    }

    private Map<Long, List<SysModuleField>> selectFieldsGroupByModule(Set<Long> moduleIds) {
        if (moduleIds == null || moduleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysModuleField> fields =
                sysModuleFieldMapper.selectList(
                        Wrappers.<SysModuleField>lambdaQuery()
                                .in(SysModuleField::getModuleId, moduleIds)
                                .orderByAsc(SysModuleField::getSortOrder)
                                .orderByAsc(SysModuleField::getId));
        return fields.stream()
                .collect(
                        Collectors.groupingBy(
                                SysModuleField::getModuleId,
                                LinkedHashMap::new,
                                Collectors.toList()));
    }

    // ==================== 小工具 ====================

    /** 菜单节点虚拟id：MENU_{菜单ID} */
    private String menuVirId(Long menuId) {
        return VIR_ID_PREFIX_MENU + "_" + menuId;
    }

    /** 字段节点虚拟id：Field_{字段ID} */
    private String fieldVirId(Long fieldId) {
        return VIR_ID_PREFIX_FIELD + "_" + fieldId;
    }

    private boolean isMenuNode(SysMenuHeader node) {
        return Objects.equals(node.getNodeType(), NODE_TYPE_MENU);
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
