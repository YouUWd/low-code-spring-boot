package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.dto.common.*;
import com.jdec.platform.config.api.dto.request.MoveModuleReq;
import com.jdec.platform.config.api.dto.request.SaveModuleReq;
import com.jdec.platform.config.api.dto.request.SaveModuleReq.SaveSysModuleReq;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.config.biz.audit.event.SysModuleChangeEvent;
import com.jdec.platform.config.biz.check.CheckConstant;
import com.jdec.platform.config.biz.check.ReferenceCheckResult;
import com.jdec.platform.config.biz.check.ReferenceChecker;
import com.jdec.platform.config.biz.check.ReferenceContext;
import com.jdec.platform.config.biz.entity.*;
import com.jdec.platform.config.biz.mapper.*;
import com.jdec.platform.hr.api.SubjectApi;
import com.jdec.platform.shared.annotation.ConfigChangeNotify;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import com.jdec.platform.shared.enums.ChangeType;
import com.jdec.platform.shared.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 模块管理业务实现类 负责模块、关联表、字段配置和状态配置的完整生命周期管理 */
@Service
@RequiredArgsConstructor
@Slf4j
@com.jdec.platform.shared.datasource.DataSource(DataSourceConstants.CONFIG_ENGINE)
public class SysModuleService implements SysModuleApi, ReferenceChecker {

    // 权限变更类型位掩码
    private static final int PERM_CHANGE_MODULE_RESET = 1; // 1: 重置整个模块的字段权限
    private static final int PERM_CHANGE_TABLE_RESET = 1 << 1; // 2: 重置只读关联表物理字段的权限
    private static final int PERM_CHANGE_TABLE_CLEAN = 1 << 2; // 4: 清理删除表相关字段的角色权限
    private static final int PERM_CHANGE_SIMPLE_CLEAN = 1 << 3; // 8: 清理删除简单物理字段的角色权限
    private static final int PERM_CHANGE_COMBINE_CLEAN = 1 << 4; // 16: 清理删除组合字段的角色权限

    private static final ThreadLocal<Integer> PERMISSION_CHANGES = ThreadLocal.withInitial(() -> 0);

    private final SysModuleMapper sysModuleMapper;
    private final SysModuleTableMapper sysModuleTableMapper;
    private final SysModuleFieldMapper sysModuleFieldMapper;
    private final SysModuleStatusMapper sysModuleStatusMapper;
    private final SysStatusMapper sysStatusMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysFieldValueMapper sysFieldValueMapper;
    private final SysInteractionPermissionMapper sysInteractionPermissionMapper;
    private final SysRoleModuleFieldPermissionMapper sysRoleModuleFieldPermissionMapper;
    private final SysApprovalChainTypeMapper sysApprovalChainTypeMapper;
    private final SysWechatTemplateMapper sysWechatTemplateMapper;
    private final SysWechatTemplateParamMapper sysWechatTemplateParamMapper;
    private final SysFieldMapper sysFieldMapper;
    private final SysModuleHeaderMapper sysModuleHeaderMapper;
    private final DataSourceResolver dataSourceResolver;
    private final ObjectMapper objectMapper;
    private final SubjectApi subjectApi;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<SysModuleListResp> listModules(String projectNo, Long subjectId, Integer category) {
        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getProjectNo, projectNo);
        wrapper.eq(SysModule::getSubjectId, subjectId);
        wrapper.orderByAsc(SysModule::getSortOrder);
        List<SysModule> modules = sysModuleMapper.selectList(wrapper);

        return modules.stream().map(this::toModuleListResp).collect(Collectors.toList());
    }

    private SysModuleListResp toModuleListResp(SysModule module) {
        if (module == null) {
            return null;
        }
        SysModuleListResp resp = new SysModuleListResp();
        resp.setId(module.getId());
        resp.setProjectNo(module.getProjectNo());
        resp.setSubjectId(module.getSubjectId());
        resp.setModuleCode(module.getModuleCode());
        resp.setModuleName(module.getModuleName());
        resp.setModuleDesc(module.getModuleDesc());
        resp.setSortOrder(module.getSortOrder());
        resp.setCreatedBy(module.getCreatedBy());
        resp.setCreatedDate(
                module.getCreatedDate() != null ? module.getCreatedDate().toString() : null);
        resp.setUpdatedBy(module.getUpdatedBy());
        resp.setUpdatedDate(
                module.getUpdatedDate() != null ? module.getUpdatedDate().toString() : null);
        return resp;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SysModuleCompleteResp getModuleCompleteById(
            String projectNo, Long subjectId, Long moduleId) {
        SysModule module = sysModuleMapper.selectById(moduleId);
        if (module == null) {
            return null;
        }
        if (projectNo != null && !Objects.equals(module.getProjectNo(), projectNo)) {
            return null;
        }
        if (subjectId != null && !Objects.equals(module.getSubjectId(), subjectId)) {
            return null;
        }
        return buildModuleCompleteResp(module);
    }

    @Override
    @Transactional
    public MoveModuleResp moveModule(MoveModuleReq request) {
        Long moduleId = request.getModuleId();
        Integer targetSortOrder = request.getTargetSortOrder();

        // 参数验证
        if (moduleId == null) {
            throw new BusinessException(400, "模块ID不能为空");
        }
        if (targetSortOrder == null || targetSortOrder < 0) {
            throw new BusinessException(400, "目标排序顺序不能为空且必须大于等于0");
        }

        SysModule module = sysModuleMapper.selectById(moduleId);
        if (module == null) {
            throw new BusinessException(404, "模块不存在");
        }

        List<SysModule> allModules =
                new ArrayList<>(
                        sysModuleMapper.selectList(
                                Wrappers.<SysModule>lambdaQuery()
                                        .eq(SysModule::getProjectNo, module.getProjectNo())
                                        .eq(SysModule::getSubjectId, module.getSubjectId())
                                        .orderByAsc(SysModule::getSortOrder)));

        allModules.removeIf(s -> s.getId().equals(moduleId));
        int insertPos = Math.min(targetSortOrder, allModules.size());
        allModules.add(insertPos, module);
        reshuffleAndUpdate(allModules);

        // 重新查询最新状态并构造精简响应
        SysModule latest = sysModuleMapper.selectById(moduleId);
        MoveModuleResp resp = new MoveModuleResp();
        resp.setId(latest.getId());
        resp.setSortOrder(latest.getSortOrder());
        resp.setUpdatedDate(latest.getUpdatedDate());

        // 发布移动事件
        eventPublisher.publishEvent(
                com.jdec.platform.config.biz.audit.event.SysModuleChangeEvent.createMoveEvent(
                        module.getProjectNo(),
                        module.getSubjectId(),
                        moduleId,
                        request,
                        module.getModuleName(),
                        "根模块",
                        "根模块"));

        return resp;
    }

    /** 对一组节点进行重编号，并仅对 sortOrder 发生变化的记录执行 DB 更新。 */
    private void reshuffleAndUpdate(List<SysModule> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            SysModule node = nodes.get(i);
            if (node.getSortOrder() == null || node.getSortOrder() != i) {
                node.setSortOrder(i);
                final int sortOrder = i;
                sysModuleMapper.update(
                        null,
                        Wrappers.<SysModule>lambdaUpdate()
                                .eq(SysModule::getId, node.getId())
                                .set(SysModule::getSortOrder, sortOrder));
            }
        }
    }

    /** 构建模块完整信息响应 */
    private SysModuleCompleteResp buildModuleCompleteResp(SysModule module) {
        SysModuleCompleteResp resp = new SysModuleCompleteResp();

        // 1. 设置模块基本信息
        SysModuleCompleteResp.ModuleInfo moduleInfo = new SysModuleCompleteResp.ModuleInfo();
        moduleInfo.setId(module.getId());
        moduleInfo.setProjectNo(module.getProjectNo());
        moduleInfo.setSubjectId(module.getSubjectId());
        moduleInfo.setModuleCode(module.getModuleCode());
        moduleInfo.setModuleName(module.getModuleName());
        moduleInfo.setModuleDesc(module.getModuleDesc());
        moduleInfo.setSortOrder(module.getSortOrder());
        moduleInfo.setCreatedBy(module.getCreatedBy());
        moduleInfo.setCreatedDate(module.getCreatedDate());
        moduleInfo.setUpdatedBy(module.getUpdatedBy());
        moduleInfo.setUpdatedDate(module.getUpdatedDate());
        resp.setModule(moduleInfo);

        Long moduleId = module.getId();

        // 2. 获取关联表 (sys_module_table)
        List<SysModuleTable> tables =
                sysModuleTableMapper.selectList(
                        Wrappers.<SysModuleTable>lambdaQuery()
                                .eq(SysModuleTable::getModuleId, moduleId)
                                .orderByAsc(SysModuleTable::getSortOrder));
        List<ModuleTableDTO> tableInfos =
                tables.stream()
                        .map(
                                t -> {
                                    ModuleTableDTO info = new ModuleTableDTO();
                                    info.setId(t.getId());
                                    info.setTableName(t.getTableName());
                                    info.setTableDesc(t.getTableDesc());
                                    info.setIsPrimary(t.getIsPrimary());
                                    info.setJoinLeftField(t.getJoinLeftField());
                                    info.setJoinRightField(t.getJoinRightField());
                                    info.setRelationType(t.getRelationType());
                                    info.setReadOnly(t.getReadOnly());
                                    info.setSortOrder(t.getSortOrder());
                                    return info;
                                })
                        .collect(Collectors.toList());
        resp.setModuleTables(tableInfos);

        // 3. 获取模块字段配置 (sys_module_field)
        List<SysModuleField> allFields =
                sysModuleFieldMapper.selectList(
                        Wrappers.<SysModuleField>lambdaQuery()
                                .eq(SysModuleField::getModuleId, moduleId)
                                .orderByAsc(SysModuleField::getSortOrder));

        List<ModuleSimpleFieldDTO> simpleFieldInfos =
                allFields.stream()
                        .map(
                                f -> {
                                    ModuleSimpleFieldDTO info = new ModuleSimpleFieldDTO();
                                    info.setId(f.getId());
                                    info.setTableName(f.getTableName());
                                    info.setColumnName(f.getColumnName());
                                    info.setDisplayName(f.getDisplayName());
                                    info.setSortOrder(f.getSortOrder());
                                    return info;
                                })
                        .collect(Collectors.toList());
        resp.setSimpleFields(simpleFieldInfos);

        // 4. 获取列表表头配置 (sys_module_header)
        List<SysModuleHeader> allHeaders =
                sysModuleHeaderMapper.selectList(
                        Wrappers.<SysModuleHeader>lambdaQuery()
                                .eq(SysModuleHeader::getModuleId, moduleId)
                                .orderByAsc(SysModuleHeader::getSortOrder));
        List<ModuleTableHeaderDTO> headerInfos =
                allHeaders.stream()
                        .map(
                                h -> {
                                    ModuleTableHeaderDTO dto = new ModuleTableHeaderDTO();
                                    dto.setName(h.getHeaderName());
                                    dto.setTable(h.getTableName());
                                    dto.setField(h.getColumnName());
                                    dto.setWidth(h.getWidth());
                                    dto.setSortOrder(h.getSortOrder());
                                    dto.setSearchType(h.getSearchType());
                                    dto.setFixed(h.getFixed());
                                    dto.setEllipsis(
                                            h.getEllipsis() != null && h.getEllipsis() == 1);
                                    dto.setSortable(
                                            h.getSortable() != null && h.getSortable() == 1);
                                    return dto;
                                })
                        .collect(Collectors.toList());
        resp.setModuleHeaders(headerInfos);

        // 5. 获取状态列表 (sys_module_status)
        List<SysModuleStatus> statuses =
                sysModuleStatusMapper.selectList(
                        Wrappers.<SysModuleStatus>lambdaQuery()
                                .eq(SysModuleStatus::getModuleId, moduleId)
                                .orderByAsc(SysModuleStatus::getId));
        List<ModuleStatusDTO> statusInfos =
                statuses.stream()
                        .map(
                                s -> {
                                    ModuleStatusDTO info = new ModuleStatusDTO();
                                    info.setId(s.getId());
                                    info.setStatusPid(s.getStatusPid());
                                    info.setStatusId(s.getStatusId());
                                    return info;
                                })
                        .collect(Collectors.toList());
        resp.setModuleStatuses(statusInfos);

        return resp;
    }

    @Override
    @Transactional
    @ConfigChangeNotify(changeType = ChangeType.MODULE_CHANGED, moduleIdExpr = "#request.module.id")
    public Long saveModule(String projectNo, Long subjectId, SaveModuleReq request) {
        SaveModuleReq.SaveSysModuleReq req = request.getModule();
        if (req == null) {
            throw new BusinessException(400, "模块信息不能为空");
        }

        // 1. 前置校验
        validateSaveRequest(req);

        PERMISSION_CHANGES.set(0);
        try {
            // 2. 保存/更新模块主体信息
            SysModule module = saveOrUpdateModuleEntity(projectNo, subjectId, req);
            Long moduleId = module.getId();

            // 3. 保存关联表（支持 null 或空数组全量清空）
            saveModuleTables(
                    moduleId,
                    request.getModuleTables() != null
                            ? request.getModuleTables()
                            : Collections.emptyList());

            // 4. 保存物理字段（支持 null 或空数组全量清空）
            saveSimpleFields(
                    moduleId,
                    request.getSimpleFields() != null
                            ? request.getSimpleFields()
                            : Collections.emptyList());

            // 5. 保存列表表头配置（支持 null 或空数组全量清空）
            saveModuleHeaders(
                    moduleId,
                    request.getModuleHeaders() != null
                            ? request.getModuleHeaders()
                            : Collections.emptyList());

            // 6. 保存状态机配置（支持 null 或空数组全量清空）
            saveModuleStatuses(
                    moduleId,
                    request.getModuleStatuses() != null
                            ? request.getModuleStatuses()
                            : Collections.emptyList());

            int changeMask = PERMISSION_CHANGES.get();
            if (changeMask > 0) {
                log.info(
                        "模块ID {} 保存成功，发生了权限变更，掩码值为: {} (二进制: {})",
                        moduleId,
                        changeMask,
                        Integer.toBinaryString(changeMask));
            } else {
                log.info("模块ID {} 保存成功，无权限变更", moduleId);
            }

            // 7. 发布模块变更事件（审计日志监听器统一处理历史快照查询与记录）
            eventPublisher.publishEvent(
                    SysModuleChangeEvent.createSaveEvent(
                            projectNo, subjectId, moduleId, null, request));

            return moduleId;
        } finally {
            PERMISSION_CHANGES.remove();
        }
    }

    private void validateSaveRequest(SaveModuleReq.SaveSysModuleReq req) {
        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getProjectNo, AppContext.getProjectNo())
                .eq(SysModule::getModuleCode, req.getModuleCode())
                .eq(SysModule::getSubjectId, AppContext.getSubjectId());
        if (req.getId() != null) {
            wrapper.ne(SysModule::getId, req.getId());
        }
        // 模块标识唯一性校验
        List<SysModule> sysModules = sysModuleMapper.selectList(wrapper);
        if (!sysModules.isEmpty()) {
            throw new BusinessException("模块标识已存在!");
        }
    }

    private SysModule saveOrUpdateModuleEntity(
            String projectNo, Long subjectId, SaveModuleReq.SaveSysModuleReq req) {
        SysModule module;
        if (req.getId() == null) {
            // 创建新模块
            module = createModule(projectNo, subjectId, req);
        } else {
            // 编辑现有模块
            module = sysModuleMapper.selectById(req.getId());
            if (module == null) {
                throw new BusinessException(404, "模块不存在");
            }
            updateModuleInfo(module, req);
            sysModuleMapper.updateById(module);
        }
        return module;
    }

    @Override
    @Transactional
    @ConfigChangeNotify(changeType = ChangeType.MODULE_CHANGED, moduleIdExpr = "#moduleId")
    public void deleteModuleComplete(Long moduleId) {
        SysModule module = sysModuleMapper.selectById(moduleId);
        if (module == null) {
            throw new BusinessException(404, "模块不存在");
        }

        // ========== 前置校验：检查外部依赖 ==========

        // 检查菜单引用
        Long menuCount =
                sysMenuMapper.selectCount(
                        Wrappers.<SysMenu>lambdaQuery().eq(SysMenu::getModuleId, moduleId));
        if (menuCount > 0) {
            throw new BusinessException(400, "该模块已被菜单引用，请先解除关联");
        }

        // 检查审批链引用
        Long approvalCount =
                sysApprovalChainTypeMapper.selectCount(
                        Wrappers.<SysApprovalChainType>lambdaQuery()
                                .eq(SysApprovalChainType::getModuleId, moduleId));
        if (approvalCount > 0) {
            throw new BusinessException(400, "该模块已配置审批链，请先删除审批链配置");
        }

        // 检查微信模板引用
        Long templateCount =
                sysWechatTemplateMapper.selectCount(
                        Wrappers.<SysWechatTemplate>lambdaQuery()
                                .eq(SysWechatTemplate::getModuleId, moduleId));
        if (templateCount > 0) {
            throw new BusinessException(400, "该模块已关联微信模板，请先删除模板配置");
        }

        // 检查字段值配置引用
        Long fieldValueCount =
                sysFieldValueMapper.selectCount(
                        Wrappers.<SysFieldValue>lambdaQuery()
                                .eq(SysFieldValue::getModuleId, moduleId));
        if (fieldValueCount > 0) {
            throw new BusinessException(400, "该模块已配置字段值，请先删除字段值配置");
        }

        // 检查交互权限引用
        Long interactionPermissionCount =
                sysInteractionPermissionMapper.selectCount(
                        Wrappers.<SysInteractionPermission>lambdaQuery()
                                .eq(SysInteractionPermission::getModuleId, moduleId));
        if (interactionPermissionCount > 0) {
            throw new BusinessException(400, "该模块已配置交互权限，请先删除交互权限配置");
        }

        // 检查角色字段权限引用
        Long roleFieldPermissionCount =
                sysRoleModuleFieldPermissionMapper.selectCount(
                        Wrappers.<SysRoleModuleFieldPermission>lambdaQuery()
                                .eq(SysRoleModuleFieldPermission::getModuleId, moduleId));
        if (roleFieldPermissionCount > 0) {
            throw new BusinessException(400, "该模块已被角色字段权限引用，请先解除关联");
        }

        // 提前获取删除前的完整数据供审计构建
        SysModuleCompleteResp deletedCompleteData =
                getModuleCompleteById(module.getProjectNo(), module.getSubjectId(), moduleId);

        // ========== 级联删除：模块内部附属配置 ==========

        // 删除模块
        sysModuleMapper.deleteById(moduleId);

        // 删除关联表
        sysModuleTableMapper.delete(
                Wrappers.<SysModuleTable>lambdaQuery().eq(SysModuleTable::getModuleId, moduleId));

        // 删除模块字段配置
        sysModuleFieldMapper.delete(
                Wrappers.<SysModuleField>lambdaQuery().eq(SysModuleField::getModuleId, moduleId));

        // 删除状态
        sysModuleStatusMapper.delete(
                Wrappers.<SysModuleStatus>lambdaQuery().eq(SysModuleStatus::getModuleId, moduleId));

        // 发布删除事件（审计日志由事件监听器构建处理）
        eventPublisher.publishEvent(
                SysModuleChangeEvent.createDeleteEvent(
                        module.getProjectNo(),
                        module.getSubjectId(),
                        moduleId,
                        deletedCompleteData));
    }

    private SysModule createModule(String projectNo, Long subjectId, SaveSysModuleReq request) {
        SysModule module = new SysModule();
        module.setProjectNo(projectNo);
        module.setSubjectId(subjectId);
        module.setModuleCode(request.getModuleCode());
        module.setModuleName(request.getModuleName());
        module.setModuleDesc(request.getModuleDesc());

        // 处理排序顺序
        if (request.getSortOrder() == null) {
            Long count =
                    sysModuleMapper.selectCount(
                            Wrappers.<SysModule>lambdaQuery()
                                    .eq(SysModule::getProjectNo, projectNo)
                                    .eq(SysModule::getSubjectId, subjectId));
            module.setSortOrder(count.intValue());
        } else {
            module.setSortOrder(request.getSortOrder());
        }

        LocalDateTime now = LocalDateTime.now();
        module.setCreatedDate(now);
        module.setUpdatedDate(now);
        sysModuleMapper.insert(module);
        return module;
    }

    private void saveModuleTables(Long moduleId, List<ModuleTableDTO> tables) {
        if (tables == null) {
            tables = Collections.emptyList();
        }
        // 1. 查询已有的关联表
        List<SysModuleTable> existTables =
                sysModuleTableMapper.selectList(
                        Wrappers.<SysModuleTable>lambdaQuery()
                                .eq(SysModuleTable::getModuleId, moduleId));
        Map<Long, SysModuleTable> existTableMap =
                existTables.stream().collect(Collectors.toMap(SysModuleTable::getId, t -> t));

        // 2. 收集请求中提交的有效 ID
        Set<Long> keepIds =
                tables.stream()
                        .map(ModuleTableDTO::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        // 3. 计算需要删除的关联表
        List<SysModuleTable> deleteTables =
                existTables.stream()
                        .filter(t -> !keepIds.contains(t.getId()))
                        .collect(Collectors.toList());

        if (!deleteTables.isEmpty()) {
            List<String> deleteTableNames =
                    deleteTables.stream()
                            .map(SysModuleTable::getTableName)
                            .filter(Objects::nonNull)
                            .toList();

            if (!deleteTableNames.isEmpty()) {
                // 级联删除属于这些被删除关联表的物理字段及相关权限
                List<SysModuleField> fieldsToDelete =
                        sysModuleFieldMapper.selectList(
                                Wrappers.<SysModuleField>lambdaQuery()
                                        .eq(SysModuleField::getModuleId, moduleId)
                                        .in(SysModuleField::getTableName, deleteTableNames));
                List<Long> deleteFieldIds =
                        fieldsToDelete.stream().map(SysModuleField::getId).toList();
                if (!deleteFieldIds.isEmpty()) {
                    sysModuleFieldMapper.deleteByIds(deleteFieldIds);
                    cleanFieldReferences(deleteFieldIds);
                    PERMISSION_CHANGES.set(PERMISSION_CHANGES.get() | PERM_CHANGE_TABLE_CLEAN);
                }
            }

            // 删除关联表记录
            List<Long> deleteTableIds = deleteTables.stream().map(SysModuleTable::getId).toList();
            sysModuleTableMapper.deleteByIds(deleteTableIds);
        }

        // 4. 保存或更新关联表
        for (int i = 0; i < tables.size(); i++) {
            ModuleTableDTO info = tables.get(i);
            SysModuleTable t;
            boolean isUpdate = false;
            if (info.getId() != null && existTableMap.containsKey(info.getId())) {
                t = existTableMap.get(info.getId());
                isUpdate = true;
            } else {
                t = new SysModuleTable();
                t.setModuleId(moduleId);
                t.setCreatedDate(LocalDateTime.now());
            }
            Integer oldReadOnly = t.getReadOnly();
            t.setTableName(info.getTableName());
            t.setTableDesc(info.getTableDesc());
            t.setIsPrimary(info.getIsPrimary());
            t.setJoinLeftField(info.getJoinLeftField());
            t.setJoinRightField(info.getJoinRightField());
            t.setRelationType(info.getRelationType());
            t.setReadOnly(info.getReadOnly());
            t.setSortOrder(info.getSortOrder() != null ? info.getSortOrder() : i);
            t.setUpdatedDate(LocalDateTime.now());

            if (isUpdate) {
                // 如果只读属性由非只读变更为只读，重置该关联表字段的可写与更新权限为0
                if (Objects.equals(info.getReadOnly(), 1) && !Objects.equals(oldReadOnly, 1)) {
                    resetTableFieldPermissions(moduleId, info.getTableName());
                    PERMISSION_CHANGES.set(PERMISSION_CHANGES.get() | PERM_CHANGE_TABLE_RESET);
                }
                sysModuleTableMapper.updateById(t);
            } else {
                sysModuleTableMapper.insert(t);
            }
        }
    }

    /** 重置指定模块下的所有字段的角色权限（写和更新权限设为0） */
    private void resetModuleFieldPermissions(Long moduleId) {
        SysRoleModuleFieldPermission updatePerm = new SysRoleModuleFieldPermission();
        updatePerm.setWritable(0);
        updatePerm.setUpdatable(0);
        sysRoleModuleFieldPermissionMapper.update(
                updatePerm,
                Wrappers.<SysRoleModuleFieldPermission>lambdaQuery()
                        .eq(SysRoleModuleFieldPermission::getModuleId, moduleId));
    }

    /** 重置指定关联表下的所有物理字段的角色权限 */
    private void resetTableFieldPermissions(Long moduleId, String tableName) {
        if (tableName == null) {
            return;
        }
        List<SysModuleField> fields =
                sysModuleFieldMapper.selectList(
                        Wrappers.<SysModuleField>lambdaQuery()
                                .eq(SysModuleField::getModuleId, moduleId)
                                .eq(SysModuleField::getTableName, tableName));
        List<Long> fieldIds = fields.stream().map(SysModuleField::getId).toList();
        if (fieldIds.isEmpty()) {
            return;
        }
        SysRoleModuleFieldPermission updatePerm = new SysRoleModuleFieldPermission();
        updatePerm.setWritable(0);
        updatePerm.setUpdatable(0);
        sysRoleModuleFieldPermissionMapper.update(
                updatePerm,
                Wrappers.<SysRoleModuleFieldPermission>lambdaQuery()
                        .in(SysRoleModuleFieldPermission::getFieldId, fieldIds));
    }

    /** 清理字段的外部业务表引用关系（级联清理） */
    private void cleanFieldReferences(List<Long> deleteIds) {
        if (CollectionUtils.isEmpty(deleteIds)) {
            return;
        }

        // 1. 清理角色模块字段权限
        sysRoleModuleFieldPermissionMapper.delete(
                Wrappers.<SysRoleModuleFieldPermission>lambdaQuery()
                        .in(SysRoleModuleFieldPermission::getFieldId, deleteIds));

        // 2. 逻辑删除微信模板参数配置记录（实体上包含 @TableLogic 注解）
        sysWechatTemplateParamMapper.delete(
                Wrappers.<SysWechatTemplateParam>lambdaQuery()
                        .in(SysWechatTemplateParam::getFieldId, deleteIds));
    }

    private void saveSimpleFields(Long moduleId, List<ModuleSimpleFieldDTO> fields) {
        if (fields == null) {
            fields = Collections.emptyList();
        }
        // 1. 查询已有的字段配置
        List<SysModuleField> existFields =
                sysModuleFieldMapper.selectList(
                        Wrappers.<SysModuleField>lambdaQuery()
                                .eq(SysModuleField::getModuleId, moduleId));
        Map<Long, SysModuleField> existFieldMap =
                existFields.stream().collect(Collectors.toMap(SysModuleField::getId, f -> f));

        // 2. 收集请求中提交的有效 ID
        Set<Long> keepIds =
                fields.stream()
                        .map(ModuleSimpleFieldDTO::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        // 3. 计算需要删除的孤儿 ID 并执行级联删除
        List<Long> deleteIds =
                existFields.stream()
                        .map(SysModuleField::getId)
                        .filter(id -> !keepIds.contains(id))
                        .collect(Collectors.toList());

        if (!deleteIds.isEmpty()) {
            sysModuleFieldMapper.deleteByIds(deleteIds);
            // 级联清理角色权限表与微信参数表的引用
            cleanFieldReferences(deleteIds);
            PERMISSION_CHANGES.set(PERMISSION_CHANGES.get() | PERM_CHANGE_SIMPLE_CLEAN);
        }

        // 4. 组装待批量保存/更新的列表
        List<SysModuleField> saveOrUpdateList = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            ModuleSimpleFieldDTO info = fields.get(i);
            SysModuleField f;
            if (info.getId() != null && existFieldMap.containsKey(info.getId())) {
                f = existFieldMap.get(info.getId());
            } else {
                f = new SysModuleField();
                f.setModuleId(moduleId);
            }
            f.setColumnName(info.getColumnName());
            f.setTableName(info.getTableName());
            f.setDisplayName(info.getDisplayName());
            f.setSortOrder(info.getSortOrder() != null ? info.getSortOrder() : i);
            saveOrUpdateList.add(f);
        }

        if (!saveOrUpdateList.isEmpty()) {
            // 使用 MyBatis-Plus 批量更新/插入工具
            Db.saveOrUpdateBatch(saveOrUpdateList);
        }
    }

    private void saveModuleStatuses(Long moduleId, List<ModuleStatusDTO> statuses) {
        if (statuses == null) {
            statuses = Collections.emptyList();
        }
        // 1. 查询已有的状态配置
        List<SysModuleStatus> existStatuses =
                sysModuleStatusMapper.selectList(
                        Wrappers.<SysModuleStatus>lambdaQuery()
                                .eq(SysModuleStatus::getModuleId, moduleId));
        Map<Long, SysModuleStatus> existStatusMap =
                existStatuses.stream().collect(Collectors.toMap(SysModuleStatus::getId, s -> s));

        // 2. 收集请求中提交的有效 ID
        Set<Long> keepIds =
                statuses.stream()
                        .map(ModuleStatusDTO::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        // 3. 计算并删除需要删除的状态
        List<Long> deleteIds =
                existStatuses.stream()
                        .map(SysModuleStatus::getId)
                        .filter(id -> !keepIds.contains(id))
                        .collect(Collectors.toList());

        if (!deleteIds.isEmpty()) {
            sysModuleStatusMapper.deleteByIds(deleteIds);
        }

        // 4. 组装待保存/更新的状态列表
        List<SysModuleStatus> saveOrUpdateList = new ArrayList<>();
        for (ModuleStatusDTO info : statuses) {
            SysModuleStatus s;
            if (info.getId() != null && existStatusMap.containsKey(info.getId())) {
                s = existStatusMap.get(info.getId());
            } else {
                s = new SysModuleStatus();
                s.setModuleId(moduleId);
                s.setCreatedDate(LocalDateTime.now());
            }
            s.setStatusPid(info.getStatusPid());
            s.setStatusId(info.getStatusId());
            s.setUpdatedDate(LocalDateTime.now());
            saveOrUpdateList.add(s);
        }

        if (!saveOrUpdateList.isEmpty()) {
            Db.saveOrUpdateBatch(saveOrUpdateList);
        }
    }

    private void saveModuleHeaders(Long moduleId, List<ModuleTableHeaderDTO> headers) {
        if (headers == null) {
            headers = Collections.emptyList();
        }
        // 1. 查询已有的表头配置
        List<SysModuleHeader> existHeaders =
                sysModuleHeaderMapper.selectList(
                        Wrappers.<SysModuleHeader>lambdaQuery()
                                .eq(SysModuleHeader::getModuleId, moduleId));
        Map<Long, SysModuleHeader> existHeaderMap =
                existHeaders.stream().collect(Collectors.toMap(SysModuleHeader::getId, h -> h));

        // 2. 收集请求中提交的有效 ID
        Set<Long> keepIds =
                headers.stream()
                        .map(ModuleTableHeaderDTO::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        // 3. 计算并删除需要删除的表头
        List<Long> deleteIds =
                existHeaders.stream()
                        .map(SysModuleHeader::getId)
                        .filter(id -> !keepIds.contains(id))
                        .collect(Collectors.toList());

        if (!deleteIds.isEmpty()) {
            sysModuleHeaderMapper.deleteByIds(deleteIds);
        }

        // 4. 组装待保存/更新的表头列表
        List<SysModuleHeader> saveOrUpdateList = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            ModuleTableHeaderDTO info = headers.get(i);
            SysModuleHeader h;
            if (info.getId() != null && existHeaderMap.containsKey(info.getId())) {
                h = existHeaderMap.get(info.getId());
            } else {
                h = new SysModuleHeader();
                h.setModuleId(moduleId);
            }
            h.setTableName(info.getTable());
            h.setColumnName(info.getField());
            h.setHeaderName(info.getName());
            h.setWidth(info.getWidth());
            h.setSortOrder(info.getSortOrder() != null ? info.getSortOrder() : i);
            h.setSearchType(info.getSearchType());
            h.setFixed(info.getFixed());
            h.setEllipsis(info.getEllipsis() != null && info.getEllipsis() ? 1 : 0);
            h.setSortable(info.getSortable() != null && info.getSortable() ? 1 : 0);
            saveOrUpdateList.add(h);
        }

        if (!saveOrUpdateList.isEmpty()) {
            Db.saveOrUpdateBatch(saveOrUpdateList);
        }
    }

    /** 仅做实体字段赋值，不包含任何 DB 副作用。 */
    private void updateModuleInfo(SysModule module, SaveSysModuleReq request) {
        module.setModuleCode(request.getModuleCode());
        module.setModuleName(request.getModuleName());
        module.setModuleDesc(request.getModuleDesc());
        if (request.getSortOrder() != null) {
            module.setSortOrder(request.getSortOrder());
        }
        module.setUpdatedDate(LocalDateTime.now());
    }

    @Override
    public List<SysStatusTreeResp> getModuleStatusTree(
            String projectNo, Long subjectId, Long moduleId) {
        SysModule module = sysModuleMapper.selectById(moduleId);
        if (module == null
                || !Objects.equals(module.getProjectNo(), projectNo)
                || !Objects.equals(module.getSubjectId(), subjectId)) {
            throw new BusinessException(404, "模块不存在");
        }

        // 1. 获取关联的模块状态关系
        List<SysModuleStatus> moduleStatuses =
                sysModuleStatusMapper.selectList(
                        Wrappers.<SysModuleStatus>lambdaQuery()
                                .eq(SysModuleStatus::getModuleId, module.getId()));
        if (CollectionUtils.isEmpty(moduleStatuses)) {
            return Collections.emptyList();
        }

        // 2. 提取 statusId 和 statusPid
        List<Long> statusIds =
                moduleStatuses.stream()
                        .flatMap(ms -> Stream.of(ms.getStatusId(), ms.getStatusPid()))
                        .filter(id -> id != null && id != 0L)
                        .distinct()
                        .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(statusIds)) {
            return Collections.emptyList();
        }

        // 3. 查询实际的状态记录详情
        List<SysStatus> statuses =
                sysStatusMapper.selectList(
                        Wrappers.<SysStatus>lambdaQuery()
                                .in(SysStatus::getId, statusIds)
                                .eq(SysStatus::getProjectNo, projectNo)
                                .eq(SysStatus::getSubjectId, subjectId)
                                .orderByAsc(SysStatus::getSortOrder)
                                .orderByAsc(SysStatus::getStatusValue)
                                .orderByAsc(SysStatus::getId));
        if (CollectionUtils.isEmpty(statuses)) {
            return Collections.emptyList();
        }

        // 4. 构建节点映射
        Map<Long, SysStatusTreeResp> nodeMap = new LinkedHashMap<>();
        for (SysStatus status : statuses) {
            SysStatusTreeResp resp = new SysStatusTreeResp();
            org.springframework.beans.BeanUtils.copyProperties(status, resp);
            resp.setChildren(new ArrayList<>());
            nodeMap.put(status.getId(), resp);
        }

        // 5. 组装状态树
        List<SysStatusTreeResp> tree = new ArrayList<>();
        for (SysStatus status : statuses) {
            SysStatusTreeResp node = nodeMap.get(status.getId());
            Long pid = status.getPid();
            // 如果父状态ID为空、为0，或者当前模块的被选状态中不包含其父状态，则作为当前模块的树根节点
            if (pid == null || pid == 0L || !nodeMap.containsKey(pid)) {
                tree.add(node);
            } else {
                SysStatusTreeResp parent = nodeMap.get(pid);
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
    public List<SysModuleSimpleTreeResp> getAvailableModuleTree(
            Integer category, String projectNo, Long subjectId) {
        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getProjectNo, projectNo);
        wrapper.eq(SysModule::getSubjectId, subjectId);
        wrapper.orderByAsc(SysModule::getSortOrder);
        List<SysModule> modules = sysModuleMapper.selectList(wrapper);

        if (CollectionUtils.isEmpty(modules)) {
            return Collections.emptyList();
        }

        return modules.stream()
                .map(
                        module -> {
                            SysModuleSimpleTreeResp node = new SysModuleSimpleTreeResp();
                            node.setId(module.getId());
                            node.setModuleCode(module.getModuleCode());
                            node.setModuleName(module.getModuleName());
                            node.setChildren(Collections.emptyList());
                            return node;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public List<String> targetType() {
        return List.of(CheckConstant.SYS_STATUS);
    }

    @Override
    public ReferenceCheckResult check(ReferenceContext referenceContext) {
        switch (referenceContext.getTargetType()) {
            case CheckConstant.SYS_STATUS -> {
                return checkStatus(referenceContext);
            }
            default -> {
                return ReferenceCheckResult.empty();
            }
        }
    }

    private ReferenceCheckResult checkStatus(ReferenceContext referenceContext) {
        Long count =
                sysModuleStatusMapper.selectCount(
                        Wrappers.<SysModuleStatus>lambdaQuery()
                                .eq(SysModuleStatus::getStatusId, referenceContext.getTargetId()));
        if (count == 0) {
            return ReferenceCheckResult.empty();
        }
        return ReferenceCheckResult.builder()
                .referenced(true)
                .count(count)
                .message("状态【" + referenceContext.getTargetName() + "】绑定了" + count + "个模块状态")
                .build();
    }
}
