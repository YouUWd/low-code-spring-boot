package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.SysConfigCategoryApi;
import com.jdec.platform.config.api.SysRoleApi;
import com.jdec.platform.config.api.SysRolePermissionApi;
import com.jdec.platform.config.api.SystemInitApi;
import com.jdec.platform.config.api.dto.request.RoleUserReq;
import com.jdec.platform.config.api.dto.request.SaveConfigCategoryReq;
import com.jdec.platform.config.api.dto.request.SaveConfigItemReq;
import com.jdec.platform.config.api.dto.request.SaveRoleMenuReq;
import com.jdec.platform.config.api.dto.request.SaveRoleSpecialPermissionReq;
import com.jdec.platform.config.api.dto.request.SaveRoleUserReq;
import com.jdec.platform.config.api.dto.request.SysConfigCategorySaveReq;
import com.jdec.platform.config.api.dto.request.SysConfigItemSaveReq;
import com.jdec.platform.config.api.dto.response.RoleConfigPageResp;
import com.jdec.platform.config.api.dto.response.SystemInitResp;
import com.jdec.platform.config.biz.entity.SysConfigCategory;
import com.jdec.platform.config.biz.entity.SysMenu;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysProjectSubject;
import com.jdec.platform.config.biz.entity.SysRole;
import com.jdec.platform.config.biz.entity.SysSpecialPermission;
import com.jdec.platform.config.biz.init.ConfigCategoryInitNode;
import com.jdec.platform.config.biz.init.ConfigItemInitNode;
import com.jdec.platform.config.biz.init.MenuInitNode;
import com.jdec.platform.config.biz.init.ModuleInitNode;
import com.jdec.platform.config.biz.init.ProjectSubjectInitNode;
import com.jdec.platform.config.biz.init.SpecialPermissionInitNode;
import com.jdec.platform.config.biz.mapper.SysConfigCategoryMapper;
import com.jdec.platform.config.biz.mapper.SysMenuMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.mapper.SysProjectSubjectMapper;
import com.jdec.platform.config.biz.mapper.SysRoleMapper;
import com.jdec.platform.config.biz.mapper.SysSpecialPermissionMapper;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.enums.EffectiveTypeEnum;
import com.jdec.platform.shared.enums.UserTypeEnum;
import com.jdec.platform.shared.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;

/**
 * 系统初始化服务。
 *
 * <p>以 {@code init/project_subject.json} 中的项目主体清单为驱动，逐个主体初始化模块、菜单、通用配置、特殊权限，
 * 并为每个主体创建超级管理员角色、绑定全部菜单与特殊权限。
 *
 * <p><strong>主键ID</strong>：项目主体 / 模块 / 菜单 / 特殊权限的主键均取自初始化 JSON（依次递增、跨环境固定）， 落库走各 Mapper 的 {@code
 * insertWithId}；通用配置分类与配置项的主键仍由数据库自增。
 *
 * <p><strong>并发控制</strong>：{@link #initSystem()} 用 JVM 互斥锁保证同一实例内不会并发执行，重复调用直接报错。
 *
 * <p><strong>事务边界</strong>：按项目主体划分。单个主体的「项目主体 / 模块 / 菜单 / 通用配置 / 特殊权限」在同一个事务内完成，
 * 任一环节失败整体回滚该主体；超级管理员角色的创建与授权<strong>不在事务内</strong>，因为 {@code SysRoleService.saveRoleUser} 内部会跨库读取
 * HR 数据，包在事务里会导致数据源无法切换。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SystemInitService implements SystemInitApi {

    /** 初始化 JSON 资源目录 */
    private static final String INIT_DIR = "init/";

    /** 超级管理员角色名称 */
    private static final String SUPER_ADMIN_ROLE_NAME = "超级管理员";

    /** 超级管理员默认绑定的用户ID：所有主体的超级管理员均绑定该用户，不再绑定主体 */
    private static final String SUPER_ADMIN_USER_ID = "162";

    /** 根节点父ID */
    private static final long ROOT_ID = 0L;

    /** 初始化互斥锁：防止同一实例内并发重复初始化 */
    private final ReentrantLock initLock = new ReentrantLock();

    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final SysProjectSubjectMapper projectSubjectMapper;
    private final SysModuleMapper moduleMapper;
    private final SysMenuMapper menuMapper;
    private final SysConfigCategoryMapper configCategoryMapper;
    private final SysSpecialPermissionMapper specialPermissionMapper;
    private final SysRoleMapper roleMapper;
    private final SysConfigCategoryApi configCategoryApi;
    private final SysRoleApi roleApi;
    private final SysRolePermissionApi rolePermissionApi;

    @Override
    public SystemInitResp initSystem() {
        if (!initLock.tryLock()) {
            throw new BusinessException("系统初始化正在执行中，请勿重复调用");
        }
        try {
            return doInitSystem();
        } finally {
            // 初始化接口无入参上下文，结束时清理线程，避免污染线程池后续请求
            AppContext.remove();
            initLock.unlock();
        }
    }

    /** 初始化主流程：以项目主体清单为驱动逐个初始化，单个主体失败不影响其它主体 */
    private SystemInitResp doInitSystem() {
        List<ProjectSubjectInitNode> projects =
                readInitJson("project_subject.json", new TypeReference<>() {});
        if (CollectionUtils.isEmpty(projects)) {
            throw new BusinessException("未找到项目主体初始化配置 init/project_subject.json");
        }

        List<ModuleInitNode> moduleTemplates =
                readInitJson("module.json", new TypeReference<>() {});
        List<MenuInitNode> menuTemplates = readInitJson("menu.json", new TypeReference<>() {});
        List<ConfigCategoryInitNode> configTemplates =
                readInitJson("config.json", new TypeReference<>() {});
        List<SpecialPermissionInitNode> permissionTemplates =
                readInitJson("special_permission.json", new TypeReference<>() {});

        List<SystemInitResp.ProjectInitResult> results = new ArrayList<>();
        int successCount = 0;
        for (ProjectSubjectInitNode project : projects) {
            SystemInitResp.ProjectInitResult result = new SystemInitResp.ProjectInitResult();
            result.setSubjectId(project.getSubjectId());
            result.setProjectNo(project.getProjectNo());
            result.setSubjectName(project.getSubjectName());
            List<String> steps = new ArrayList<>();
            result.setSteps(steps);
            try {
                initOneProject(
                        project,
                        moduleTemplates,
                        menuTemplates,
                        configTemplates,
                        permissionTemplates,
                        steps);
                result.setSuccess(true);
                successCount++;
            } catch (Exception e) {
                // 单个主体失败不影响其它主体，记录明细继续执行
                result.setSuccess(false);
                result.setErrorMsg(e.getMessage());
                steps.add("初始化中断于失败步骤");
                log.error(
                        "项目主体初始化失败: projectNo={}, subjectId={}, subjectName={}",
                        project.getProjectNo(),
                        project.getSubjectId(),
                        project.getSubjectName(),
                        e);
            }
            results.add(result);
        }

        SystemInitResp resp = new SystemInitResp();
        resp.setTotalCount(results.size());
        resp.setSuccessCount(successCount);
        resp.setProjects(results);
        return resp;
    }

    /** 初始化单个项目主体：切换上下文后按顺序初始化各类数据，最后创建超级管理员并授权 */
    private void initOneProject(
            ProjectSubjectInitNode project,
            List<ModuleInitNode> moduleTemplates,
            List<MenuInitNode> menuTemplates,
            List<ConfigCategoryInitNode> configTemplates,
            List<SpecialPermissionInitNode> permissionTemplates,
            List<String> steps) {

        String projectNo = project.getProjectNo();
        Long subjectId = project.getSubjectId();
        // 切换上下文，后续所有 Service 调用均以当前项目主体作为归属标识
        AppContext.setContext(projectNo, subjectId);

        // 主体数据（项目主体/模块/菜单/通用配置/特殊权限）同库同事务，任一环节失败整体回滚。
        // 步骤信息暂存到临时列表，事务提交成功后才并入结果，避免回滚后残留“已新增”等误导性描述。
        List<String> txSteps = new ArrayList<>();
        ProjectInitData data =
                transactionTemplate.execute(
                        status ->
                                initProjectData(
                                        project,
                                        moduleTemplates,
                                        menuTemplates,
                                        configTemplates,
                                        permissionTemplates,
                                        txSteps));
        steps.addAll(txSteps);

        // 超级管理员角色必须放在事务外：saveRoleUser 内部跨库读 HR，事务内会导致数据源无法切换
        initSuperAdminRole(projectNo, subjectId, data.menuIds(), data.permissionIds(), steps);
    }

    /** 在单个事务内初始化该主体的项目主体、模块、菜单、通用配置、特殊权限，返回角色授权所需的ID集合 */
    private ProjectInitData initProjectData(
            ProjectSubjectInitNode project,
            List<ModuleInitNode> moduleTemplates,
            List<MenuInitNode> menuTemplates,
            List<ConfigCategoryInitNode> configTemplates,
            List<SpecialPermissionInitNode> permissionTemplates,
            List<String> steps) {

        String projectNo = project.getProjectNo();
        Long subjectId = project.getSubjectId();

        initProjectSubject(project, steps);

        Map<String, Long> moduleCodeIdMap =
                initModules(projectNo, subjectId, moduleTemplates, steps);

        List<Long> menuIds = initMenus(menuTemplates, moduleCodeIdMap, steps);

        initConfigs(configTemplates, steps);

        List<Long> permissionIds =
                initSpecialPermissions(projectNo, subjectId, permissionTemplates, steps);

        return new ProjectInitData(menuIds, permissionIds);
    }

    /** 单个主体事务内初始化产出的、供角色授权使用的ID集合 */
    private record ProjectInitData(List<Long> menuIds, List<Long> permissionIds) {}

    /** 初始化项目主体记录 */
    private void initProjectSubject(ProjectSubjectInitNode project, List<String> steps) {
        SysProjectSubject existing =
                projectSubjectMapper.selectOne(
                        Wrappers.<SysProjectSubject>lambdaQuery()
                                .eq(SysProjectSubject::getProjectNo, project.getProjectNo())
                                .eq(SysProjectSubject::getSubjectId, project.getSubjectId())
                                .last("limit 1"));
        if (existing != null) {
            steps.add("项目主体: 已存在跳过");
            return;
        }
        SysProjectSubject entity = new SysProjectSubject();
        entity.setId(requireId(project.getId(), "项目主体", project.getSubjectName()));
        entity.setSubjectName(project.getSubjectName());
        entity.setCompanyName(project.getCompanyName());
        entity.setAlias(project.getAlias());
        entity.setSubjectId(project.getSubjectId());
        entity.setProjectNo(project.getProjectNo());
        projectSubjectMapper.insertWithId(entity);
        steps.add("项目主体: 新增");
    }

    /** 初始化模块树，返回 模块编码 → 模块ID 映射（已存在时基于现有数据构建，供菜单关联使用） */
    private Map<String, Long> initModules(
            String projectNo, Long subjectId, List<ModuleInitNode> templates, List<String> steps) {
        List<SysModule> existing =
                moduleMapper.selectList(
                        Wrappers.<SysModule>lambdaQuery()
                                .eq(SysModule::getProjectNo, projectNo)
                                .eq(SysModule::getSubjectId, subjectId));
        Map<String, Long> codeIdMap = new LinkedHashMap<>();
        if (!existing.isEmpty()) {
            existing.forEach(m -> codeIdMap.putIfAbsent(m.getModuleCode(), m.getId()));
            steps.add("模块: 已存在跳过(" + existing.size() + "个)");
            return codeIdMap;
        }
        int count = 0;
        for (ModuleInitNode node : templates) {
            count += saveModuleTree(projectNo, subjectId, node, ROOT_ID, codeIdMap);
        }
        steps.add("模块: 新增 " + count + " 个");
        return codeIdMap;
    }

    /**
     * 递归保存模块树：先保存父模块拿到ID，再以该ID为 parentId 保存子模块，返回本次保存的模块数量。
     *
     * <p>此处直接落 {@code sys_module} 表，未走 {@code SysModuleApi.saveModule}：该接口会连带维护 {@code
     * sys_module_field} / {@code sys_module_status} 两张关联表，而初始化环境不一定建表；模块初始化只需模块主体信息。
     */
    private int saveModuleTree(
            String projectNo,
            Long subjectId,
            ModuleInitNode node,
            Long parentModuleId,
            Map<String, Long> codeIdMap) {
        if (node.getModuleCode() == null || node.getModuleCode().trim().isEmpty()) {
            throw new BusinessException("模块配置缺少 moduleCode: " + node.getModuleName());
        }
        if (node.getModuleName() == null || node.getModuleName().trim().isEmpty()) {
            throw new BusinessException("模块配置缺少 moduleName: " + node.getModuleCode());
        }
        if (codeIdMap.containsKey(node.getModuleCode())) {
            throw new BusinessException("模块编码重复: " + node.getModuleCode());
        }

        Long moduleId = requireId(node.getId(), "模块", node.getModuleName());
        LocalDateTime now = LocalDateTime.now();
        SysModule entity = new SysModule();
        entity.setId(moduleId);
        entity.setProjectNo(projectNo);
        entity.setSubjectId(subjectId);
        entity.setModuleCode(node.getModuleCode());
        entity.setModuleName(node.getModuleName());
        entity.setModuleDesc(node.getModuleDesc());
        entity.setCategory(node.getCategory() == null ? 1 : node.getCategory());
        entity.setSourceSubjects(node.getSourceSubjects());
        entity.setPrimaryTable(node.getPrimaryTable());
        entity.setParentId(parentModuleId);
        entity.setSortOrder(node.getSortOrder() == null ? 0 : node.getSortOrder());
        entity.setCreatedDate(now);
        entity.setUpdatedDate(now);
        moduleMapper.insertWithId(entity);

        codeIdMap.put(node.getModuleCode(), moduleId);
        int count = 1;

        if (!CollectionUtils.isEmpty(node.getChildren())) {
            for (ModuleInitNode child : node.getChildren()) {
                count += saveModuleTree(projectNo, subjectId, child, moduleId, codeIdMap);
            }
        }
        return count;
    }

    /** 初始化菜单树，返回该主体下的全部菜单ID（已存在时返回现有菜单ID） */
    private List<Long> initMenus(
            List<MenuInitNode> templates, Map<String, Long> moduleCodeIdMap, List<String> steps) {
        Long subjectId = AppContext.getSubjectId();
        String projectNo = AppContext.getProjectNo();
        List<SysMenu> existing =
                menuMapper.selectList(
                        Wrappers.<SysMenu>lambdaQuery()
                                .eq(SysMenu::getProjectNo, projectNo)
                                .eq(SysMenu::getSubjectId, subjectId));
        if (!existing.isEmpty()) {
            steps.add("菜单: 已存在跳过(" + existing.size() + "个)");
            return existing.stream().map(SysMenu::getId).collect(Collectors.toList());
        }
        List<Long> menuIds = new ArrayList<>();
        for (MenuInitNode node : templates) {
            saveMenuTree(node, ROOT_ID, moduleCodeIdMap, menuIds);
        }
        steps.add("菜单: 新增 " + menuIds.size() + " 个");
        return menuIds;
    }

    /**
     * 递归保存菜单树：以 JSON 中的 id 作为主键落库，再以该 id 为 pid 保存子菜单。
     *
     * <p>此处直接落 {@code sys_menu} 表，未走 {@code SysMenuService.saveMenuNode}：该接口以「id 非空即编辑」判定，
     * 无法承载初始化指定的主键。初始化数据为受控模板，无需重复名/环校验，也不产生审计记录。
     */
    private void saveMenuTree(
            MenuInitNode node,
            Long parentMenuId,
            Map<String, Long> moduleCodeIdMap,
            List<Long> menuIds) {
        Long menuId = requireId(node.getId(), "菜单", node.getTitle());
        LocalDateTime now = LocalDateTime.now();
        SysMenu entity = new SysMenu();
        entity.setId(menuId);
        entity.setPid(parentMenuId);
        entity.setSubjectId(AppContext.getSubjectId());
        entity.setProjectNo(AppContext.getProjectNo());
        entity.setTitle(node.getTitle());
        entity.setParam(node.getParam());
        entity.setMenuType(node.getMenuType());
        entity.setStructuralType(node.getStructuralType());
        entity.setPersonalizeFlag(
                node.getPersonalizeFlag() == null ? 0 : node.getPersonalizeFlag());
        entity.setCategory(node.getCategory());
        entity.setImgDefault(node.getImgDefault());
        entity.setImgActive(node.getImgActive());
        entity.setSortOrder(node.getSortOrder());
        entity.setShowed(node.getShowed() == null ? 1 : node.getShowed());
        entity.setEnabled(node.getEnabled() == null ? 1 : node.getEnabled());
        entity.setDocUrl(node.getDocUrl());
        entity.setDocName(node.getDocName());
        entity.setVideoUrl(node.getVideoUrl());
        entity.setVideoName(node.getVideoName());
        if (node.getModuleCode() != null && !node.getModuleCode().isEmpty()) {
            Long moduleId = moduleCodeIdMap.get(node.getModuleCode());
            if (moduleId == null) {
                log.warn("菜单[{}]引用的模块编码[{}]未找到，该菜单将不关联模块", node.getTitle(), node.getModuleCode());
            }
            entity.setModuleId(moduleId);
        }
        entity.setCreatedDate(now);
        entity.setUpdatedDate(now);
        menuMapper.insertWithId(entity);
        menuIds.add(menuId);

        if (!CollectionUtils.isEmpty(node.getChildren())) {
            for (MenuInitNode child : node.getChildren()) {
                saveMenuTree(child, menuId, moduleCodeIdMap, menuIds);
            }
        }
    }

    /** 初始化通用配置（分类 + 配置项） */
    private void initConfigs(List<ConfigCategoryInitNode> templates, List<String> steps) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        Long existingCount =
                configCategoryMapper.selectCount(
                        Wrappers.<SysConfigCategory>lambdaQuery()
                                .eq(SysConfigCategory::getProjectNo, projectNo)
                                .eq(SysConfigCategory::getSubjectId, subjectId));
        if (existingCount != null && existingCount > 0) {
            steps.add("通用配置: 已存在跳过(" + existingCount + "个分类)");
            return;
        }
        int categoryCount = 0;
        int itemCount = 0;
        for (ConfigCategoryInitNode template : templates) {
            SaveConfigCategoryReq categoryReq = new SaveConfigCategoryReq();
            SysConfigCategorySaveReq category = new SysConfigCategorySaveReq();
            category.setLabel(template.getLabel());
            category.setCategoryAlias(template.getCategoryAlias());
            category.setDescription(template.getDescription());
            category.setFormat(template.getFormat());
            category.setSource(template.getSource());
            category.setSort(template.getSort());
            category.setStatus(template.getStatus());
            categoryReq.setConfig(category);
            configCategoryApi.saveCategory(categoryReq);
            categoryCount++;

            if (!CollectionUtils.isEmpty(template.getItems())) {
                for (ConfigItemInitNode item : template.getItems()) {
                    itemCount += saveConfigItemTree(template.getCategoryAlias(), item, ROOT_ID);
                }
            }
        }
        steps.add("通用配置: 新增 " + categoryCount + " 个分类、" + itemCount + " 个配置项");
    }

    /** 递归保存配置项树，返回保存的配置项数量 */
    private int saveConfigItemTree(
            String categoryAlias, ConfigItemInitNode node, Long parentItemId) {
        SaveConfigItemReq req = new SaveConfigItemReq();
        SysConfigItemSaveReq item = new SysConfigItemSaveReq();
        item.setCategoryAlias(categoryAlias);
        item.setPid(parentItemId);
        item.setLabel(node.getLabel());
        item.setValue(node.getValue());
        item.setDescription(node.getDescription());
        item.setExtra(node.getExtra());
        item.setSort(node.getSort());
        item.setStatus(node.getStatus());
        req.setConfig(item);

        Long itemId = configCategoryApi.saveItem(req);
        int count = 1;

        if (!CollectionUtils.isEmpty(node.getChildren())) {
            for (ConfigItemInitNode child : node.getChildren()) {
                count += saveConfigItemTree(categoryAlias, child, itemId);
            }
        }
        return count;
    }

    /** 初始化特殊权限，返回该主体下的全部特殊权限ID（已存在时返回现有ID） */
    private List<Long> initSpecialPermissions(
            String projectNo,
            Long subjectId,
            List<SpecialPermissionInitNode> templates,
            List<String> steps) {
        List<SysSpecialPermission> existing =
                specialPermissionMapper.selectList(
                        Wrappers.<SysSpecialPermission>lambdaQuery()
                                .eq(SysSpecialPermission::getProjectNo, projectNo)
                                .eq(SysSpecialPermission::getSubjectId, subjectId));
        if (!existing.isEmpty()) {
            steps.add("特殊权限: 已存在跳过(" + existing.size() + "个)");
            return existing.stream().map(SysSpecialPermission::getId).collect(Collectors.toList());
        }
        List<Long> ids = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (SpecialPermissionInitNode node : templates) {
            Long id = requireId(node.getId(), "特殊权限", node.getName());
            SysSpecialPermission entity = new SysSpecialPermission();
            entity.setId(id);
            entity.setCode(node.getCode());
            entity.setName(node.getName());
            entity.setDescription(node.getDescription());
            entity.setSort(node.getSort());
            entity.setStatus(node.getStatus() == null ? 1 : node.getStatus());
            entity.setSubjectId(subjectId);
            entity.setProjectNo(projectNo);
            entity.setCreatedDate(now);
            entity.setUpdatedDate(now);
            specialPermissionMapper.insertWithId(entity);
            ids.add(id);
        }
        steps.add("特殊权限: 新增 " + ids.size() + " 个");
        return ids;
    }

    /**
     * 创建（或复用）超级管理员角色，并绑定全部菜单与特殊权限。
     *
     * <p>角色授权对象固定为 {@link #SUPER_ADMIN_USER_ID} 指定的用户，不再绑定主体。
     */
    private void initSuperAdminRole(
            String projectNo,
            Long subjectId,
            List<Long> menuIds,
            List<Long> permissionIds,
            List<String> steps) {
        SysRole role =
                roleMapper.selectOne(
                        Wrappers.<SysRole>lambdaQuery()
                                .eq(SysRole::getRoleName, SUPER_ADMIN_ROLE_NAME)
                                .eq(SysRole::getProjectNo, projectNo)
                                .eq(SysRole::getSubjectId, subjectId)
                                .last("limit 1"));

        Long roleId;
        if (role != null) {
            roleId = role.getId();
            steps.add("超级管理员角色: 已存在跳过");
        } else {
            SaveRoleUserReq req = new SaveRoleUserReq();
            req.setRoleName(SUPER_ADMIN_ROLE_NAME);
            // 默认不绑定主体，改为绑定指定用户（各主体的超级管理员均绑定该用户）
            RoleUserReq userReq = new RoleUserReq();
            userReq.setUserId(SUPER_ADMIN_USER_ID);
            userReq.setUserType(UserTypeEnum.INSIDE.getCode());
            userReq.setEffectiveType(EffectiveTypeEnum.PERMANENT.getCode());
            userReq.setStatus(0);
            req.setUserInsideList(List.of(userReq));
            RoleConfigPageResp resp = roleApi.saveRoleUser(req);
            roleId = resp == null ? null : resp.getId();
            if (roleId == null) {
                throw new BusinessException("超级管理员角色创建失败，未返回角色ID");
            }
            steps.add("超级管理员角色: 新增");
        }

        if (!CollectionUtils.isEmpty(menuIds)) {
            SaveRoleMenuReq menuReq = new SaveRoleMenuReq();
            menuReq.setRoleId(roleId);
            menuReq.setMenuIds(menuIds);
            menuReq.setCheckFlag(1);
            menuReq.setPermissionTypeName("模块权限-系统菜单权限");
            rolePermissionApi.saveRoleMenu(menuReq);
            steps.add("角色菜单绑定: " + menuIds.size() + " 个");
        }

        if (!CollectionUtils.isEmpty(permissionIds)) {
            SaveRoleSpecialPermissionReq permissionReq = new SaveRoleSpecialPermissionReq();
            permissionReq.setRoleId(roleId);
            permissionReq.setSpecialPermissionIds(permissionIds);
            permissionReq.setCheckFlag(1);
            permissionReq.setPermissionTypeName("其它权限");
            rolePermissionApi.saveRoleSpecialPermission(permissionReq);
            steps.add("角色特殊权限绑定: " + permissionIds.size() + " 个");
        }
    }

    /** 读取初始化 JSON 中指定的主键ID，缺失时直接中断，避免以空主键落库 */
    private Long requireId(Long id, String dataType, String dataName) {
        if (id == null) {
            throw new BusinessException("初始化配置缺少主键ID: " + dataType + "[" + dataName + "]");
        }
        return id;
    }

    /** 读取 classpath 下的初始化 JSON */
    private <T> T readInitJson(String fileName, TypeReference<T> typeReference) {
        String path = INIT_DIR + fileName;
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new BusinessException("初始化配置不存在: " + path);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, typeReference);
        } catch (IOException e) {
            throw new BusinessException("读取初始化配置失败: " + path + "，原因: " + e.getMessage());
        }
    }
}
