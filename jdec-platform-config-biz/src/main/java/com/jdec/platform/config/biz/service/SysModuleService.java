package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleStatusDTO;
import com.jdec.platform.config.api.dto.request.SaveModuleReq;
import com.jdec.platform.config.api.dto.request.SaveModuleReq.SaveSysModuleReq;
import com.jdec.platform.config.api.dto.response.SysModuleListResp;
import com.jdec.platform.config.api.dto.response.SysModuleResp;
import com.jdec.platform.config.api.dto.response.SysModuleSimpleTreeResp;
import com.jdec.platform.config.api.dto.response.SysStatusTreeResp;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModuleField;
import com.jdec.platform.config.biz.entity.SysModuleStatus;
import com.jdec.platform.config.biz.entity.SysRoleModuleFieldPermission;
import com.jdec.platform.config.biz.entity.SysStatus;
import com.jdec.platform.config.biz.entity.SysTableRelation;
import com.jdec.platform.config.biz.mapper.SysModuleFieldMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.mapper.SysModuleStatusMapper;
import com.jdec.platform.config.biz.mapper.SysRoleModuleFieldPermissionMapper;
import com.jdec.platform.config.biz.mapper.SysStatusMapper;
import com.jdec.platform.config.biz.mapper.SysTableRelationMapper;
import com.jdec.platform.config.biz.reference.ReferenceCheckService;
import com.jdec.platform.config.biz.reference.ReferenceViolation;
import com.jdec.platform.shared.annotation.ConfigChangeNotify;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.enums.ChangeType;
import com.jdec.platform.shared.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 模块管理业务实现类 负责模块、字段配置和状态配置的完整生命周期管理 */
@Service
@RequiredArgsConstructor
@Slf4j
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysModuleService implements SysModuleApi {

    private final SysModuleMapper sysModuleMapper;
    private final SysModuleFieldMapper sysModuleFieldMapper;
    private final SysModuleStatusMapper sysModuleStatusMapper;
    private final SysStatusMapper sysStatusMapper;
    private final SysTableRelationMapper sysTableRelationMapper;
    private final SysRoleModuleFieldPermissionMapper sysRoleModuleFieldPermissionMapper;
    private final ReferenceCheckService referenceCheckService;

    @Override
    public List<SysModuleListResp> listModules(String projectNo, Long subjectId, Integer category) {
        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getProjectNo, projectNo);
        wrapper.eq(SysModule::getSubjectId, subjectId);
        wrapper.eq(SysModule::getCategory, category);
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
        resp.setCategory(module.getCategory());
        resp.setSourceSubjects(module.getSourceSubjects());
        resp.setModuleCode(module.getModuleCode());
        resp.setModuleName(module.getModuleName());
        resp.setModuleDesc(module.getModuleDesc());
        resp.setParentId(module.getParentId() != null ? module.getParentId() : 0L);
        resp.setPrimaryTable(module.getPrimaryTable());
        resp.setEnableActive(module.getEnableActive());
        resp.setChangeModuleId(module.getChangeModuleId());
        resp.setSortOrder(module.getSortOrder());
        resp.setCreatedBy(module.getCreatedBy());
        resp.setCreatedDate(module.getCreatedDate());
        resp.setUpdatedBy(module.getUpdatedBy());
        resp.setUpdatedDate(module.getUpdatedDate());
        return resp;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SysModuleResp getModuleCompleteById(String projectNo, Long subjectId, Long moduleId) {
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

    /** 构建模块完整响应 */
    private SysModuleResp buildModuleCompleteResp(SysModule module) {
        SysModuleResp resp = new SysModuleResp();

        // 1. 设置模块基本信息
        SysModuleResp.ModuleInfo moduleInfo = new SysModuleResp.ModuleInfo();
        moduleInfo.setId(module.getId());
        moduleInfo.setProjectNo(module.getProjectNo());
        moduleInfo.setSubjectId(module.getSubjectId());
        moduleInfo.setCategory(module.getCategory());
        moduleInfo.setSourceSubjects(module.getSourceSubjects());
        moduleInfo.setModuleCode(module.getModuleCode());
        moduleInfo.setModuleName(module.getModuleName());
        moduleInfo.setModuleDesc(module.getModuleDesc());
        moduleInfo.setParentId(module.getParentId() != null ? module.getParentId() : 0L);
        moduleInfo.setPrimaryTable(module.getPrimaryTable());
        moduleInfo.setEnableActive(module.getEnableActive());
        moduleInfo.setChangeModuleId(module.getChangeModuleId());
        moduleInfo.setSortOrder(module.getSortOrder());
        moduleInfo.setCreatedBy(module.getCreatedBy());
        moduleInfo.setCreatedDate(module.getCreatedDate());
        moduleInfo.setUpdatedBy(module.getUpdatedBy());
        moduleInfo.setUpdatedDate(module.getUpdatedDate());
        resp.setModule(moduleInfo);

        Long moduleId = module.getId();

        // 2. 获取模块已启用的字段配置
        List<SysModuleField> allFields =
                sysModuleFieldMapper.selectList(
                        Wrappers.<SysModuleField>lambdaQuery()
                                .eq(SysModuleField::getModuleId, moduleId)
                                .eq(SysModuleField::getStatus, 1)
                                .orderByAsc(SysModuleField::getSortOrder));

        List<ModuleFieldDTO> fieldInfos =
                allFields.stream()
                        .map(
                                f -> {
                                    ModuleFieldDTO info = new ModuleFieldDTO();
                                    info.setId(f.getId());
                                    info.setTableName(f.getTableName());
                                    info.setColumnName(f.getColumnName());
                                    info.setDisplayName(f.getDisplayName());
                                    info.setBizKeyOrder(f.getBizKeyOrder());
                                    info.setSortOrder(f.getSortOrder());
                                    info.setStatus(f.getStatus());
                                    return info;
                                })
                        .collect(Collectors.toList());
        resp.setFields(fieldInfos);

        // 3. 获取状态列表
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
    @ConfigChangeNotify(changeType = ChangeType.MODULE_CHANGED, moduleIdExpr = "#result")
    public Long saveModule(String projectNo, Long subjectId, SaveModuleReq request) {
        SaveModuleReq.SaveSysModuleReq req = request.getModule();
        if (req == null) {
            throw new BusinessException(400, "模块信息不能为空");
        }

        // 1. 前置唯一性校验 (moduleCode 防重)
        validateSaveRequest(projectNo, subjectId, req);

        // 2. 校验表关联关系 (父子模块必须为 1:1 或 1:N；模块内关联表必须为 1:1 或 N:1)
        validateModuleTableRelations(projectNo, subjectId, request);

        // 3. 保存模块主体信息 (sys_module)
        SysModule module = saveOrUpdateModuleEntity(projectNo, subjectId, req);
        Long moduleId = module.getId();

        // 3. 差量维护物理字段配置 (sys_module_field)
        saveFields(moduleId, request.getFields());

        // 4. 全量刷新状态机绑定关系 (sys_module_status 纯关联表，按 moduleId 物理重置)
        resetModuleStatuses(moduleId, request.getModuleStatuses());

        // 5. 同步更新模块树节点拓扑（若传入）
        if (!CollectionUtils.isEmpty(request.getTreeNodes())) {
            updateModuleTreeNodes(projectNo, subjectId, moduleId, request.getTreeNodes());
        }

        log.info(
                "项目 {} 主体 {} 下模块 {} ({}) 保存成功",
                projectNo,
                subjectId,
                module.getModuleName(),
                moduleId);
        return moduleId;
    }

    private void validateSaveRequest(
            String projectNo, Long subjectId, SaveModuleReq.SaveSysModuleReq req) {
        boolean exists =
                sysModuleMapper.exists(
                        Wrappers.<SysModule>lambdaQuery()
                                .eq(SysModule::getProjectNo, projectNo)
                                .eq(SysModule::getSubjectId, subjectId)
                                .eq(SysModule::getModuleCode, req.getModuleCode())
                                .ne(req.getId() != null, SysModule::getId, req.getId()));
        if (exists) {
            throw new BusinessException("模块标识已存在!");
        }
    }

    /** 校验模块物理表关联关系（包括父子模块主表关系与模块内关联表关系） */
    private void validateModuleTableRelations(
            String projectNo, Long subjectId, SaveModuleReq request) {
        SaveModuleReq.SaveSysModuleReq req = request.getModule();
        if (req == null) {
            return;
        }

        // 1. 查询当前项目下的全局表关联关系配置
        List<SysTableRelation> relations =
                sysTableRelationMapper.selectList(
                        Wrappers.<SysTableRelation>lambdaQuery()
                                .eq(
                                        projectNo != null && !projectNo.isBlank(),
                                        SysTableRelation::getProjectNo,
                                        projectNo));

        // 2. 确定当前模块的主表
        String currentPrimaryTable = req.getPrimaryTable();
        if ((currentPrimaryTable == null || currentPrimaryTable.isBlank())
                && !CollectionUtils.isEmpty(request.getFields())) {
            for (ModuleFieldDTO f : request.getFields()) {
                if (f != null && f.getTableName() != null && !f.getTableName().isBlank()) {
                    currentPrimaryTable = f.getTableName().trim();
                    break;
                }
            }
        }
        if (currentPrimaryTable != null) {
            currentPrimaryTable = currentPrimaryTable.trim();
        }

        // 3. 校验要求 2：模块内部的关联表要和模块主表是 1:1 或者 N:1 的关系，不能是 1:N 的关系
        if (currentPrimaryTable != null
                && !currentPrimaryTable.isBlank()
                && !CollectionUtils.isEmpty(request.getFields())) {
            Set<String> internalTables = new HashSet<>();
            for (ModuleFieldDTO field : request.getFields()) {
                if (field != null
                        && field.getTableName() != null
                        && !field.getTableName().isBlank()) {
                    String tbl = field.getTableName().trim();
                    if (!tbl.equalsIgnoreCase(currentPrimaryTable)) {
                        internalTables.add(tbl);
                    }
                }
            }

            for (String subTable : internalTables) {
                String relType = resolveTableRelationType(currentPrimaryTable, subTable, relations);
                if (relType == null) {
                    throw new BusinessException(
                            400,
                            String.format(
                                    "模块内关联表关系非法：模块主表 [%s] 与关联表 [%s] 未配置关联关系",
                                    currentPrimaryTable, subTable));
                }
                if ("1:N".equalsIgnoreCase(relType)) {
                    throw new BusinessException(
                            400,
                            String.format(
                                    "模块内关联表关系非法：模块主表 [%s] 与关联表 [%s] 必须为 1:1 或 N:1 关系，不能为 1:N",
                                    currentPrimaryTable, subTable));
                }
                if (!"1:1".equalsIgnoreCase(relType) && !"N:1".equalsIgnoreCase(relType)) {
                    throw new BusinessException(
                            400,
                            String.format(
                                    "模块内关联表关系非法：模块主表 [%s] 与关联表 [%s] 关系类型 [%s] 非法",
                                    currentPrimaryTable, subTable, relType));
                }
            }
        }

        // 4. 校验要求 1：子模块的主表要和父模块的主表有关联关系，且父子模块主表间是 1:1 或者 1:N 的关系，不能是 N:1
        validateParentChildTableRelations(
                projectNo, subjectId, req, currentPrimaryTable, request.getTreeNodes(), relations);
    }

    private void validateParentChildTableRelations(
            String projectNo,
            Long subjectId,
            SaveModuleReq.SaveSysModuleReq req,
            String currentPrimaryTable,
            List<SaveModuleReq.ModuleTreeNodeDTO> treeNodes,
            List<SysTableRelation> relations) {
        // 查询当前项目与主体下的所有现有模块
        List<SysModule> allModules =
                sysModuleMapper.selectList(
                        Wrappers.<SysModule>lambdaQuery()
                                .eq(SysModule::getProjectNo, projectNo)
                                .eq(
                                        subjectId != null && subjectId > 0,
                                        SysModule::getSubjectId,
                                        subjectId));

        Map<Long, SysModule> currentModuleMap = new HashMap<>();
        if (allModules != null) {
            for (SysModule m : allModules) {
                if (m != null && m.getId() != null) {
                    currentModuleMap.put(m.getId(), m);
                }
            }
        }

        Long currentModuleId = req.getId();

        if (!CollectionUtils.isEmpty(treeNodes)) {
            // 组装最新拓扑父子映射
            Map<Long, Long> parentMap = new HashMap<>();
            if (allModules != null) {
                for (SysModule m : allModules) {
                    parentMap.put(m.getId(), m.getParentId() != null ? m.getParentId() : 0L);
                }
            }

            for (SaveModuleReq.ModuleTreeNodeDTO node : treeNodes) {
                if (node == null) continue;
                Long nid =
                        (node.getId() == null || node.getId() == 0L)
                                ? (currentModuleId != null ? currentModuleId : 0L)
                                : node.getId();
                Long npid =
                        (node.getParentId() == null || node.getParentId() == 0L)
                                ? 0L
                                : node.getParentId();
                parentMap.put(nid, npid);
            }

            // 遍历所有非根节点，校验其与其物理父模块主表的关系
            for (Map.Entry<Long, Long> entry : parentMap.entrySet()) {
                Long childId = entry.getKey();
                Long parentId = entry.getValue();

                if (parentId == null || parentId == 0L) {
                    continue;
                }

                // 获取子模块主表
                String childPrimary = null;
                if (childId.equals(0L)
                        || (currentModuleId != null && currentModuleId.equals(childId))) {
                    childPrimary = currentPrimaryTable;
                } else {
                    SysModule childMod = currentModuleMap.get(childId);
                    if (childMod != null) {
                        childPrimary = childMod.getPrimaryTable();
                    }
                }

                if (childPrimary == null || childPrimary.isBlank()) {
                    continue; // 虚拟子模块不校验
                }

                // 向上遍历父链，获取最近的具有物理主表的父模块
                String parentPrimary = null;
                Long curP = parentId;
                Set<Long> visited = new HashSet<>();
                while (curP != null && curP > 0L && visited.add(curP)) {
                    String pTable = null;
                    if (curP.equals(0L)
                            || (currentModuleId != null && currentModuleId.equals(curP))) {
                        pTable = currentPrimaryTable;
                    } else {
                        SysModule pMod = currentModuleMap.get(curP);
                        if (pMod != null) {
                            pTable = pMod.getPrimaryTable();
                        }
                    }

                    if (pTable != null && !pTable.isBlank()) {
                        parentPrimary = pTable.trim();
                        break;
                    }

                    curP = parentMap.get(curP);
                }

                if (parentPrimary == null || parentPrimary.isBlank()) {
                    continue; // 父链全为虚拟模块跳过
                }

                validateParentChildRelation(parentPrimary, childPrimary.trim(), relations);
            }
        } else if (currentModuleId != null) {
            // 未传入 treeNodes 时，根据数据库现有拓扑校验当前编辑模块
            SysModule currentMod = currentModuleMap.get(currentModuleId);
            if (currentMod != null
                    && currentMod.getParentId() != null
                    && currentMod.getParentId() > 0L) {
                if (currentPrimaryTable != null && !currentPrimaryTable.isBlank()) {
                    String parentPrimary = null;
                    Long curP = currentMod.getParentId();
                    Set<Long> visited = new HashSet<>();
                    while (curP != null && curP > 0L && visited.add(curP)) {
                        SysModule pMod = currentModuleMap.get(curP);
                        if (pMod != null
                                && pMod.getPrimaryTable() != null
                                && !pMod.getPrimaryTable().isBlank()) {
                            parentPrimary = pMod.getPrimaryTable().trim();
                            break;
                        }
                        curP = pMod != null ? pMod.getParentId() : 0L;
                    }

                    if (parentPrimary != null && !parentPrimary.isBlank()) {
                        validateParentChildRelation(parentPrimary, currentPrimaryTable, relations);
                    }
                }
            }
        }
    }

    private void validateParentChildRelation(
            String parentPrimary, String childPrimary, List<SysTableRelation> relations) {
        String relType = resolveTableRelationType(parentPrimary, childPrimary, relations);
        if (relType == null) {
            throw new BusinessException(
                    400,
                    String.format(
                            "父子模块表关联关系非法：父模块主表 [%s] 与子模块主表 [%s] 未配置关联关系",
                            parentPrimary, childPrimary));
        }
        if ("N:1".equalsIgnoreCase(relType)) {
            throw new BusinessException(
                    400,
                    String.format(
                            "父子模块表关联关系非法：父模块主表 [%s] 与子模块主表 [%s] 必须为 1:1 或 1:N 关系，不能为 N:1",
                            parentPrimary, childPrimary));
        }
        if (!"1:1".equalsIgnoreCase(relType) && !"1:N".equalsIgnoreCase(relType)) {
            throw new BusinessException(
                    400,
                    String.format(
                            "父子模块表关联关系非法：父模块主表 [%s] 与子模块主表 [%s] 关系类型 [%s] 非法",
                            parentPrimary, childPrimary, relType));
        }
    }

    /**
     * 推导 fromTable 到 toTable 的关联关系类型
     *
     * @param fromTable 源表
     * @param toTable 目标表
     * @param relations 项目下所有关联关系
     * @return 1:1, 1:N, N:1，未找到返回 null
     */
    private String resolveTableRelationType(
            String fromTable, String toTable, List<SysTableRelation> relations) {
        if (fromTable == null || toTable == null) {
            return null;
        }
        String from = fromTable.trim();
        String to = toTable.trim();
        if (from.equalsIgnoreCase(to)) {
            return "1:1";
        }
        if (relations == null || relations.isEmpty()) {
            return null;
        }
        for (SysTableRelation rel : relations) {
            if (rel == null || rel.getRelationType() == null || rel.getRelationType().isBlank()) {
                continue;
            }
            String main = rel.getMainTable() != null ? rel.getMainTable().trim() : "";
            String join = rel.getJoinTable() != null ? rel.getJoinTable().trim() : "";
            String type = rel.getRelationType().trim().toUpperCase();

            if (main.equalsIgnoreCase(from) && join.equalsIgnoreCase(to)) {
                return type;
            } else if (main.equalsIgnoreCase(to) && join.equalsIgnoreCase(from)) {
                if ("1:1".equals(type)) {
                    return "1:1";
                } else if ("1:N".equals(type)) {
                    return "N:1";
                } else if ("N:1".equals(type)) {
                    return "1:N";
                }
            }
        }
        return null;
    }

    private SysModule saveOrUpdateModuleEntity(
            String projectNo, Long subjectId, SaveModuleReq.SaveSysModuleReq req) {
        SysModule module;
        if (req.getId() == null) {
            module = createModule(projectNo, subjectId, req);
        } else {
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

        // ========== 前置校验：引用检查（外部依赖与子模块） ==========
        List<ReferenceViolation> violations =
                referenceCheckService.check(SysModule.class, List.of(moduleId));
        if (!violations.isEmpty()) {
            throw new BusinessException(
                    400, referenceCheckService.buildMessage(module.getModuleName(), violations));
        }

        // ========== 级联删除：模块内部附属配置 ==========
        sysModuleMapper.deleteById(moduleId);

        sysModuleFieldMapper.delete(
                Wrappers.<SysModuleField>lambdaQuery().eq(SysModuleField::getModuleId, moduleId));

        sysModuleStatusMapper.delete(
                Wrappers.<SysModuleStatus>lambdaQuery().eq(SysModuleStatus::getModuleId, moduleId));

        sysRoleModuleFieldPermissionMapper.delete(
                Wrappers.<SysRoleModuleFieldPermission>lambdaQuery()
                        .eq(SysRoleModuleFieldPermission::getModuleId, moduleId));
    }

    private SysModule createModule(String projectNo, Long subjectId, SaveSysModuleReq request) {
        SysModule module = new SysModule();
        module.setProjectNo(projectNo);
        module.setSubjectId(subjectId);
        module.setCategory(request.getCategory() != null ? request.getCategory() : 1);
        module.setSourceSubjects(request.getSourceSubjects());
        module.setPrimaryTable(request.getPrimaryTable());
        module.setEnableActive(request.getEnableActive() != null ? request.getEnableActive() : 0);
        module.setChangeModuleId(request.getChangeModuleId());
        module.setModuleCode(request.getModuleCode());
        module.setModuleName(request.getModuleName());
        module.setModuleDesc(request.getModuleDesc());
        module.setParentId(0L);
        module.setSortOrder(0);

        LocalDateTime now = LocalDateTime.now();
        module.setCreatedDate(now);
        module.setUpdatedDate(now);
        sysModuleMapper.insert(module);
        return module;
    }

    private void updateModuleInfo(SysModule module, SaveSysModuleReq request) {
        module.setModuleCode(request.getModuleCode());
        module.setModuleName(request.getModuleName());
        module.setModuleDesc(request.getModuleDesc());
        if (request.getCategory() != null) {
            module.setCategory(request.getCategory());
        }
        if (request.getSourceSubjects() != null) {
            module.setSourceSubjects(request.getSourceSubjects());
        }
        if (request.getPrimaryTable() != null) {
            module.setPrimaryTable(request.getPrimaryTable());
        }
        if (request.getEnableActive() != null) {
            module.setEnableActive(request.getEnableActive());
        }
        if (request.getChangeModuleId() != null) {
            module.setChangeModuleId(request.getChangeModuleId());
        }
        module.setUpdatedDate(LocalDateTime.now());
    }

    /** 差量维护模块物理字段配置 (sys_module_field: 采用 status 软启用/停用方案，复用原有 ID 保持权限及引用稳定) */
    private void saveFields(Long moduleId, List<ModuleFieldDTO> fields) {
        if (fields == null) {
            fields = Collections.emptyList();
        }

        // 1. 查询该模块在数据库中现存的所有物理字段配置（包含启用与已停用的）
        List<SysModuleField> existFields =
                sysModuleFieldMapper.selectList(
                        Wrappers.<SysModuleField>lambdaQuery()
                                .eq(SysModuleField::getModuleId, moduleId));
        Map<Long, SysModuleField> existFieldByIdMap =
                existFields.stream()
                        .filter(f -> f.getId() != null)
                        .collect(Collectors.toMap(SysModuleField::getId, f -> f, (k1, k2) -> k1));
        Map<String, SysModuleField> existFieldByColMap =
                existFields.stream()
                        .filter(f -> f.getColumnName() != null)
                        .collect(
                                Collectors.toMap(
                                        SysModuleField::getColumnName, f -> f, (k1, k2) -> k1));

        LocalDateTime now = LocalDateTime.now();
        List<SysModuleField> saveOrUpdateList = new ArrayList<>();
        Set<Long> processedFieldIds = new HashSet<>();

        // 2. 处理本次前端选中的有效字段（激活启用 status = 1）
        for (int i = 0; i < fields.size(); i++) {
            ModuleFieldDTO info = fields.get(i);
            if (info == null || info.getColumnName() == null || info.getColumnName().isBlank()) {
                continue;
            }

            SysModuleField f = null;
            // 优先根据 ID 匹配已存字段
            if (info.getId() != null && existFieldByIdMap.containsKey(info.getId())) {
                f = existFieldByIdMap.get(info.getId());
            }
            // 若前端未传入 ID 或 ID 不匹配，则按 columnName 在该模块内自动查找复用已有字段记录
            if (f == null && existFieldByColMap.containsKey(info.getColumnName())) {
                f = existFieldByColMap.get(info.getColumnName());
            }

            if (f == null) {
                // 确实为全新物理字段，执行新增
                f = new SysModuleField();
                f.setModuleId(moduleId);
                f.setColumnName(info.getColumnName().trim());
                f.setCreatedDate(now);
            }

            f.setStatus(1); // 激活启用
            f.setTableName(info.getTableName());
            f.setDisplayName(info.getDisplayName());
            f.setBizKeyOrder(info.getBizKeyOrder());
            f.setSortOrder(info.getSortOrder() != null ? info.getSortOrder() : i);
            f.setUpdatedDate(now);

            saveOrUpdateList.add(f);
            if (f.getId() != null) {
                processedFieldIds.add(f.getId());
            }
        }

        // 3. 识别在库中存在但本次未被勾选的字段：软停用 (status = 0)，绝不物理删除以保护权限及关联数据
        for (SysModuleField f : existFields) {
            if (f.getId() != null
                    && !processedFieldIds.contains(f.getId())
                    && (f.getStatus() == null || f.getStatus() == 1)) {
                f.setStatus(0); // 停用
                f.setUpdatedDate(now);
                saveOrUpdateList.add(f);
            }
        }

        if (!saveOrUpdateList.isEmpty()) {
            Db.saveOrUpdateBatch(saveOrUpdateList);
        }
    }

    /** 全量重置模块状态机绑定关系 (sys_module_status: 纯关联表，按 moduleId 物理重置) */
    private void resetModuleStatuses(Long moduleId, List<ModuleStatusDTO> statuses) {
        // 1. 物理清空当前模块旧的状态机关系
        sysModuleStatusMapper.delete(
                Wrappers.<SysModuleStatus>lambdaQuery().eq(SysModuleStatus::getModuleId, moduleId));

        if (CollectionUtils.isEmpty(statuses)) {
            return;
        }

        // 2. 组装并批量插入新的状态机配置
        LocalDateTime now = LocalDateTime.now();
        List<SysModuleStatus> insertList = new ArrayList<>();
        for (ModuleStatusDTO dto : statuses) {
            if (dto.getStatusPid() == null || dto.getStatusId() == null) {
                continue;
            }
            SysModuleStatus status = new SysModuleStatus();
            status.setModuleId(moduleId);
            status.setStatusPid(dto.getStatusPid());
            status.setStatusId(dto.getStatusId());
            status.setCreatedDate(now);
            status.setUpdatedDate(now);
            insertList.add(status);
        }

        if (!insertList.isEmpty()) {
            Db.saveBatch(insertList);
        }
    }

    /** 批量更新模块树节点层级与排序（包含作用域校验、防环检测、新增模块占位符回填与差异更新） */
    private void updateModuleTreeNodes(
            String projectNo,
            Long subjectId,
            Long currentSavedModuleId,
            List<SaveModuleReq.ModuleTreeNodeDTO> treeNodes) {
        if (CollectionUtils.isEmpty(treeNodes)) {
            return;
        }

        // 1. 查询当前租户与主体下的所有模块
        List<SysModule> allModules =
                sysModuleMapper.selectList(
                        Wrappers.<SysModule>lambdaQuery()
                                .eq(SysModule::getProjectNo, projectNo)
                                .eq(SysModule::getSubjectId, subjectId));
        Map<Long, SysModule> currentModuleMap =
                allModules.stream().collect(Collectors.toMap(SysModule::getId, m -> m));

        // 2. 规整节点 ID：如果节点 id 为空或为 0，代表本次新增/保存的当前模块，替换为真实生成的 ID
        List<SaveModuleReq.ModuleTreeNodeDTO> normalizedNodes = new ArrayList<>();
        for (SaveModuleReq.ModuleTreeNodeDTO node : treeNodes) {
            SaveModuleReq.ModuleTreeNodeDTO normalized = new SaveModuleReq.ModuleTreeNodeDTO();
            Long nid =
                    (node.getId() == null || node.getId() == 0L)
                            ? currentSavedModuleId
                            : node.getId();
            Long npid =
                    (node.getParentId() == null || node.getParentId() == 0L)
                            ? 0L
                            : node.getParentId();
            normalized.setId(nid);
            normalized.setParentId(npid);
            normalized.setSortOrder(node.getSortOrder() != null ? node.getSortOrder() : 0);
            normalizedNodes.add(normalized);
        }

        // 3. 校验传入的节点均属于当前项目与主体
        Map<Long, Long> nextParentMap = new HashMap<>();
        for (SysModule m : allModules) {
            nextParentMap.put(m.getId(), m.getParentId() != null ? m.getParentId() : 0L);
        }

        for (SaveModuleReq.ModuleTreeNodeDTO node : normalizedNodes) {
            if (!currentModuleMap.containsKey(node.getId())) {
                throw new BusinessException(400, "待调整树结构的模块不存在或无权操作: " + node.getId());
            }
            Long targetParentId = node.getParentId();
            if (targetParentId != 0L && !currentModuleMap.containsKey(targetParentId)) {
                throw new BusinessException(400, "目标父模块不存在或无权操作: " + targetParentId);
            }
            nextParentMap.put(node.getId(), targetParentId);
        }

        // 4. 防环校验：沿 parent 链路向上遍历，防止出现环状依赖
        for (Long startId : nextParentMap.keySet()) {
            Set<Long> visited = new HashSet<>();
            Long cur = startId;
            while (cur != null && cur != 0L) {
                if (!visited.add(cur)) {
                    throw new BusinessException(400, "模块树结构存在循环引用，调整失败");
                }
                cur = nextParentMap.get(cur);
            }
        }

        // 5. 差异比对：仅对实际发生 parentId 或 sortOrder 变化的节点执行更新
        List<SysModule> updateList = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (SaveModuleReq.ModuleTreeNodeDTO node : normalizedNodes) {
            SysModule current = currentModuleMap.get(node.getId());
            if (current == null) {
                continue;
            }
            Long targetParentId = node.getParentId();
            Integer targetSortOrder = node.getSortOrder();

            Long currentParentId = current.getParentId() != null ? current.getParentId() : 0L;
            Integer currentSortOrder = current.getSortOrder() != null ? current.getSortOrder() : 0;

            if (!Objects.equals(currentParentId, targetParentId)
                    || !Objects.equals(currentSortOrder, targetSortOrder)) {
                SysModule toUpdate = new SysModule();
                toUpdate.setId(current.getId());
                toUpdate.setParentId(targetParentId);
                toUpdate.setSortOrder(targetSortOrder);
                toUpdate.setUpdatedDate(now);
                updateList.add(toUpdate);
            }
        }

        if (!updateList.isEmpty()) {
            Db.updateBatchById(updateList);
            log.info("同步批量更新了 {} 个模块树节点的位置与排序", updateList.size());
        }
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

        // 4. 构建节点映射并组装状态树
        Map<Long, SysStatusTreeResp> nodeMap = new LinkedHashMap<>();
        for (SysStatus status : statuses) {
            SysStatusTreeResp resp = new SysStatusTreeResp();
            BeanUtils.copyProperties(status, resp);
            resp.setChildren(new ArrayList<>());
            nodeMap.put(status.getId(), resp);
        }

        List<SysStatusTreeResp> tree = new ArrayList<>();
        for (SysStatus status : statuses) {
            SysStatusTreeResp node = nodeMap.get(status.getId());
            Long pid = status.getPid();
            if (pid == null || pid == 0L || !nodeMap.containsKey(pid)) {
                tree.add(node);
            } else {
                SysStatusTreeResp parent = nodeMap.get(pid);
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(node);
            }
        }
        return tree;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<SysModuleSimpleTreeResp> getAvailableModuleTree(
            Integer category, String projectNo, Long subjectId) {
        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getProjectNo, projectNo);
        wrapper.eq(SysModule::getSubjectId, subjectId);
        if (category != null) {
            wrapper.eq(SysModule::getCategory, category);
        }
        wrapper.orderByAsc(SysModule::getSortOrder);
        List<SysModule> modules = sysModuleMapper.selectList(wrapper);

        if (CollectionUtils.isEmpty(modules)) {
            return Collections.emptyList();
        }

        Map<Long, SysModuleSimpleTreeResp> nodeMap = new LinkedHashMap<>();
        for (SysModule m : modules) {
            SysModuleSimpleTreeResp node = new SysModuleSimpleTreeResp();
            node.setId(m.getId());
            node.setModuleCode(m.getModuleCode());
            node.setModuleName(m.getModuleName());
            node.setChildren(new ArrayList<>());
            nodeMap.put(m.getId(), node);
        }

        List<SysModuleSimpleTreeResp> tree = new ArrayList<>();
        for (SysModule m : modules) {
            SysModuleSimpleTreeResp node = nodeMap.get(m.getId());
            Long parentId = m.getParentId();
            if (parentId == null || parentId == 0L || !nodeMap.containsKey(parentId)) {
                tree.add(node);
            } else {
                SysModuleSimpleTreeResp parentNode = nodeMap.get(parentId);
                if (parentNode.getChildren() == null) {
                    parentNode.setChildren(new ArrayList<>());
                }
                parentNode.getChildren().add(node);
            }
        }
        return tree;
    }
}
