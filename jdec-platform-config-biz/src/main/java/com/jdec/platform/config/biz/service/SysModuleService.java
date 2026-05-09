package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.dto.request.MoveModuleReq;
import com.jdec.platform.config.api.dto.request.SaveModuleReq;
import com.jdec.platform.config.api.dto.request.SaveSysModuleReq;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.config.api.dto.response.SysModuleDetailResp;
import com.jdec.platform.config.api.dto.response.SysModuleListResp;
import com.jdec.platform.config.api.enums.ModuleTypeEnum;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModuleCombineField;
import com.jdec.platform.config.biz.entity.SysModuleSimpleField;
import com.jdec.platform.config.biz.entity.SysModuleStatus;
import com.jdec.platform.config.biz.entity.SysModuleTable;
import com.jdec.platform.config.biz.mapper.SysModuleCombineFieldMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.mapper.SysModuleSimpleFieldMapper;
import com.jdec.platform.config.biz.mapper.SysModuleStatusMapper;
import com.jdec.platform.config.biz.mapper.SysModuleTableMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/** 模块管理 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysModuleService implements SysModuleApi {

    private final SysModuleMapper sysModuleMapper;
    private final SysModuleTableMapper sysModuleTableMapper;
    private final SysModuleSimpleFieldMapper sysModuleSimpleFieldMapper;
    private final SysModuleCombineFieldMapper sysModuleCombineFieldMapper;
    private final SysModuleStatusMapper sysModuleStatusMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<SysModuleListResp> listModules(String tenantId) {
        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getTenantId, tenantId);
        wrapper.orderByAsc(SysModule::getParentId).orderByAsc(SysModule::getSortOrder);
        List<SysModule> modules = sysModuleMapper.selectList(wrapper);

        return modules.stream()
                .map(
                        module -> {
                            SysModuleListResp resp = new SysModuleListResp();
                            resp.setId(module.getId());
                            resp.setTenantId(module.getTenantId());
                            resp.setModuleCode(module.getModuleCode());
                            resp.setModuleName(module.getModuleName());
                            resp.setModuleDesc(module.getModuleDesc());
                            resp.setParentId(module.getParentId());
                            resp.setModuleLevel(module.getModuleLevel());
                            resp.setModulePath(module.getModulePath());
                            resp.setPrimaryTable(module.getPrimaryTable());
                            resp.setModuleType(
                                    module.getModuleType() != null
                                            ? module.getModuleType().name()
                                            : null);
                            resp.setModuleStatus(module.getModuleStatus());
                            resp.setLeafFlag(module.getLeafFlag());
                            resp.setSortOrder(module.getSortOrder());
                            resp.setCreatedBy(module.getCreatedBy());
                            resp.setCreatedName(module.getCreatedName());
                            resp.setCreatedDate(
                                    module.getCreatedDate() != null
                                            ? module.getCreatedDate().toString()
                                            : null);
                            resp.setUpdatedBy(module.getUpdatedBy());
                            resp.setUpdatedName(module.getUpdatedName());
                            resp.setUpdatedDate(
                                    module.getUpdatedDate() != null
                                            ? module.getUpdatedDate().toString()
                                            : null);
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public SysModuleCompleteResp getModuleComplete(String tenantId, String moduleCode) {
        SysModule module =
                sysModuleMapper.selectOne(
                        Wrappers.<SysModule>lambdaQuery()
                                .eq(SysModule::getTenantId, tenantId)
                                .eq(SysModule::getModuleCode, moduleCode));
        if (module == null) {
            return null;
        }

        return buildModuleCompleteResp(module);
    }

    /** 构建模块完整信息响应 */
    private SysModuleCompleteResp buildModuleCompleteResp(SysModule module) {
        SysModuleCompleteResp resp = new SysModuleCompleteResp();

        // 设置模块基本信息
        SysModuleCompleteResp.ModuleInfo moduleInfo = new SysModuleCompleteResp.ModuleInfo();
        moduleInfo.setId(module.getId());
        moduleInfo.setTenantId(module.getTenantId());
        moduleInfo.setModuleCode(module.getModuleCode());
        moduleInfo.setModuleName(module.getModuleName());
        moduleInfo.setModuleDesc(module.getModuleDesc());
        moduleInfo.setParentId(module.getParentId());
        moduleInfo.setModuleLevel(module.getModuleLevel());
        moduleInfo.setModulePath(module.getModulePath());
        moduleInfo.setPrimaryTable(module.getPrimaryTable());
        moduleInfo.setModuleType(module.getModuleType());
        moduleInfo.setModuleStatus(module.getModuleStatus());
        moduleInfo.setLeafFlag(module.getLeafFlag());
        moduleInfo.setSortOrder(module.getSortOrder());
        moduleInfo.setTableHeader(module.getTableHeader());
        moduleInfo.setCreatedBy(module.getCreatedBy());
        moduleInfo.setCreatedDate(module.getCreatedDate());
        moduleInfo.setCreatedName(module.getCreatedName());
        moduleInfo.setUpdatedBy(module.getUpdatedBy());
        moduleInfo.setUpdatedDate(module.getUpdatedDate());
        moduleInfo.setUpdatedName(module.getUpdatedName());
        resp.setModule(moduleInfo);

        Long moduleId = module.getId();

        // 获取关联表
        LambdaQueryWrapper<SysModuleTable> tableWrapper = new LambdaQueryWrapper<>();
        tableWrapper
                .eq(SysModuleTable::getModuleId, moduleId)
                .orderByAsc(SysModuleTable::getSortOrder);
        List<SysModuleTable> tables = sysModuleTableMapper.selectList(tableWrapper);
        List<SysModuleCompleteResp.ModuleTableInfo> tableInfos =
                tables.stream()
                        .map(
                                t -> {
                                    SysModuleCompleteResp.ModuleTableInfo info =
                                            new SysModuleCompleteResp.ModuleTableInfo();
                                    info.setId(t.getId());
                                    info.setTableName(t.getTableName());
                                    info.setTableDesc(t.getTableDesc());
                                    info.setJoinLeftField(t.getJoinLeftField());
                                    info.setJoinRightField(t.getJoinRightField());
                                    info.setRelationType(t.getRelationType());
                                    info.setSortOrder(t.getSortOrder());
                                    return info;
                                })
                        .collect(Collectors.toList());
        resp.setModuleTables(tableInfos);

        // 获取简单字段
        LambdaQueryWrapper<SysModuleSimpleField> simpleFieldWrapper = new LambdaQueryWrapper<>();
        simpleFieldWrapper.eq(SysModuleSimpleField::getModuleId, moduleId);
        List<SysModuleSimpleField> simpleFields =
                sysModuleSimpleFieldMapper.selectList(simpleFieldWrapper);
        List<SysModuleCompleteResp.SimpleFieldInfo> simpleFieldInfos =
                simpleFields.stream()
                        .map(
                                f -> {
                                    SysModuleCompleteResp.SimpleFieldInfo info =
                                            new SysModuleCompleteResp.SimpleFieldInfo();
                                    info.setId(f.getId());
                                    info.setTableName(f.getTableName());
                                    info.setColumnName(f.getColumnName());
                                    info.setTransformer(f.getTransformer());
                                    return info;
                                })
                        .collect(Collectors.toList());
        resp.setSimpleFields(simpleFieldInfos);

        // 获取组合字段
        LambdaQueryWrapper<SysModuleCombineField> combineFieldWrapper = new LambdaQueryWrapper<>();
        combineFieldWrapper.eq(SysModuleCombineField::getModuleId, moduleId);
        List<SysModuleCombineField> combineFields =
                sysModuleCombineFieldMapper.selectList(combineFieldWrapper);
        List<SysModuleCompleteResp.CombineFieldInfo> combineFieldInfos =
                combineFields.stream()
                        .map(
                                f -> {
                                    SysModuleCompleteResp.CombineFieldInfo info =
                                            new SysModuleCompleteResp.CombineFieldInfo();
                                    info.setId(f.getId());
                                    info.setLogicalField(f.getLogicalField());
                                    info.setDisplayName(f.getDisplayName());
                                    info.setSourceMapping(f.getSourceMapping());
                                    info.setTransformer(f.getTransformer());
                                    info.setEnabled(f.getEnabled());
                                    return info;
                                })
                        .collect(Collectors.toList());
        resp.setCombineFields(combineFieldInfos);

        // 获取模块状态（仅DETAIL类型）
        if (ModuleTypeEnum.DETAIL == module.getModuleType()) {
            LambdaQueryWrapper<SysModuleStatus> statusWrapper = new LambdaQueryWrapper<>();
            statusWrapper.eq(SysModuleStatus::getModuleId, moduleId);
            List<SysModuleStatus> statuses = sysModuleStatusMapper.selectList(statusWrapper);
            List<SysModuleCompleteResp.ModuleStatusInfo> statusInfos =
                    statuses.stream()
                            .map(
                                    s -> {
                                        SysModuleCompleteResp.ModuleStatusInfo info =
                                                new SysModuleCompleteResp.ModuleStatusInfo();
                                        info.setId(s.getId());
                                        info.setStatusPid(s.getStatusPid());
                                        info.setStatusId(s.getStatusId());
                                        return info;
                                    })
                            .collect(Collectors.toList());
            resp.setModuleStatuses(statusInfos);
        }

        return resp;
    }

    @Override
    @Transactional
    public SysModuleDetailResp saveModule(SaveModuleReq request) {
        if (request.getModule() == null) {
            throw new BusinessException(400, "模块信息不能为空");
        }

        SysModule module;
        Long moduleId;

        // 如果 ID 为空则创建新模块，否则编辑现有模块
        if (request.getModule().getId() == null) {
            // 创建新模块
            module = createOrUpdateModule(request.getModule());
            moduleId = module.getId();
        } else {
            moduleId = request.getModule().getId();
            module = sysModuleMapper.selectById(moduleId);
            if (module == null) {
                throw new BusinessException(404, "模块不存在");
            }
            // 编辑现有模块
            updateModuleInfo(module, request.getModule());
            sysModuleMapper.updateById(module);
        }

        if (!CollectionUtils.isEmpty(request.getModuleTables())) {
            saveModuleTables(moduleId, request.getModuleTables());
        }

        if (!CollectionUtils.isEmpty(request.getSimpleFields())) {
            saveSimpleFields(moduleId, request.getSimpleFields());
        }

        if (!CollectionUtils.isEmpty(request.getCombineFields())) {
            saveCombineFields(moduleId, request.getCombineFields());
        }

        // 仅DETAIL类型模块保存状态信息
        if (ModuleTypeEnum.DETAIL == module.getModuleType()
                && !CollectionUtils.isEmpty(request.getModuleStatuses())) {
            saveModuleStatuses(moduleId, request.getModuleStatuses());
        }

        return toModuleDetail(module);
    }

    @Override
    @Transactional
    public void deleteModuleComplete(Long moduleId) {
        SysModule module = sysModuleMapper.selectById(moduleId);
        if (module == null) {
            throw new BusinessException(404, "模块不存在");
        }

        // 检查是否有子模块
        LambdaQueryWrapper<SysModule> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.eq(SysModule::getParentId, moduleId);
        long childCount = sysModuleMapper.selectCount(childWrapper);
        if (childCount > 0) {
            throw new BusinessException(400, "模块存在子模块，无法删除。请先删除所有子模块");
        }

        // 物理删除模块
        sysModuleMapper.deleteById(moduleId);

        // 物理删除关联表
        LambdaQueryWrapper<SysModuleTable> tableWrapper = new LambdaQueryWrapper<>();
        tableWrapper.eq(SysModuleTable::getModuleId, moduleId);
        sysModuleTableMapper.delete(tableWrapper);

        // 物理删除简单字段
        LambdaQueryWrapper<SysModuleSimpleField> simpleFieldWrapper = new LambdaQueryWrapper<>();
        simpleFieldWrapper.eq(SysModuleSimpleField::getModuleId, moduleId);
        sysModuleSimpleFieldMapper.delete(simpleFieldWrapper);

        // 物理删除组合字段
        LambdaQueryWrapper<SysModuleCombineField> combineFieldWrapper = new LambdaQueryWrapper<>();
        combineFieldWrapper.eq(SysModuleCombineField::getModuleId, moduleId);
        sysModuleCombineFieldMapper.delete(combineFieldWrapper);

        // 物理删除模块状态
        LambdaQueryWrapper<SysModuleStatus> statusWrapper = new LambdaQueryWrapper<>();
        statusWrapper.eq(SysModuleStatus::getModuleId, moduleId);
        sysModuleStatusMapper.delete(statusWrapper);

        // 更新父模块的叶子节点标记
        if (module.getParentId() != null) {
            updateLeafFlag(module.getParentId());
        }
    }

    @Override
    @Transactional
    public SysModuleDetailResp moveModule(MoveModuleReq request) {
        Long moduleId = request.getModuleId();
        Long targetParentId = request.getTargetParentId();
        Integer targetSortOrder = request.getTargetSortOrder();

        // 参数验证
        if (targetSortOrder == null || targetSortOrder < 0) {
            throw new BusinessException(400, "目标排序顺序不能为空且必须大于等于0");
        }

        SysModule module = sysModuleMapper.selectById(moduleId);
        if (module == null) {
            throw new BusinessException(404, "模块不存在");
        }

        Long oldParentId = module.getParentId();
        Integer oldSortOrder = module.getSortOrder();

        // 检查目标父模块是否存在（如果不为空）
        if (targetParentId != null) {
            SysModule targetParent = sysModuleMapper.selectById(targetParentId);
            if (targetParent == null) {
                throw new BusinessException(404, "目标父模块不存在");
            }
            // 检查是否试图将模块移动到其子模块下（防止循环）
            if (isDescendant(moduleId, targetParentId)) {
                throw new BusinessException(400, "不能将模块移动到其子模块下");
            }
        }

        // 判断是否为同父模块拖拽
        boolean isSameParent =
                (oldParentId == null && targetParentId == null)
                        || (oldParentId != null && oldParentId.equals(targetParentId));

        if (isSameParent) {
            // 同父模块拖拽：只需要调整排序顺序
            if (oldSortOrder.equals(targetSortOrder)) {
                // 位置没有变化，无需操作
                return toModuleDetail(module);
            }

            // 查询同父模块下的所有子节点
            LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysModule::getParentId, oldParentId).orderByAsc(SysModule::getSortOrder);
            List<SysModule> siblings = sysModuleMapper.selectList(wrapper);

            // 调整排序顺序
            List<SysModule> toUpdate = new ArrayList<>();
            if (oldSortOrder < targetSortOrder) {
                // 向后移动：将 oldSortOrder+1 到 targetSortOrder 之间的节点前移
                siblings.forEach(
                        sibling -> {
                            if (!sibling.getId().equals(moduleId)) {
                                int currentSort = sibling.getSortOrder();
                                if (currentSort > oldSortOrder && currentSort <= targetSortOrder) {
                                    sibling.setSortOrder(currentSort - 1);
                                    sibling.setUpdatedDate(LocalDateTime.now());
                                    toUpdate.add(sibling);
                                }
                            }
                        });
            } else {
                // 向前移动：将 targetSortOrder 到 oldSortOrder-1 之间的节点后移
                siblings.forEach(
                        sibling -> {
                            if (!sibling.getId().equals(moduleId)) {
                                int currentSort = sibling.getSortOrder();
                                if (currentSort >= targetSortOrder && currentSort < oldSortOrder) {
                                    sibling.setSortOrder(currentSort + 1);
                                    sibling.setUpdatedDate(LocalDateTime.now());
                                    toUpdate.add(sibling);
                                }
                            }
                        });
            }

            if (!toUpdate.isEmpty()) {
                Db.updateBatchById(toUpdate);
            }

            // 更新被移动模块的排序顺序
            module.setSortOrder(targetSortOrder);
            module.setUpdatedDate(LocalDateTime.now());
            sysModuleMapper.updateById(module);

        } else {
            // 跨父模块拖拽

            // 1. 调整旧父模块下的排序：将 oldSortOrder 之后的节点前移
            LambdaQueryWrapper<SysModule> oldSiblingsWrapper = new LambdaQueryWrapper<>();
            oldSiblingsWrapper
                    .eq(SysModule::getParentId, oldParentId)
                    .gt(SysModule::getSortOrder, oldSortOrder);
            List<SysModule> oldSiblings = sysModuleMapper.selectList(oldSiblingsWrapper);
            oldSiblings.forEach(
                    sibling -> {
                        sibling.setSortOrder(sibling.getSortOrder() - 1);
                        sibling.setUpdatedDate(LocalDateTime.now());
                    });
            if (!oldSiblings.isEmpty()) {
                Db.updateBatchById(oldSiblings);
            }

            // 2. 调整新父模块下的排序：将 targetSortOrder 及之后的节点后移
            LambdaQueryWrapper<SysModule> newSiblingsWrapper = new LambdaQueryWrapper<>();
            newSiblingsWrapper
                    .eq(SysModule::getParentId, targetParentId)
                    .ge(SysModule::getSortOrder, targetSortOrder);
            List<SysModule> newSiblings = sysModuleMapper.selectList(newSiblingsWrapper);
            newSiblings.forEach(
                    sibling -> {
                        sibling.setSortOrder(sibling.getSortOrder() + 1);
                        sibling.setUpdatedDate(LocalDateTime.now());
                    });
            if (!newSiblings.isEmpty()) {
                Db.updateBatchById(newSiblings);
            }

            // 3. 更新被移动模块的父模块、排序和层级
            module.setParentId(targetParentId);
            module.setSortOrder(targetSortOrder);

            // 更新模块层级和路径
            if (targetParentId != null) {
                // 复用前面已经查询过的 targetParent（第 313 行）
                SysModule targetParent = sysModuleMapper.selectById(targetParentId);
                module.setModuleLevel(
                        (targetParent.getModuleLevel() != null ? targetParent.getModuleLevel() : 0)
                                + 1);
                String parentPath =
                        targetParent.getModulePath() != null ? targetParent.getModulePath() : "";
                String newPath =
                        parentPath.isEmpty()
                                ? module.getModuleCode()
                                : parentPath + "/" + module.getModuleCode();
                module.setModulePath(newPath);

                // 更新目标父模块的叶子节点标记
                if (targetParent.getLeafFlag() == 1) {
                    targetParent.setLeafFlag(0);
                    sysModuleMapper.updateById(targetParent);
                }
            } else {
                // 移动到根级
                module.setModuleLevel(1);
                module.setModulePath(module.getModuleCode());
            }

            module.setUpdatedDate(LocalDateTime.now());
            sysModuleMapper.updateById(module);

            // 4. 更新旧父模块的叶子节点标记
            if (oldParentId != null) {
                updateLeafFlag(oldParentId);
            }
        }

        return toModuleDetail(module);
    }

    private SysModule createOrUpdateModule(SaveSysModuleReq request) {
        SysModule module = new SysModule();
        module.setTenantId(request.getTenantId());
        module.setModuleCode(request.getModuleCode());
        module.setModuleName(request.getModuleName());
        module.setModuleDesc(request.getModuleDesc());
        module.setParentId(request.getParentId());
        module.setPrimaryTable(request.getPrimaryTable());
        module.setModuleType(convertToModuleTypeEnum(request.getModuleType()));
        module.setModuleStatus("active");
        module.setLeafFlag(1);

        // 将 tableHeader 转换为 JSON 字符串
        if (request.getTableHeader() != null && !request.getTableHeader().isEmpty()) {
            try {
                module.setTableHeader(objectMapper.writeValueAsString(request.getTableHeader()));
            } catch (Exception e) {
                throw new BusinessException(500, "tableHeader 序列化失败", e);
            }
        }

        module.setCreatedBy(request.getCreatedBy());
        module.setCreatedDate(LocalDateTime.now());
        module.setCreatedName(request.getCreatedName());

        // 处理父模块相关逻辑
        if (request.getParentId() != null) {
            SysModule parentModule = sysModuleMapper.selectById(request.getParentId());
            if (parentModule == null) {
                throw new BusinessException(404, "父模块不存在");
            }

            // 设置层级
            module.setModuleLevel(
                    (parentModule.getModuleLevel() != null ? parentModule.getModuleLevel() : 0)
                            + 1);

            // 设置路径（在 insert 前设置好，避免后续 update）
            String parentPath =
                    parentModule.getModulePath() != null ? parentModule.getModulePath() : "";
            String newPath =
                    parentPath.isEmpty()
                            ? module.getModuleCode()
                            : parentPath + "/" + module.getModuleCode();
            module.setModulePath(newPath);

            // 更新父模块的叶子节点标记
            if (parentModule.getLeafFlag() == 1) {
                parentModule.setLeafFlag(0);
                sysModuleMapper.updateById(parentModule);
            }
        } else {
            module.setModuleLevel(1);
            module.setModulePath(module.getModuleCode());
        }

        // 设置排序顺序
        if (request.getSortOrder() == null) {
            LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysModule::getTenantId, request.getTenantId())
                    .eq(SysModule::getParentId, request.getParentId())
                    .orderByDesc(SysModule::getSortOrder)
                    .last("LIMIT 1");
            SysModule lastModule = sysModuleMapper.selectOne(wrapper);
            module.setSortOrder(lastModule != null ? lastModule.getSortOrder() + 1 : 0);
        } else {
            module.setSortOrder(request.getSortOrder());
        }

        sysModuleMapper.insert(module);

        return module;
    }

    private void saveModuleTables(Long moduleId, List<SaveModuleReq.SaveModuleTableReq> tables) {
        LambdaQueryWrapper<SysModuleTable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModuleTable::getModuleId, moduleId);
        sysModuleTableMapper.delete(wrapper);

        if (tables.isEmpty()) {
            return;
        }

        List<SysModuleTable> tableList =
                tables.stream()
                        .map(
                                tableReq -> {
                                    SysModuleTable table = new SysModuleTable();
                                    table.setModuleId(moduleId);
                                    table.setTableName(tableReq.getTableName());
                                    table.setTableDesc(tableReq.getTableDesc());
                                    table.setJoinLeftField(tableReq.getJoinLeftField());
                                    table.setJoinRightField(tableReq.getJoinRightField());
                                    table.setRelationType(tableReq.getRelationType());
                                    table.setSortOrder(
                                            tableReq.getSortOrder() != null
                                                    ? tableReq.getSortOrder()
                                                    : 0);
                                    table.setCreatedDate(LocalDateTime.now());
                                    return table;
                                })
                        .collect(Collectors.toList());

        Db.saveBatch(tableList);
    }

    private void saveSimpleFields(
            Long moduleId, List<SaveModuleReq.SaveSimpleFieldReq> simpleFields) {
        // 清理旧的简单字段
        LambdaQueryWrapper<SysModuleSimpleField> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModuleSimpleField::getModuleId, moduleId);
        sysModuleSimpleFieldMapper.delete(wrapper);

        if (simpleFields.isEmpty()) {
            return;
        }

        // 保存新的简单字段
        List<SysModuleSimpleField> fieldList =
                simpleFields.stream()
                        .map(
                                fieldReq -> {
                                    SysModuleSimpleField field = new SysModuleSimpleField();
                                    field.setModuleId(moduleId);
                                    field.setTableName(fieldReq.getTableName());
                                    field.setColumnName(fieldReq.getColumnName());
                                    field.setTransformer(fieldReq.getTransformer());
                                    field.setCreatedDate(LocalDateTime.now());
                                    return field;
                                })
                        .collect(Collectors.toList());

        Db.saveBatch(fieldList);
    }

    private void saveCombineFields(
            Long moduleId, List<SaveModuleReq.SaveCombineFieldReq> combineFields) {
        // 清理旧的组合字段
        LambdaQueryWrapper<SysModuleCombineField> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModuleCombineField::getModuleId, moduleId);
        sysModuleCombineFieldMapper.delete(wrapper);

        if (combineFields.isEmpty()) {
            return;
        }

        // 保存新的组合字段
        List<SysModuleCombineField> fieldList =
                combineFields.stream()
                        .map(
                                fieldReq -> {
                                    SysModuleCombineField field = new SysModuleCombineField();
                                    field.setModuleId(moduleId);
                                    field.setLogicalField(fieldReq.getLogicalField());
                                    field.setDisplayName(fieldReq.getDisplayName());

                                    // 将 List<SourceMappingItem> 转换为 JSON 字符串
                                    if (fieldReq.getSourceMapping() != null
                                            && !fieldReq.getSourceMapping().isEmpty()) {
                                        try {
                                            field.setSourceMapping(
                                                    objectMapper.writeValueAsString(
                                                            fieldReq.getSourceMapping()));
                                        } catch (Exception e) {
                                            throw new BusinessException(
                                                    500, "sourceMapping 序列化失败", e);
                                        }
                                    }

                                    field.setTransformer(fieldReq.getTransformer());
                                    field.setEnabled(
                                            fieldReq.getEnabled() != null
                                                    ? fieldReq.getEnabled()
                                                    : 1);
                                    field.setCreatedDate(LocalDateTime.now());
                                    return field;
                                })
                        .collect(Collectors.toList());

        Db.saveBatch(fieldList);
    }

    private void saveModuleStatuses(
            Long moduleId, List<SaveModuleReq.SaveModuleStatusReq> moduleStatuses) {
        // 清理旧的模块状态
        LambdaQueryWrapper<SysModuleStatus> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModuleStatus::getModuleId, moduleId);
        sysModuleStatusMapper.delete(wrapper);

        if (moduleStatuses.isEmpty()) {
            return;
        }

        // 保存新的模块状态
        List<SysModuleStatus> statusList =
                moduleStatuses.stream()
                        .map(
                                statusReq -> {
                                    SysModuleStatus status = new SysModuleStatus();
                                    status.setModuleId(moduleId);
                                    status.setStatusPid(statusReq.getStatusPid());
                                    status.setStatusId(statusReq.getStatusId());
                                    status.setCreatedDate(LocalDateTime.now());
                                    return status;
                                })
                        .collect(Collectors.toList());

        Db.saveBatch(statusList);
    }

    private void updateLeafFlag(Long moduleId) {
        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getParentId, moduleId);
        long childCount = sysModuleMapper.selectCount(wrapper);

        SysModule module = sysModuleMapper.selectById(moduleId);
        if (module != null) {
            int newLeafFlag = childCount > 0 ? 0 : 1;
            if (module.getLeafFlag() != newLeafFlag) {
                module.setLeafFlag(newLeafFlag);
                sysModuleMapper.updateById(module);
            }
        }
    }

    /** 检查 targetId 是否是 moduleId 的后代 用于防止将模块移动到其子模块下 */
    private boolean isDescendant(Long moduleId, Long targetId) {
        if (moduleId == null || targetId == null) {
            return false;
        }

        if (moduleId.equals(targetId)) {
            return true;
        }

        SysModule target = sysModuleMapper.selectById(targetId);
        if (target == null) {
            return false;
        }

        // 递归向上查找父节点，检查是否存在 moduleId
        Long currentParentId = target.getParentId();
        while (currentParentId != null) {
            if (currentParentId.equals(moduleId)) {
                return true;
            }
            SysModule parent = sysModuleMapper.selectById(currentParentId);
            if (parent == null) {
                break;
            }
            currentParentId = parent.getParentId();
        }

        return false;
    }

    /** 更新模块基本信息 */
    private void updateModuleInfo(SysModule module, SaveSysModuleReq request) {
        module.setModuleName(request.getModuleName());
        module.setModuleDesc(request.getModuleDesc());
        module.setPrimaryTable(request.getPrimaryTable());
        module.setModuleType(convertToModuleTypeEnum(request.getModuleType()));

        // 更新 tableHeader
        if (request.getTableHeader() != null && !request.getTableHeader().isEmpty()) {
            try {
                module.setTableHeader(objectMapper.writeValueAsString(request.getTableHeader()));
            } catch (Exception e) {
                throw new BusinessException(500, "tableHeader 序列化失败", e);
            }
        }

        module.setUpdatedDate(LocalDateTime.now());
    }

    private SysModuleDetailResp toModuleDetail(SysModule module) {
        if (module == null) {
            return null;
        }
        SysModuleDetailResp resp = new SysModuleDetailResp();
        resp.setId(module.getId());
        resp.setTenantId(module.getTenantId());
        resp.setModuleCode(module.getModuleCode());
        resp.setModuleName(module.getModuleName());
        resp.setModuleDesc(module.getModuleDesc());
        resp.setParentId(module.getParentId());
        resp.setModuleLevel(module.getModuleLevel());
        resp.setModulePath(module.getModulePath());
        resp.setPrimaryTable(module.getPrimaryTable());
        resp.setModuleType(module.getModuleType());
        resp.setModuleStatus(module.getModuleStatus());
        resp.setLeafFlag(module.getLeafFlag());
        resp.setSortOrder(module.getSortOrder());
        resp.setTableHeader(module.getTableHeader());

        resp.setCreatedBy(module.getCreatedBy());
        resp.setCreatedDate(module.getCreatedDate());
        resp.setCreatedName(module.getCreatedName());
        resp.setUpdatedBy(module.getUpdatedBy());
        resp.setUpdatedDate(module.getUpdatedDate());
        resp.setUpdatedName(module.getUpdatedName());
        return resp;
    }

    /**
     * 将字符串转换为 ModuleTypeEnum
     *
     * @param moduleType 模块类型字符串
     * @return ModuleTypeEnum
     * @throws BusinessException 当模块类型无效时抛出异常
     */
    private ModuleTypeEnum convertToModuleTypeEnum(String moduleType) {
        if (moduleType == null || moduleType.trim().isEmpty()) {
            throw new BusinessException(400, "模块类型不能为空");
        }

        String trimmedType = moduleType.trim().toUpperCase();

        try {
            return ModuleTypeEnum.valueOf(trimmedType);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    400, String.format("无效的模块类型: %s，允许的值为: LIST, DETAIL", moduleType), e);
        }
    }
}
