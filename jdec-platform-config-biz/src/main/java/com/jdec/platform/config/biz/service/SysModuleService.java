package com.jdec.platform.config.biz.service;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.dto.common.*;
import com.jdec.platform.config.api.dto.request.MoveModuleReq;
import com.jdec.platform.config.api.dto.request.SaveModuleReq;
import com.jdec.platform.config.api.dto.request.SaveModuleReq.SaveSysModuleReq;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.config.api.enums.ModuleTypeEnum;
import com.jdec.platform.config.biz.audit.event.SysModuleChangeEvent;
import com.jdec.platform.config.biz.check.CheckConstant;
import com.jdec.platform.config.biz.check.ReferenceCheckResult;
import com.jdec.platform.config.biz.check.ReferenceChecker;
import com.jdec.platform.config.biz.check.ReferenceContext;
import com.jdec.platform.config.biz.entity.*;
import com.jdec.platform.config.biz.mapper.*;
import com.jdec.platform.hr.api.SubjectApi;
import com.jdec.platform.hr.api.bo.SubjectBO;
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
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 模块管理业务实现类 负责模块、关联表、字段配置和状态配置的完整生命周期管理 */
@Service
@RequiredArgsConstructor
@Slf4j
@com.jdec.platform.shared.datasource.DataSource(DataSourceConstants.CONFIG_CENTER)
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
    private final DataSourceResolver dataSourceResolver;
    private final ObjectMapper objectMapper;
    private final SubjectApi subjectApi;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<SysModuleListResp> listModules(String projectNo, Long subjectId, Integer category) {
        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getProjectNo, projectNo);
        wrapper.eq(SysModule::getSubjectId, subjectId);
        wrapper.eq(category != null, SysModule::getCategory, category);
        wrapper.orderByAsc(SysModule::getParentId).orderByAsc(SysModule::getSortOrder);
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
        resp.setParentId(module.getParentId());
        resp.setDetailModuleId(module.getDetailModuleId());
        resp.setPrimaryTable(module.getPrimaryTable());
        resp.setModuleType(module.getModuleType() != null ? module.getModuleType().name() : null);
        resp.setApprovalRequired(module.getApprovalRequired());
        resp.setBizDefFlag(module.getBizDefFlag());
        resp.setCategory(module.getCategory());
        resp.setSortOrder(module.getSortOrder());
        resp.setRelateSearchField(module.getRelateSearchField());
        resp.setCreatedBy(module.getCreatedBy());
        resp.setCreatedName(module.getCreatedName());
        resp.setCreatedDate(
                module.getCreatedDate() != null ? module.getCreatedDate().toString() : null);
        resp.setUpdatedBy(module.getUpdatedBy());
        resp.setUpdatedName(module.getUpdatedName());
        resp.setUpdatedDate(
                module.getUpdatedDate() != null ? module.getUpdatedDate().toString() : null);
        return resp;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SysModuleCompleteResp getModuleCompleteById(
            String projectNo, Long subjectId, Long moduleId) {
        SysModule module = sysModuleMapper.selectById(moduleId);
        if (module == null
                || !Objects.equals(module.getProjectNo(), projectNo)
                || !Objects.equals(module.getSubjectId(), subjectId)) {
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
        moduleInfo.setProjectNo(module.getProjectNo());
        moduleInfo.setSubjectId(module.getSubjectId());
        moduleInfo.setModuleCode(module.getModuleCode());
        moduleInfo.setModuleName(module.getModuleName());
        moduleInfo.setModuleDesc(module.getModuleDesc());
        moduleInfo.setParentId(module.getParentId());
        moduleInfo.setDetailModuleId(module.getDetailModuleId());
        moduleInfo.setPrimaryTable(module.getPrimaryTable());
        moduleInfo.setModuleType(module.getModuleType());
        moduleInfo.setApprovalRequired(module.getApprovalRequired());
        moduleInfo.setBizDefFlag(module.getBizDefFlag());
        moduleInfo.setCategory(module.getCategory());
        moduleInfo.setSortOrder(module.getSortOrder());
        moduleInfo.setRelateSearchField(module.getRelateSearchField());
        moduleInfo.setTableHeader(parseTableHeader(module.getTableHeader()));
        moduleInfo.setSourceSubjects(buildSubjectInfos(module.getSourceSubjects()));
        moduleInfo.setCreatedBy(module.getCreatedBy());
        moduleInfo.setCreatedDate(module.getCreatedDate());
        moduleInfo.setCreatedName(module.getCreatedName());
        moduleInfo.setUpdatedBy(module.getUpdatedBy());
        moduleInfo.setUpdatedDate(module.getUpdatedDate());
        moduleInfo.setUpdatedName(module.getUpdatedName());

        populateTableModuleCollections(module, moduleInfo);

        resp.setModule(moduleInfo);

        Long moduleId = module.getId();

        // 获取关联表
        LambdaQueryWrapper<SysModuleTable> tableWrapper = new LambdaQueryWrapper<>();
        tableWrapper
                .eq(SysModuleTable::getModuleId, moduleId)
                .orderByAsc(SysModuleTable::getSortOrder);
        List<SysModuleTable> tables = sysModuleTableMapper.selectList(tableWrapper);
        List<ModuleTableDTO> tableInfos =
                tables.stream()
                        .map(
                                t -> {
                                    ModuleTableDTO info = new ModuleTableDTO();
                                    info.setId(t.getId());
                                    info.setTableName(t.getTableName());
                                    info.setTableDesc(t.getTableDesc());
                                    info.setJoinLeftField(t.getJoinLeftField());
                                    info.setJoinRightField(t.getJoinRightField());
                                    info.setRelationType(t.getRelationType());
                                    info.setReadOnly(t.getReadOnly());
                                    info.setSortOrder(t.getSortOrder());
                                    return info;
                                })
                        .collect(Collectors.toList());
        resp.setModuleTables(tableInfos);

        // 获取模块字段配置并分流
        List<SysModuleField> allFields =
                sysModuleFieldMapper.selectList(
                        Wrappers.<SysModuleField>lambdaQuery()
                                .eq(SysModuleField::getModuleId, moduleId));

        Map<String, List<SysModuleField>> simpleFieldsByTable =
                allFields.stream()
                        .filter(f -> "SIMPLE".equals(f.getFieldType()))
                        .collect(Collectors.groupingBy(SysModuleField::getTableName));

        Map<String, Map<String, String>> tableColumnDisplayNameMap = new HashMap<>();

        for (Map.Entry<String, List<SysModuleField>> entry : simpleFieldsByTable.entrySet()) {
            String tableName = entry.getKey();
            if (tableName == null || tableName.isBlank()) {
                continue;
            }
            Map<String, String> columnDisplayNameMap = new HashMap<>();

            // 1. Get SysField configurations
            List<SysField> sysFields =
                    sysFieldMapper.selectList(
                            Wrappers.<SysField>lambdaQuery()
                                    .eq(SysField::getProjectNo, module.getProjectNo())
                                    .eq(SysField::getSubjectId, module.getSubjectId())
                                    .eq(SysField::getTableName, tableName)
                                    .eq(SysField::getDeleted, 0));
            for (SysField sf : sysFields) {
                if (StringUtils.hasText(sf.getDisplayName())) {
                    columnDisplayNameMap.put(sf.getColumnName(), sf.getDisplayName());
                }
            }

            // 2. Query schema comments for fields not resolved by SysField
            try {
                DataSource dataSource = dataSourceResolver.resolve(module.getProjectNo());
                JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
                String sql =
                        """
                                SELECT COLUMN_NAME, COLUMN_COMMENT
                                FROM INFORMATION_SCHEMA.COLUMNS
                                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                                """;
                List<Map<String, Object>> rows =
                        jdbcTemplate.queryForList(sql, module.getProjectNo(), tableName);
                for (Map<String, Object> row : rows) {
                    String colName = (String) row.get("COLUMN_NAME");
                    String colComment = (String) row.get("COLUMN_COMMENT");
                    if (!columnDisplayNameMap.containsKey(colName)
                            && StringUtils.hasText(colComment)) {
                        columnDisplayNameMap.put(colName, colComment);
                    }
                }
            } catch (Exception e) {
                log.warn("Query column comments failed for table: {}", tableName, e);
            }

            tableColumnDisplayNameMap.put(tableName, columnDisplayNameMap);
        }

        List<ModuleSimpleFieldDTO> simpleFieldInfos =
                allFields.stream()
                        .filter(f -> "SIMPLE".equals(f.getFieldType()))
                        .map(
                                f -> {
                                    ModuleSimpleFieldDTO info = new ModuleSimpleFieldDTO();
                                    info.setId(f.getId());
                                    info.setTableName(f.getTableName());
                                    info.setColumnName(f.getFieldCode());
                                    info.setTransformer(f.getTransformer());
                                    info.setBizKeyOrder(f.getBizKeyOrder());

                                    Map<String, String> colMap =
                                            tableColumnDisplayNameMap.get(f.getTableName());
                                    if (colMap != null) {
                                        info.setDisplayName(colMap.get(f.getFieldCode()));
                                    }
                                    return info;
                                })
                        .collect(Collectors.toList());
        resp.setSimpleFields(simpleFieldInfos);

        //        List<ModuleCombineFieldDTO> combineFieldInfos =
        //                allFields.stream()
        //                        .filter(f -> "COMBINE".equals(f.getFieldType()))
        //                        .sorted(Comparator.comparing(SysModuleField::getId))
        //                        .map(
        //                                f -> {
        //                                    ModuleCombineFieldDTO info = new
        // ModuleCombineFieldDTO();
        //                                    info.setId(f.getId());
        //                                    info.setLogicalField(f.getFieldCode());
        //                                    info.setDisplayName(f.getDisplayName());
        //
        // info.setSourceMapping(parseSourceMapping(f.getSourceMapping()));
        //                                    info.setTransformer(f.getTransformer());
        //                                    info.setEnabled(f.getEnabled());
        //                                    return info;
        //                                })
        //                        .collect(Collectors.toList());
        //        resp.setCombineFields(combineFieldInfos);

        // 获取状态（仅对于 DETAIL 类型）
        if (ModuleTypeEnum.DETAIL == module.getModuleType()) {
            LambdaQueryWrapper<SysModuleStatus> statusWrapper = new LambdaQueryWrapper<>();
            statusWrapper
                    .eq(SysModuleStatus::getModuleId, moduleId)
                    .orderByAsc(SysModuleStatus::getId);
            List<SysModuleStatus> statuses = sysModuleStatusMapper.selectList(statusWrapper);
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
        }

        return resp;
    }

    private void populateTableModuleCollections(
            SysModule module, SysModuleCompleteResp.ModuleInfo moduleInfo) {
        LambdaQueryWrapper<SysModule> moduleQuery = new LambdaQueryWrapper<>();
        moduleQuery.eq(SysModule::getProjectNo, module.getProjectNo());
        moduleQuery.eq(SysModule::getSubjectId, module.getSubjectId());
        List<SysModule> allModulesInSubject = sysModuleMapper.selectList(moduleQuery);

        List<SysModuleCompleteResp.TableModule> bizDefTables =
                new ArrayList<>(
                        allModulesInSubject.stream()
                                .filter(
                                        m ->
                                                m.getBizDefFlag() != null
                                                        && m.getBizDefFlag() == 1
                                                        && StringUtils.hasText(m.getPrimaryTable()))
                                .map(
                                        m -> {
                                            SysModuleCompleteResp.TableModule tm =
                                                    new SysModuleCompleteResp.TableModule();
                                            tm.setTableName(
                                                    m.getPrimaryTable().toLowerCase().trim());
                                            tm.setModuleId(m.getId());
                                            return tm;
                                        })
                                .collect(
                                        Collectors.toMap(
                                                SysModuleCompleteResp.TableModule::getTableName,
                                                tm -> tm,
                                                (existing, replacement) -> existing))
                                .values());
        moduleInfo.setBizDefTables(bizDefTables);

        List<SysModuleCompleteResp.TableModule> writableRelationTables = new ArrayList<>();
        if (!allModulesInSubject.isEmpty()) {
            List<Long> moduleIds =
                    allModulesInSubject.stream().map(SysModule::getId).collect(Collectors.toList());
            LambdaQueryWrapper<SysModuleTable> moduleTableQuery = new LambdaQueryWrapper<>();
            moduleTableQuery.in(SysModuleTable::getModuleId, moduleIds);
            List<SysModuleTable> allModuleTables =
                    sysModuleTableMapper.selectList(moduleTableQuery);
            writableRelationTables =
                    new ArrayList<>(
                            allModuleTables.stream()
                                    .filter(
                                            mt ->
                                                    mt.getReadOnly() != null
                                                            && mt.getReadOnly() == 0
                                                            && StringUtils.hasText(
                                                                    mt.getTableName()))
                                    .map(
                                            mt -> {
                                                SysModuleCompleteResp.TableModule tm =
                                                        new SysModuleCompleteResp.TableModule();
                                                tm.setTableName(
                                                        mt.getTableName().toLowerCase().trim());
                                                tm.setModuleId(mt.getModuleId());
                                                return tm;
                                            })
                                    .collect(
                                            Collectors.toMap(
                                                    SysModuleCompleteResp.TableModule::getTableName,
                                                    tm -> tm,
                                                    (existing, replacement) -> existing))
                                    .values());
        }
        moduleInfo.setWritableRelationTables(writableRelationTables);
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

            // 4. 保存简单字段（支持 null 或空数组全量清空）
            saveSimpleFields(
                    moduleId,
                    request.getSimpleFields() != null
                            ? request.getSimpleFields()
                            : Collections.emptyList());

            // 5. 仅 DETAIL 类型模块保存状态信息（支持 null 或空数组全量清空）
            if (ModuleTypeEnum.DETAIL == module.getModuleType()) {
                saveModuleStatuses(
                        moduleId,
                        request.getModuleStatuses() != null
                                ? request.getModuleStatuses()
                                : Collections.emptyList());
            }

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
        // 前置校验：LIST 类型或配置了 sourceSubjects 时，bizDefFlag 必须为 0
        boolean isListOrHasSource =
                "LIST".equalsIgnoreCase(req.getModuleType())
                        || CollectionUtil.isNotEmpty(req.getSourceSubjects());
        if (isListOrHasSource && req.getBizDefFlag() != null && req.getBizDefFlag() != 0) {
            throw new BusinessException(400, "LIST类型或配置了数据来源主体的模块，bizDefFlag 必须为 0");
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
            Integer oldBizDefFlag = module.getBizDefFlag();

            // Phase 1: 纯字段赋值，无副作用
            updateModuleInfo(module, req);
            sysModuleMapper.updateById(module);

            // Phase 1.5: 权限重置 —— 仅当 bizDefFlag 由非0变为0时触发（可写->只读）
            if (req.getBizDefFlag() != null
                    && req.getBizDefFlag() == 0
                    && !Objects.equals(oldBizDefFlag, 0)) {
                resetModuleFieldPermissions(module.getId());
                PERMISSION_CHANGES.set(PERMISSION_CHANGES.get() | PERM_CHANGE_MODULE_RESET);
            }

            // Phase 3: bizDefFlag=1 时跨模块传播只读 —— 仅当 bizDefFlag 由非1（只读）变为1（可写）时触发
            if (req.getBizDefFlag() != null
                    && req.getBizDefFlag() == 1
                    && !Objects.equals(oldBizDefFlag, 1)) {
                applyBizDefReadOnlyToSubject(module, req.getPrimaryTable());
            }
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

        // 检查子模块
        Long childCount =
                sysModuleMapper.selectCount(
                        Wrappers.<SysModule>lambdaQuery().eq(SysModule::getParentId, moduleId));
        if (childCount > 0) {
            throw new BusinessException(400, "该模块下存在子模块，请先删除子模块");
        }

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

    @Override
    @Transactional
    public MoveModuleResp moveModule(MoveModuleReq request) {
        Long moduleId = request.getModuleId();
        Long targetParentId = request.getTargetParentId();
        Integer targetSortOrder = request.getTargetSortOrder();

        // 参数验证
        if (moduleId == null) {
            throw new BusinessException(400, "模块ID不能为空");
        }
        if (targetParentId == null) {
            throw new BusinessException(400, "目标父模块ID不能为空，根节点请传入0");
        }
        if (targetSortOrder == null || targetSortOrder < 0) {
            throw new BusinessException(400, "目标排序顺序不能为空且必须大于等于0");
        }

        SysModule module = sysModuleMapper.selectById(moduleId);
        if (module == null) {
            throw new BusinessException(404, "模块不存在");
        }

        Long oldParentId = module.getParentId();
        // 提前查询目标父模块，后续复用，避免重复查询
        SysModule targetParent = null;
        if (targetParentId > 0) {
            if (moduleId.equals(targetParentId)) {
                throw new BusinessException(400, "不能将模块移动到自身下");
            }
            targetParent = sysModuleMapper.selectById(targetParentId);
            if (targetParent == null) {
                throw new BusinessException(404, "目标父模块不存在");
            }
            if (!Objects.equals(targetParent.getProjectNo(), module.getProjectNo())
                    || !Objects.equals(targetParent.getSubjectId(), module.getSubjectId())) {
                throw new BusinessException(400, "不能跨项目或主体移动模块");
            }
            // 检查是否试图将模块移动到其子模块下（防止循环）
            if (isDescendant(moduleId, targetParentId)) {
                throw new BusinessException(400, "不能将模块移动到其子模块下");
            }
        }

        // 判断是否为同父模块拖拽
        if (Objects.equals(oldParentId, targetParentId)) {
            // 同父拖拽：全量洗牌。
            // 取出该组全部子节点，移除被拖模块后插入目标位置，然后从 0 开始完整重编号。
            // 相比增量移位，全量洗牌确保 sortOrder 无论 DB 原始状态如何都连续无断层。
            List<SysModule> siblings =
                    getChildrenSorted(oldParentId, module.getProjectNo(), module.getSubjectId());
            siblings.removeIf(s -> s.getId().equals(moduleId));

            // clamp 目标位置到合法范围 [0, siblings.size()]
            int insertPos = Math.min(targetSortOrder, siblings.size());
            siblings.add(insertPos, module);

            // reshuffleAndUpdate 内部对 sortOrder 做 diff，位置未变的记录不产生 DB 写入
            reshuffleAndUpdate(siblings);

        } else {
            // 跨父移动

            // 1. 旧父组：移除被移动模块后完整重编号，确保旧父排序连续
            List<SysModule> oldChildren =
                    getChildrenSorted(oldParentId, module.getProjectNo(), module.getSubjectId());
            oldChildren.removeIf(s -> s.getId().equals(moduleId));
            reshuffleAndUpdate(oldChildren);

            // 2. 更新被移动模块自身的 parentId
            module.setParentId(targetParentId);

            // 仅更新跨父移动涉及的字段：parentId / updatedDate
            LocalDateTime now = LocalDateTime.now();
            module.setUpdatedDate(now);
            sysModuleMapper.update(
                    null,
                    Wrappers.<SysModule>lambdaUpdate()
                            .eq(SysModule::getId, module.getId())
                            .set(SysModule::getParentId, module.getParentId())
                            .set(SysModule::getUpdatedDate, module.getUpdatedDate()));

            // 3. 新父组：从 DB 拉取当前子节点（已包含刚写入 of module），
            // 移除 module 后在目标位置插入，完整重编号
            List<SysModule> newChildren =
                    getChildrenSorted(targetParentId, module.getProjectNo(), module.getSubjectId());
            newChildren.removeIf(s -> s.getId().equals(moduleId));
            int insertPos = Math.min(targetSortOrder, newChildren.size());
            newChildren.add(insertPos, module);
            reshuffleAndUpdate(newChildren);
        }

        // 重新查询最新状态并构造精简响应
        SysModule latest = sysModuleMapper.selectById(moduleId);
        MoveModuleResp resp = new MoveModuleResp();
        resp.setId(latest.getId());
        resp.setParentId(latest.getParentId());
        resp.setSortOrder(latest.getSortOrder());
        resp.setUpdatedDate(latest.getUpdatedDate());

        // 发布移动事件（审计逻辑在事件监听器中执行）
        String oldParentName = getModuleNameById(oldParentId);
        String newParentName = getModuleNameById(targetParentId);
        eventPublisher.publishEvent(
                com.jdec.platform.config.biz.audit.event.SysModuleChangeEvent.createMoveEvent(
                        module.getProjectNo(),
                        module.getSubjectId(),
                        moduleId,
                        request,
                        module.getModuleName(),
                        oldParentName,
                        newParentName));

        return resp;
    }

    private String getModuleNameById(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return "根模块";
        }
        SysModule parent = sysModuleMapper.selectById(parentId);
        return parent != null ? parent.getModuleName() : "未知模块";
    }

    /**
     * 查询指定父节点下的所有直接子节点，按 sortOrder 升序排列，返回可变列表。
     *
     * @param parentId 父节点 ID（null 表示根节点）
     * @param projectNo 项目编号
     * @param subjectId 主体ID
     */
    private List<SysModule> getChildrenSorted(Long parentId, String projectNo, Long subjectId) {
        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getParentId, parentId != null ? parentId : 0L);
        wrapper.eq(SysModule::getProjectNo, projectNo);
        wrapper.eq(SysModule::getSubjectId, subjectId);
        wrapper.orderByAsc(SysModule::getSortOrder);
        return new ArrayList<>(sysModuleMapper.selectList(wrapper));
    }

    /** 对一组子节点进行重编号，并仅对 sortOrder 发生变化的记录执行 DB 更新。 */
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

    /** 检查 targetId 是否为 moduleId 的子孙节点 */
    private boolean isDescendant(Long moduleId, Long targetId) {
        SysModule target = sysModuleMapper.selectById(targetId);
        if (target == null) return false;

        Long currentId = targetId;
        while (currentId != null && currentId > 0) {
            SysModule current = sysModuleMapper.selectById(currentId);
            if (current == null) break;
            if (Objects.equals(current.getParentId(), moduleId)) return true;
            currentId = current.getParentId();
        }
        return false;
    }

    private SysModule createModule(String projectNo, Long subjectId, SaveSysModuleReq request) {
        SysModule module = new SysModule();
        module.setProjectNo(projectNo);
        module.setSubjectId(subjectId);
        module.setModuleCode(request.getModuleCode());
        module.setModuleName(request.getModuleName());
        module.setModuleDesc(request.getModuleDesc());
        module.setParentId(request.getParentId() != null ? request.getParentId() : 0L);
        module.setDetailModuleId(request.getDetailModuleId());
        module.setPrimaryTable(request.getPrimaryTable());
        module.setModuleType(convertToModuleTypeEnum(request.getModuleType()));
        module.setApprovalRequired(request.getApprovalRequired());
        module.setBizDefFlag(request.getBizDefFlag());
        module.setCategory(request.getCategory() != null ? request.getCategory() : 1);
        module.setRelateSearchField(request.getRelateSearchField());
        // 处理排序顺序
        if (request.getSortOrder() == null) {
            Long count =
                    sysModuleMapper.selectCount(
                            Wrappers.<SysModule>lambdaQuery()
                                    .eq(SysModule::getParentId, module.getParentId()));
            module.setSortOrder(count.intValue());
        } else {
            module.setSortOrder(request.getSortOrder());
        }

        requestToTableHeader(request, module);

        LocalDateTime now = LocalDateTime.now();
        module.setCreatedDate(now);
        module.setUpdatedDate(now);
        sysModuleMapper.insert(module);
        return module;
    }

    private void requestToTableHeader(SaveSysModuleReq request, SysModule module) {
        try {
            module.setTableHeader(
                    request.getTableHeader() != null
                            ? objectMapper.writeValueAsString(request.getTableHeader())
                            : null);
        } catch (Exception e) {
            throw new BusinessException(500, "tableHeader 序列化失败", e);
        }

        try {
            module.setSourceSubjects(
                    request.getSourceSubjects() != null
                            ? objectMapper.writeValueAsString(request.getSourceSubjects())
                            : null);
        } catch (Exception e) {
            throw new BusinessException(500, "sourceSubjects 序列化失败", e);
        }
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
                // 级联删除属于这些被删除关联表的简单物理字段及相关权限
                List<SysModuleField> fieldsToDelete =
                        sysModuleFieldMapper.selectList(
                                Wrappers.<SysModuleField>lambdaQuery()
                                        .eq(SysModuleField::getModuleId, moduleId)
                                        .eq(SysModuleField::getFieldType, "SIMPLE")
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
                                .eq(SysModuleField::getFieldType, "SIMPLE")
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
                                .eq(SysModuleField::getModuleId, moduleId)
                                .eq(SysModuleField::getFieldType, "SIMPLE"));
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
        for (ModuleSimpleFieldDTO info : fields) {
            SysModuleField f;
            if (info.getId() != null && existFieldMap.containsKey(info.getId())) {
                f = existFieldMap.get(info.getId());
            } else {
                f = new SysModuleField();
                f.setModuleId(moduleId);
                f.setFieldType("SIMPLE");
            }
            f.setFieldCode(info.getColumnName());
            f.setTableName(info.getTableName());
            f.setTransformer(info.getTransformer());
            f.setBizKeyOrder(info.getBizKeyOrder());
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

    /** 仅做实体字段赋值，不包含任何 DB 副作用。 */
    private void updateModuleInfo(SysModule module, SaveSysModuleReq request) {
        module.setModuleCode(request.getModuleCode());
        module.setModuleName(request.getModuleName());
        module.setModuleDesc(request.getModuleDesc());
        module.setPrimaryTable(request.getPrimaryTable());
        module.setModuleType(convertToModuleTypeEnum(request.getModuleType()));
        module.setApprovalRequired(request.getApprovalRequired());
        module.setBizDefFlag(request.getBizDefFlag());
        if (request.getCategory() != null) {
            module.setCategory(request.getCategory());
        }
        module.setRelateSearchField(request.getRelateSearchField());
        module.setDetailModuleId(request.getDetailModuleId());
        requestToTableHeader(request, module);
        module.setUpdatedDate(LocalDateTime.now());
    }

    /**
     * bizDefFlag=1 时，将当前主体和项目下（排除当前模块自身）所有引用了 primaryTable 的关联表设置为只读，并重置对应字段权限。当前模块的关联表由
     * saveModuleTables 处理。
     */
    private void applyBizDefReadOnlyToSubject(SysModule module, String primaryTable) {
        if (!StringUtils.hasText(primaryTable)) {
            return;
        }

        // 1. 查出同 projectNo + subjectId 下其他模块的 ID（排除当前模块）
        List<Long> otherModuleIds =
                sysModuleMapper
                        .selectList(
                                Wrappers.<SysModule>lambdaQuery()
                                        .eq(SysModule::getProjectNo, module.getProjectNo())
                                        .eq(SysModule::getSubjectId, module.getSubjectId())
                                        .ne(SysModule::getId, module.getId())
                                        .select(SysModule::getId))
                        .stream()
                        .map(SysModule::getId)
                        .collect(Collectors.toList());

        if (otherModuleIds.isEmpty()) {
            return;
        }

        // 2. 找出这些模块中 tableName = primaryTable 的关联表记录
        List<SysModuleTable> affectedTables =
                sysModuleTableMapper.selectList(
                        Wrappers.<SysModuleTable>lambdaQuery()
                                .in(SysModuleTable::getModuleId, otherModuleIds)
                                .eq(SysModuleTable::getTableName, primaryTable));

        if (affectedTables.isEmpty()) {
            return;
        }

        // 3. 批量将这些关联表记录设置为只读
        SysModuleTable updatePerm = new SysModuleTable();
        updatePerm.setReadOnly(1);
        sysModuleTableMapper.update(
                updatePerm,
                Wrappers.<SysModuleTable>lambdaQuery()
                        .in(SysModuleTable::getModuleId, otherModuleIds)
                        .eq(SysModuleTable::getTableName, primaryTable));

        // 4. 逐个重置对应模块下该关联表的字段权限
        for (SysModuleTable t : affectedTables) {
            resetTableFieldPermissions(t.getModuleId(), t.getTableName());
        }
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

    /** 将数据来源主体 JSON 字符串解析为 List */
    private List<Long> parseSourceSubjects(String sourceSubjectsJson) {
        try {
            return objectMapper.readValue(sourceSubjectsJson, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.error("sourceSubjects 解析失败: {}", sourceSubjectsJson, e);
            return new ArrayList<>();
        }
    }

    /** 获取主体详细列表 */
    private List<SysModuleCompleteResp.SubjectInfo> buildSubjectInfos(String sourceSubjectsJson) {
        if (sourceSubjectsJson == null) {
            return null;
        }
        List<Long> subjectIds = parseSourceSubjects(sourceSubjectsJson);
        if (CollectionUtils.isEmpty(subjectIds)) {
            return Collections.emptyList();
        }
        try {
            List<SubjectBO> allSubjects = subjectApi.getSubjectList();
            Map<Long, String> subjectMap =
                    CollectionUtils.isNotEmpty(allSubjects)
                            ? allSubjects.stream()
                                    .filter(s -> s.getId() != null)
                                    .collect(
                                            Collectors.toMap(
                                                    SubjectBO::getId,
                                                    SubjectBO::getSubjectName,
                                                    (a, b) -> a))
                            : Collections.emptyMap();
            return subjectIds.stream()
                    .map(
                            id -> {
                                SysModuleCompleteResp.SubjectInfo info =
                                        new SysModuleCompleteResp.SubjectInfo();
                                info.setId(id);
                                info.setSubjectName(subjectMap.getOrDefault(id, ""));
                                return info;
                            })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取数据来源主体列表失败: {}", sourceSubjectsJson, e);
            return subjectIds.stream()
                    .map(
                            id -> {
                                SysModuleCompleteResp.SubjectInfo info =
                                        new SysModuleCompleteResp.SubjectInfo();
                                info.setId(id);
                                info.setSubjectName("");
                                return info;
                            })
                    .collect(Collectors.toList());
        }
    }

    /** 将表头 JSON 字符串解析为 List */
    private List<ModuleTableHeaderDTO> parseTableHeader(String tableHeaderJson) {
        if (tableHeaderJson == null || tableHeaderJson.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(
                    tableHeaderJson, new TypeReference<List<ModuleTableHeaderDTO>>() {});
        } catch (Exception e) {
            log.error("tableHeader 解析失败: {}", tableHeaderJson, e);
            return new ArrayList<>();
        }
    }

    /** 将物理字段映射 JSON 字符串解析为 List */
    private List<ModuleSourceMappingDTO> parseSourceMapping(String sourceMappingJson) {
        if (sourceMappingJson == null || sourceMappingJson.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(
                    sourceMappingJson, new TypeReference<List<ModuleSourceMappingDTO>>() {});
        } catch (Exception e) {
            log.error("sourceMapping 解析失败: {}", sourceMappingJson, e);
            return new ArrayList<>();
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
        wrapper.eq(category != null, SysModule::getCategory, category);
        wrapper.orderByAsc(SysModule::getSortOrder);
        List<SysModule> modules = sysModuleMapper.selectList(wrapper);

        if (CollectionUtils.isEmpty(modules)) {
            return Collections.emptyList();
        }

        // 1. 构建节点映射
        Map<Long, SysModuleSimpleTreeResp> nodeMap = new LinkedHashMap<>();
        for (SysModule module : modules) {
            SysModuleSimpleTreeResp node = new SysModuleSimpleTreeResp();
            node.setId(module.getId());
            node.setModuleCode(module.getModuleCode());
            node.setModuleName(module.getModuleName());
            node.setChildren(new ArrayList<>());
            nodeMap.put(module.getId(), node);
        }

        // 2. 组装模块树
        List<SysModuleSimpleTreeResp> tree = new ArrayList<>();
        for (SysModule module : modules) {
            SysModuleSimpleTreeResp node = nodeMap.get(module.getId());
            Long parentId = module.getParentId();
            if (parentId == null || parentId == 0L || !nodeMap.containsKey(parentId)) {
                tree.add(node);
            } else {
                SysModuleSimpleTreeResp parent = nodeMap.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    tree.add(node);
                }
            }
        }
        return tree;
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
