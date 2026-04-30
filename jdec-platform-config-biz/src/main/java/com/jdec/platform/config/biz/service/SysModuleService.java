package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.api.SysModuleApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.dto.request.CreateSysModuleReq;
import com.jdec.platform.config.api.dto.request.QuerySysModuleReq;
import com.jdec.platform.config.api.dto.request.SaveModuleReq;
import com.jdec.platform.config.api.dto.request.SortOrderUpdateReq;
import com.jdec.platform.config.api.dto.request.UpdateSysModuleReq;
import com.jdec.platform.config.api.dto.response.SysModuleDetailResp;
import com.jdec.platform.config.api.dto.response.SysModuleEntryResp;
import com.jdec.platform.config.api.dto.response.SysModuleTreeResp;
import com.jdec.platform.config.api.enums.ModuleTypeEnum;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModuleField;
import com.jdec.platform.config.biz.entity.SysModuleStatus;
import com.jdec.platform.config.biz.entity.SysModuleTable;
import com.jdec.platform.config.biz.mapper.SysModuleFieldMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.mapper.SysModuleStatusMapper;
import com.jdec.platform.config.biz.mapper.SysModuleTableMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 模块管理 Service 实现
 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysModuleService implements SysModuleApi {

    private final SysModuleMapper sysModuleMapper;
    private final SysModuleTableMapper sysModuleTableMapper;
    private final SysModuleFieldMapper sysModuleFieldMapper;
    private final SysModuleStatusMapper sysModuleStatusMapper;
    private final ObjectMapper objectMapper;

    @Override
    public SysModuleEntryResp createModule(CreateSysModuleReq request) {
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
        module.setVersion(1);
        module.setCreatedBy(request.getCreatedBy());
        module.setCreatedDate(LocalDateTime.now());
        module.setCreatedName(request.getCreatedName());
        module.setDeleted(0);

        // 处理 moduleLevel 和 modulePath
        if (request.getParentId() != null) {
            SysModule parentModule = sysModuleMapper.selectById(request.getParentId());
            if (parentModule == null) {
                throw new RuntimeException("父模块不存在");
            }
            module.setModuleLevel((parentModule.getModuleLevel() != null ? parentModule.getModuleLevel() : 0) + 1);
            String parentPath = parentModule.getModulePath() != null ? parentModule.getModulePath() : "";
            module.setModulePath(parentPath);
            
            if (parentModule.getLeafFlag() == 1) {
                parentModule.setLeafFlag(0);
                sysModuleMapper.updateById(parentModule);
            }
        } else {
            module.setModuleLevel(1);
            module.setModulePath("");
        }

        // 处理 sortOrder
        if (request.getSortOrder() == null) {
            LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysModule::getTenantId, request.getTenantId())
                    .eq(SysModule::getParentId, request.getParentId())
                    .eq(SysModule::getDeleted, 0)
                    .orderByDesc(SysModule::getSortOrder)
                    .last("LIMIT 1");
            SysModule lastModule = sysModuleMapper.selectOne(wrapper);
            module.setSortOrder(lastModule != null ? lastModule.getSortOrder() + 1 : 0);
        } else {
            module.setSortOrder(request.getSortOrder());
        }

        sysModuleMapper.insert(module);
        
        // 创建后更新 modulePath（包含自己的 ID）
        if (request.getParentId() != null) {
            SysModule parentModule = sysModuleMapper.selectById(request.getParentId());
            String parentPath = parentModule.getModulePath() != null ? parentModule.getModulePath() : "";
            String newPath = parentPath.isEmpty() ? module.getId().toString() : parentPath + "/" + module.getId();
            module.setModulePath(newPath);
            sysModuleMapper.updateById(module);
        }
        
        return toModuleEntry(module);
    }

    @Override
    public SysModuleDetailResp getModuleById(Long moduleId) {
        SysModule module = sysModuleMapper.selectById(moduleId);
        return toModuleDetail(module);
    }

    @Override
    public List<SysModuleTreeResp> getModuleTree(Long tenantId) {
        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getTenantId, tenantId)
                .eq(SysModule::getDeleted, 0)
                .isNull(SysModule::getParentId)
                .orderByAsc(SysModule::getSortOrder);

        List<SysModule> rootModules = sysModuleMapper.selectList(wrapper);
        return rootModules.stream()
                .map(this::buildModuleTree)
                .collect(Collectors.toList());
    }

    @Override
    public List<SysModuleEntryResp> queryModules(QuerySysModuleReq request) {
        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getTenantId, request.getTenantId())
                .eq(SysModule::getDeleted, 0);

        if (request.getModuleCode() != null) {
            wrapper.like(SysModule::getModuleCode, request.getModuleCode());
        }
        if (request.getModuleName() != null) {
            wrapper.like(SysModule::getModuleName, request.getModuleName());
        }
        if (request.getModuleType() != null) {
            wrapper.eq(SysModule::getModuleType, request.getModuleType());
        }
        if (request.getModuleStatus() != null) {
            wrapper.eq(SysModule::getModuleStatus, request.getModuleStatus());
        }
        if (request.getModuleLevel() != null) {
            wrapper.eq(SysModule::getModuleLevel, request.getModuleLevel());
        }
        if (request.getLeafFlag() != null) {
            wrapper.eq(SysModule::getLeafFlag, request.getLeafFlag());
        }

        wrapper.orderByAsc(SysModule::getSortOrder);
        List<SysModule> modules = sysModuleMapper.selectList(wrapper);
        return modules.stream()
                .map(this::toModuleEntry)
                .collect(Collectors.toList());
    }

    @Override
    public SysModuleEntryResp updateModule(UpdateSysModuleReq request) {
        SysModule module = sysModuleMapper.selectById(request.getId());
        if (module == null) {
            throw new RuntimeException("模块不存在");
        }

        module.setModuleName(request.getModuleName());
        module.setModuleDesc(request.getModuleDesc());
        module.setPrimaryTable(request.getPrimaryTable());
        module.setModuleType(convertToModuleTypeEnum(request.getModuleType()));
        module.setModuleStatus(request.getModuleStatus());
        module.setVersion(request.getVersion());
        module.setUpdatedBy(request.getUpdatedBy());
        module.setUpdatedDate(LocalDateTime.now());
        module.setUpdatedName(request.getUpdatedName());

        if (request.getSortOrder() != null && !request.getSortOrder().equals(module.getSortOrder())) {
            module.setSortOrder(request.getSortOrder());
        }

        sysModuleMapper.updateById(module);
        return toModuleEntry(module);
    }

    @Override
    public void deleteModule(Long moduleId) {
        SysModule module = sysModuleMapper.selectById(moduleId);
        if (module != null) {
            module.setDeleted(1);
            sysModuleMapper.updateById(module);
            
            if (module.getParentId() != null) {
                updateLeafFlag(module.getParentId());
            }
        }
    }

    @Override
    public void batchDeleteModules(List<Long> moduleIds) {
        if (CollectionUtils.isEmpty(moduleIds)) {
            return;
        }
        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysModule::getId, moduleIds);
        List<SysModule> modules = sysModuleMapper.selectList(wrapper);
        
        Set<Long> parentIds = new HashSet<>();
        
        modules.forEach(module -> {
            module.setDeleted(1);
            sysModuleMapper.updateById(module);
            if (module.getParentId() != null) {
                parentIds.add(module.getParentId());
            }
        });
        
        parentIds.forEach(this::updateLeafFlag);
    }

    @Override
    public void batchUpdateSortOrder(List<SortOrderUpdateReq> requests) {
        if (CollectionUtils.isEmpty(requests)) {
            return;
        }
        
        requests.forEach(req -> {
            SysModule module = sysModuleMapper.selectById(req.getModuleId());
            if (module != null) {
                module.setSortOrder(req.getSortOrder());
                module.setUpdatedDate(LocalDateTime.now());
                sysModuleMapper.updateById(module);
            }
        });
    }

    @Override
    @Transactional
    public SysModuleDetailResp saveModule(SaveModuleReq request) {
        if (request.getModule() == null) {
            throw new RuntimeException("模块信息不能为空");
        }

        SysModule module = createOrUpdateModule(request.getModule());

        if (!CollectionUtils.isEmpty(request.getModuleTables())) {
            saveModuleTables(module.getId(), request.getModuleTables());
        }

        if (!CollectionUtils.isEmpty(request.getModuleFields())) {
            saveModuleFields(module.getId(), request.getModuleFields());
        }

        if (!CollectionUtils.isEmpty(request.getModuleStatuses())) {
            saveModuleStatuses(module.getId(), request.getModuleStatuses());
        }

        return toModuleDetail(module);
    }

    private SysModule createOrUpdateModule(CreateSysModuleReq request) {
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
        module.setVersion(1);
        module.setCreatedBy(request.getCreatedBy());
        module.setCreatedDate(LocalDateTime.now());
        module.setCreatedName(request.getCreatedName());
        module.setDeleted(0);

        if (request.getParentId() != null) {
            SysModule parentModule = sysModuleMapper.selectById(request.getParentId());
            if (parentModule == null) {
                throw new RuntimeException("父模块不存在");
            }
            module.setModuleLevel((parentModule.getModuleLevel() != null ? parentModule.getModuleLevel() : 0) + 1);
            String parentPath = parentModule.getModulePath() != null ? parentModule.getModulePath() : "";
            module.setModulePath(parentPath);

            if (parentModule.getLeafFlag() == 1) {
                parentModule.setLeafFlag(0);
                sysModuleMapper.updateById(parentModule);
            }
        } else {
            module.setModuleLevel(1);
            module.setModulePath("");
        }

        if (request.getSortOrder() == null) {
            LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysModule::getTenantId, request.getTenantId())
                    .eq(SysModule::getParentId, request.getParentId())
                    .eq(SysModule::getDeleted, 0)
                    .orderByDesc(SysModule::getSortOrder)
                    .last("LIMIT 1");
            SysModule lastModule = sysModuleMapper.selectOne(wrapper);
            module.setSortOrder(lastModule != null ? lastModule.getSortOrder() + 1 : 0);
        } else {
            module.setSortOrder(request.getSortOrder());
        }

        sysModuleMapper.insert(module);

        if (request.getParentId() != null) {
            SysModule parentModule = sysModuleMapper.selectById(request.getParentId());
            String parentPath = parentModule.getModulePath() != null ? parentModule.getModulePath() : "";
            String newPath = parentPath.isEmpty() ? module.getId().toString() : parentPath + "/" + module.getId();
            module.setModulePath(newPath);
            sysModuleMapper.updateById(module);
        }

        return module;
    }

    private void saveModuleTables(Long moduleId, List<SaveModuleReq.SaveModuleTableReq> tables) {
        LambdaQueryWrapper<SysModuleTable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModuleTable::getModuleId, moduleId)
                .eq(SysModuleTable::getDeleted, 0);
        List<SysModuleTable> oldTables = sysModuleTableMapper.selectList(wrapper);
        oldTables.forEach(t -> {
            t.setDeleted(1);
            sysModuleTableMapper.updateById(t);
        });

        tables.forEach(tableReq -> {
            SysModuleTable table = new SysModuleTable();
            table.setModuleId(moduleId);
            table.setTableName(tableReq.getTableName());
            table.setTableDesc(tableReq.getTableDesc());
            table.setJoinLeftField(tableReq.getJoinLeftField());
            table.setJoinRightField(tableReq.getJoinRightField());
            table.setRelationType(tableReq.getRelationType());
            table.setSortOrder(tableReq.getSortOrder() != null ? tableReq.getSortOrder() : 0);
            table.setCreatedDate(LocalDateTime.now());
            table.setDeleted(0);
            sysModuleTableMapper.insert(table);
        });
    }

    private void saveModuleFields(Long moduleId, List<SaveModuleReq.SaveModuleFieldReq> fields) {
        LambdaQueryWrapper<SysModuleField> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModuleField::getModuleId, moduleId)
                .eq(SysModuleField::getDeleted, 0);
        List<SysModuleField> oldFields = sysModuleFieldMapper.selectList(wrapper);
        oldFields.forEach(f -> {
            f.setDeleted(1);
            sysModuleFieldMapper.updateById(f);
        });

        fields.forEach(fieldReq -> {
            SysModuleField field = new SysModuleField();
            field.setModuleId(moduleId);
            field.setFieldCode(fieldReq.getFieldCode());
            field.setDisplayName(fieldReq.getDisplayName());
            field.setLogicalField(fieldReq.getLogicalField());
            // 将 List<SourceMappingItem> 转换为 JSON 字符串
            if (fieldReq.getSourceMapping() != null && !fieldReq.getSourceMapping().isEmpty()) {
                try {
                    field.setSourceMapping(objectMapper.writeValueAsString(fieldReq.getSourceMapping()));
                } catch (Exception e) {
                    throw new RuntimeException("sourceMapping 序列化失败", e);
                }
            }
            field.setTransformer(fieldReq.getTransformer());
            field.setTransformerEnv(fieldReq.getTransformerEnv());
            field.setRenderIcon(fieldReq.getRenderIcon());
            field.setRenderType(fieldReq.getRenderType());
            field.setSortOrder(fieldReq.getSortOrder() != null ? fieldReq.getSortOrder() : 0);
            field.setVisible(fieldReq.getVisible() != null ? fieldReq.getVisible() : 1);
            field.setRequired(fieldReq.getRequired() != null ? fieldReq.getRequired() : 0);
            field.setSearchable(fieldReq.getSearchable() != null ? fieldReq.getSearchable() : 1);
            field.setSortable(fieldReq.getSortable() != null ? fieldReq.getSortable() : 1);
            field.setDefaultValue(fieldReq.getDefaultValue());
            field.setValidationRule(fieldReq.getValidationRule());
            field.setCreatedDate(LocalDateTime.now());
            field.setDeleted(0);
            sysModuleFieldMapper.insert(field);
        });
    }

    private void saveModuleStatuses(Long moduleId, List<SaveModuleReq.SaveModuleStatusReq> statuses) {
        LambdaQueryWrapper<SysModuleStatus> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModuleStatus::getModuleId, moduleId)
                .eq(SysModuleStatus::getDeleted, 0);
        List<SysModuleStatus> oldStatuses = sysModuleStatusMapper.selectList(wrapper);
        oldStatuses.forEach(s -> {
            s.setDeleted(1);
            sysModuleStatusMapper.updateById(s);
        });

        statuses.forEach(statusReq -> {
            SysModuleStatus status = new SysModuleStatus();
            status.setModuleId(moduleId);
            status.setStatusId(statusReq.getStatusId());
            status.setCreatedDate(LocalDateTime.now());
            status.setDeleted(0);
            sysModuleStatusMapper.insert(status);
        });
    }

    private SysModuleTreeResp buildModuleTree(SysModule module) {
        SysModuleTreeResp resp = new SysModuleTreeResp();
        resp.setId(module.getId());
        resp.setModuleCode(module.getModuleCode());
        resp.setModuleName(module.getModuleName());
        resp.setModuleType(module.getModuleType());
        resp.setModuleStatus(module.getModuleStatus());
        resp.setLeafFlag(module.getLeafFlag());
        resp.setSortOrder(module.getSortOrder());

        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getParentId, module.getId())
                .eq(SysModule::getDeleted, 0)
                .orderByAsc(SysModule::getSortOrder);
        List<SysModule> children = sysModuleMapper.selectList(wrapper);

        if (!CollectionUtils.isEmpty(children)) {
            resp.setChildren(children.stream()
                    .map(this::buildModuleTree)
                    .collect(Collectors.toList()));
        }

        return resp;
    }

    private SysModuleEntryResp toModuleEntry(SysModule module) {
        SysModuleEntryResp resp = new SysModuleEntryResp();
        resp.setId(module.getId());
        resp.setTenantId(module.getTenantId());
        resp.setModuleCode(module.getModuleCode());
        resp.setModuleName(module.getModuleName());
        resp.setModuleType(module.getModuleType());
        resp.setModuleStatus(module.getModuleStatus());
        resp.setModuleLevel(module.getModuleLevel());
        resp.setLeafFlag(module.getLeafFlag());
        resp.setSortOrder(module.getSortOrder());
        resp.setCreatedDate(module.getCreatedDate());
        resp.setUpdatedDate(module.getUpdatedDate());
        return resp;
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
        resp.setVersion(module.getVersion());
        resp.setCreatedBy(module.getCreatedBy());
        resp.setCreatedDate(module.getCreatedDate());
        resp.setCreatedName(module.getCreatedName());
        resp.setUpdatedBy(module.getUpdatedBy());
        resp.setUpdatedDate(module.getUpdatedDate());
        resp.setUpdatedName(module.getUpdatedName());
        return resp;
    }

    private void updateLeafFlag(Long moduleId) {
        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getParentId, moduleId)
                .eq(SysModule::getDeleted, 0);
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

    /**
     * 将字符串转换为 ModuleTypeEnum
     * @param moduleType 模块类型字符串
     * @return ModuleTypeEnum
     * @throws IllegalArgumentException 当模块类型无效时抛出异常
     */
    private ModuleTypeEnum convertToModuleTypeEnum(String moduleType) {
        if (moduleType == null || moduleType.trim().isEmpty()) {
            throw new IllegalArgumentException("模块类型不能为空");
        }
        
        String trimmedType = moduleType.trim().toUpperCase();
        
        try {
            return ModuleTypeEnum.valueOf(trimmedType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                String.format("无效的模块类型: %s，允许的值为: LIST, DETAIL", moduleType),
                e
            );
        }
    }
}
