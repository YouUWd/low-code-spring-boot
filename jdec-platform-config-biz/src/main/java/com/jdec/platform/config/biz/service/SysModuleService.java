package com.jdec.platform.config.biz.service;

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
import com.jdec.platform.config.api.dto.response.MoveModuleResp;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.config.api.dto.response.SysModuleListResp;
import com.jdec.platform.config.api.dto.response.SysModuleSimpleTreeResp;
import com.jdec.platform.config.api.dto.response.SysStatusTreeResp;
import com.jdec.platform.config.api.enums.ModuleTypeEnum;
import com.jdec.platform.config.biz.entity.*;
import com.jdec.platform.config.biz.mapper.*;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import com.jdec.platform.shared.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 模块管理业务实现类 负责模块、关联表、字段配置和状态配置的完整生命周期管理 */
@Service
@RequiredArgsConstructor
@Slf4j
@com.jdec.platform.shared.datasource.DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysModuleService implements SysModuleApi {

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

    @Override
    public List<SysModuleListResp> listModules(
            String projectNo, Long subjectId, Boolean availableOnly) {
        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getProjectNo, projectNo);
        wrapper.eq(SysModule::getSubjectId, subjectId);
        // 可选筛选：仅查询可用模块
        if (Boolean.TRUE.equals(availableOnly)) {
            wrapper.eq(SysModule::getModuleStatus, "active");
        }
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
        resp.setModuleLevel(module.getModuleLevel());
        resp.setModulePath(module.getModulePath());
        resp.setPrimaryTable(module.getPrimaryTable());
        resp.setModuleType(module.getModuleType() != null ? module.getModuleType().name() : null);
        resp.setModuleStatus(module.getModuleStatus());
        resp.setApprovalRequired(module.getApprovalRequired());
        resp.setLeafFlag(module.getLeafFlag());
        resp.setSortOrder(module.getSortOrder());
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
    public SysModuleCompleteResp getModuleComplete(
            String projectNo, Long subjectId, String moduleCode) {
        SysModule module =
                sysModuleMapper.selectOne(
                        Wrappers.<SysModule>lambdaQuery()
                                .eq(SysModule::getProjectNo, projectNo)
                                .eq(SysModule::getSubjectId, subjectId)
                                .eq(SysModule::getModuleCode, moduleCode));
        if (module == null) {
            return null;
        }

        return buildModuleCompleteResp(module);
    }

    @Override
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
        moduleInfo.setModuleLevel(module.getModuleLevel());
        moduleInfo.setModulePath(module.getModulePath());
        moduleInfo.setPrimaryTable(module.getPrimaryTable());
        moduleInfo.setModuleType(module.getModuleType());
        moduleInfo.setModuleStatus(module.getModuleStatus());
        moduleInfo.setApprovalRequired(module.getApprovalRequired());
        moduleInfo.setLeafFlag(module.getLeafFlag());
        moduleInfo.setSortOrder(module.getSortOrder());
        moduleInfo.setTableHeader(parseTableHeader(module.getTableHeader()));
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

                                    Map<String, String> colMap =
                                            tableColumnDisplayNameMap.get(f.getTableName());
                                    if (colMap != null) {
                                        info.setDisplayName(colMap.get(f.getFieldCode()));
                                    }
                                    return info;
                                })
                        .collect(Collectors.toList());
        resp.setSimpleFields(simpleFieldInfos);

        List<ModuleCombineFieldDTO> combineFieldInfos =
                allFields.stream()
                        .filter(f -> "COMBINE".equals(f.getFieldType()))
                        .sorted(Comparator.comparing(SysModuleField::getId))
                        .map(
                                f -> {
                                    ModuleCombineFieldDTO info = new ModuleCombineFieldDTO();
                                    info.setId(f.getId());
                                    info.setLogicalField(f.getFieldCode());
                                    info.setDisplayName(f.getDisplayName());
                                    info.setSourceMapping(parseSourceMapping(f.getSourceMapping()));
                                    info.setTransformer(f.getTransformer());
                                    info.setEnabled(f.getEnabled());
                                    return info;
                                })
                        .collect(Collectors.toList());
        resp.setCombineFields(combineFieldInfos);

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

    @Override
    @Transactional
    public Long saveModule(String projectNo, Long subjectId, SaveModuleReq request) {
        if (request.getModule() == null) {
            throw new BusinessException(400, "模块信息不能为空");
        }

        SysModule module;
        Long moduleId;

        // 如果 ID 为空则创建新模块，否则编辑现有模块
        if (request.getModule().getId() == null) {
            // 创建新模块
            module = createOrUpdateModule(projectNo, subjectId, request.getModule());
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

        if (request.getModuleTables() != null) {
            saveModuleTables(moduleId, request.getModuleTables());
        }

        if (request.getSimpleFields() != null) {
            saveSimpleFields(moduleId, request.getSimpleFields());
        }

        if (request.getCombineFields() != null) {
            saveCombineFields(moduleId, request.getCombineFields());
        }

        // 仅DETAIL类型模块保存状态信息
        if (ModuleTypeEnum.DETAIL == module.getModuleType()
                && request.getModuleStatuses() != null) {
            saveModuleStatuses(moduleId, request.getModuleStatuses());
        }

        return moduleId;
    }

    @Override
    @Transactional
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

        // 更新父模块的叶子节点标记
        if (module.getParentId() != null && module.getParentId() > 0) {
            updateLeafFlag(module.getParentId());
        }
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
            targetParent = sysModuleMapper.selectById(targetParentId);
            if (targetParent == null) {
                throw new BusinessException(404, "目标父模块不存在");
            }
            // 检查是否试图将模块移动到其子模块下（防止循环）
            if (isDescendant(moduleId, targetParentId)) {
                throw new BusinessException(400, "不能将模块移动到其子模块下");
            }
        }

        // 判断是否为同父模块拖拽
        assert oldParentId != null;
        if (oldParentId.equals(targetParentId)) {
            // 同父拖拽：全量洗牌。
            // 取出该组全部子节点，移除被拖模块后插入目标位置，然后从 0 开始完整重编号。
            // 相比增量移位，全量洗牌确保 sortOrder 无论 DB 原始状态如何都连续无断层。
            List<SysModule> siblings = getChildrenSorted(oldParentId);
            siblings.removeIf(s -> s.getId().equals(moduleId));

            // clamp 目标位置到合法范围 [0, siblings.size()]
            int insertPos = Math.min(targetSortOrder, siblings.size());
            siblings.add(insertPos, module);

            // reshuffleAndUpdate 内部对 sortOrder 做 diff，位置未变的记录不产生 DB 写入
            reshuffleAndUpdate(siblings);

        } else {
            // 跨父移动

            // 1. 旧父组：移除被移动模块后完整重编号，确保旧父排序连续
            List<SysModule> oldChildren = getChildrenSorted(oldParentId);
            oldChildren.removeIf(s -> s.getId().equals(moduleId));
            reshuffleAndUpdate(oldChildren);

            // 2. 更新被移动模块自身的 parentId / moduleLevel / modulePath
            String oldModulePath = module.getModulePath();
            int oldModuleLevel = module.getModuleLevel() != null ? module.getModuleLevel() : 1;

            module.setParentId(targetParentId);

            if (targetParent != null) {
                int newLevel =
                        (targetParent.getModuleLevel() != null ? targetParent.getModuleLevel() : 0)
                                + 1;
                module.setModuleLevel(newLevel);
                String parentPath =
                        targetParent.getModulePath() != null ? targetParent.getModulePath() : "";
                String newPath =
                        parentPath.isEmpty()
                                ? module.getModuleCode()
                                : parentPath + "/" + module.getModuleCode();
                module.setModulePath(newPath);

                // 更新目标父模块的叶子节点标记（仅更新 leafFlag 字段）
                if (targetParent.getLeafFlag() == 1) {
                    targetParent.setLeafFlag(0);
                    SysModule leafPartial = new SysModule();
                    leafPartial.setId(targetParent.getId());
                    leafPartial.setLeafFlag(0);
                    sysModuleMapper.updateById(leafPartial);
                }
            } else {
                // 移动到根级
                module.setModuleLevel(1);
                module.setModulePath(module.getModuleCode());
            }

            // 仅更新跨父移动涉及的字段：parentId / moduleLevel / modulePath / updatedDate
            LocalDateTime now = LocalDateTime.now();
            module.setUpdatedDate(now);
            SysModule modulePartial = new SysModule();
            modulePartial.setId(module.getId());
            modulePartial.setParentId(module.getParentId());
            modulePartial.setModuleLevel(module.getModuleLevel());
            modulePartial.setModulePath(module.getModulePath());
            modulePartial.setUpdatedDate(now);
            sysModuleMapper.updateById(modulePartial);

            // 3. 新父组：从 DB 拉取当前子节点（已包含刚写入 of module），
            // 移除 module 后在目标位置插入，完整重编号
            List<SysModule> newChildren = getChildrenSorted(targetParentId);
            newChildren.removeIf(s -> s.getId().equals(moduleId));
            int insertPos = Math.min(targetSortOrder, newChildren.size());
            newChildren.add(insertPos, module);
            reshuffleAndUpdate(newChildren);

            // 4. 级联更新所有子孙节点的 modulePath 和 moduleLevel
            updateDescendantPathsAndLevels(
                    moduleId,
                    oldModulePath,
                    module.getModulePath(),
                    module.getModuleLevel() - oldModuleLevel);

            // 5. 更新旧父模块的叶子节点标记
            if (oldParentId > 0) {
                updateLeafFlag(oldParentId);
            }
        }

        // 重新查询最新状态并构造精简响应
        SysModule latest = sysModuleMapper.selectById(moduleId);
        MoveModuleResp resp = new MoveModuleResp();
        resp.setId(latest.getId());
        resp.setParentId(latest.getParentId());
        resp.setModuleLevel(latest.getModuleLevel());
        resp.setModulePath(latest.getModulePath());
        resp.setSortOrder(latest.getSortOrder());
        resp.setUpdatedDate(latest.getUpdatedDate());
        return resp;
    }

    /**
     * 查询指定父节点下的所有直接子节点，按 sortOrder 升序排列，返回可变列表。
     *
     * @param parentId 父节点 ID（null 表示根节点）
     */
    private List<SysModule> getChildrenSorted(Long parentId) {
        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getParentId, parentId != null ? parentId : 0L);
        wrapper.orderByAsc(SysModule::getSortOrder);
        return new ArrayList<>(sysModuleMapper.selectList(wrapper));
    }

    /** 对一组子节点进行重编号，并仅对 sortOrder 发生变化的记录执行 DB 更新。 */
    private void reshuffleAndUpdate(List<SysModule> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            SysModule node = nodes.get(i);
            if (node.getSortOrder() == null || node.getSortOrder() != i) {
                node.setSortOrder(i);
                SysModule partial = new SysModule();
                partial.setId(node.getId());
                partial.setSortOrder(i);
                sysModuleMapper.updateById(partial);
            }
        }
    }

    /** 检查 targetId 是否为 moduleId 的子孙节点 */
    private boolean isDescendant(Long moduleId, Long targetId) {
        SysModule target = sysModuleMapper.selectById(targetId);
        if (target == null) return false;

        String path = target.getModulePath();
        // path 结构通常为 "PARENT/MODULE"
        // 简单通过 path 包含关系判断（需注意边界，或在 path 中包含 ID 以提高准确度）
        // 这里假设 moduleCode 是唯一的，或者 path 中包含模块 ID
        // 实际上更稳健的方式是递归向上查 parentId
        Long currentId = targetId;
        while (currentId != null && currentId > 0) {
            SysModule current = sysModuleMapper.selectById(currentId);
            if (current == null) break;
            if (current.getParentId().equals(moduleId)) return true;
            currentId = current.getParentId();
        }
        return false;
    }

    private void updateDescendantPathsAndLevels(
            Long parentId, String oldParentPath, String newParentPath, int levelDelta) {
        List<SysModule> children =
                sysModuleMapper.selectList(
                        Wrappers.<SysModule>lambdaQuery().eq(SysModule::getParentId, parentId));

        for (SysModule child : children) {
            String oldPath = child.getModulePath();
            String newPath = oldPath.replaceFirst(oldParentPath, newParentPath);
            int newLevel =
                    (child.getModuleLevel() != null ? child.getModuleLevel() : 1) + levelDelta;

            child.setModulePath(newPath);
            child.setModuleLevel(newLevel);

            SysModule partial = new SysModule();
            partial.setId(child.getId());
            partial.setModulePath(newPath);
            partial.setModuleLevel(newLevel);
            sysModuleMapper.updateById(partial);

            // 递归更新孙子节点
            updateDescendantPathsAndLevels(child.getId(), oldPath, newPath, levelDelta);
        }
    }

    private void updateLeafFlag(Long moduleId) {
        Long count =
                sysModuleMapper.selectCount(
                        Wrappers.<SysModule>lambdaQuery().eq(SysModule::getParentId, moduleId));
        int leafFlag = count > 0 ? 0 : 1;

        SysModule partial = new SysModule();
        partial.setId(moduleId);
        partial.setLeafFlag(leafFlag);
        sysModuleMapper.updateById(partial);
    }

    private SysModule createOrUpdateModule(
            String projectNo, Long subjectId, SaveSysModuleReq request) {
        SysModule module = new SysModule();
        module.setProjectNo(projectNo);
        module.setSubjectId(subjectId);
        module.setModuleCode(request.getModuleCode());
        module.setModuleName(request.getModuleName());
        module.setModuleDesc(request.getModuleDesc());
        module.setParentId(request.getParentId() != null ? request.getParentId() : 0L);
        module.setPrimaryTable(request.getPrimaryTable());
        module.setModuleType(convertToModuleTypeEnum(request.getModuleType()));
        module.setApprovalRequired(request.getApprovalRequired());
        module.setModuleStatus("active");
        module.setLeafFlag(1); // 默认新节点是叶子节点

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

        // 计算层级和路径
        if (module.getParentId() > 0) {
            SysModule parent = sysModuleMapper.selectById(module.getParentId());
            if (parent != null) {
                module.setModuleLevel(
                        (parent.getModuleLevel() != null ? parent.getModuleLevel() : 0) + 1);
                String parentPath = parent.getModulePath() != null ? parent.getModulePath() : "";
                module.setModulePath(
                        parentPath.isEmpty()
                                ? module.getModuleCode()
                                : parentPath + "/" + module.getModuleCode());

                // 更新父节点 leafFlag
                if (parent.getLeafFlag() == 1) {
                    SysModule parentUpdate = new SysModule();
                    parentUpdate.setId(parent.getId());
                    parentUpdate.setLeafFlag(0);
                    sysModuleMapper.updateById(parentUpdate);
                }
            } else {
                module.setModuleLevel(1);
                module.setModulePath(module.getModuleCode());
            }
        } else {
            module.setModuleLevel(1);
            module.setModulePath(module.getModuleCode());
        }

        if (request.getTableHeader() != null && !request.getTableHeader().isEmpty()) {
            try {
                module.setTableHeader(objectMapper.writeValueAsString(request.getTableHeader()));
            } catch (Exception e) {
                throw new BusinessException(500, "tableHeader 序列化失败", e);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        module.setCreatedDate(now);
        module.setUpdatedDate(now);
        sysModuleMapper.insert(module);
        return module;
    }

    private void saveModuleTables(Long moduleId, List<ModuleTableDTO> tables) {
        // 先删除旧的关联表
        LambdaQueryWrapper<SysModuleTable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModuleTable::getModuleId, moduleId);
        sysModuleTableMapper.delete(wrapper);

        // 重新保存
        for (int i = 0; i < tables.size(); i++) {
            ModuleTableDTO info = tables.get(i);
            SysModuleTable t = new SysModuleTable();
            t.setModuleId(moduleId);
            t.setTableName(info.getTableName());
            t.setTableDesc(info.getTableDesc());
            t.setJoinLeftField(info.getJoinLeftField());
            t.setJoinRightField(info.getJoinRightField());
            t.setRelationType(info.getRelationType());
            t.setSortOrder(info.getSortOrder() != null ? info.getSortOrder() : i);
            LocalDateTime now = LocalDateTime.now();
            t.setCreatedDate(now);
            t.setUpdatedDate(now);
            sysModuleTableMapper.insert(t);
        }
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
            sysModuleFieldMapper.deleteBatchIds(deleteIds);
            // 级联清理角色权限表与微信参数表的引用
            cleanFieldReferences(deleteIds);
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
            saveOrUpdateList.add(f);
        }

        if (!saveOrUpdateList.isEmpty()) {
            // 使用 MyBatis-Plus 批量更新/插入工具
            Db.saveOrUpdateBatch(saveOrUpdateList);
        }
    }

    private void saveCombineFields(Long moduleId, List<ModuleCombineFieldDTO> fields) {
        // 1. 查询已有的字段配置
        List<SysModuleField> existFields =
                sysModuleFieldMapper.selectList(
                        Wrappers.<SysModuleField>lambdaQuery()
                                .eq(SysModuleField::getModuleId, moduleId)
                                .eq(SysModuleField::getFieldType, "COMBINE"));
        Map<Long, SysModuleField> existFieldMap =
                existFields.stream().collect(Collectors.toMap(SysModuleField::getId, f -> f));

        // 2. 收集请求中提交的有效 ID
        Set<Long> keepIds =
                fields.stream()
                        .map(ModuleCombineFieldDTO::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        // 3. 计算需要删除的孤儿 ID 并执行级联删除
        List<Long> deleteIds =
                existFields.stream()
                        .map(SysModuleField::getId)
                        .filter(id -> !keepIds.contains(id))
                        .collect(Collectors.toList());

        if (!deleteIds.isEmpty()) {
            sysModuleFieldMapper.deleteBatchIds(deleteIds);
            // 级联清理角色权限表与微信参数表的引用
            cleanFieldReferences(deleteIds);
        }

        // 4. 组装待批量保存/更新的列表
        List<SysModuleField> saveOrUpdateList = new ArrayList<>();
        for (ModuleCombineFieldDTO info : fields) {
            SysModuleField f;
            if (info.getId() != null && existFieldMap.containsKey(info.getId())) {
                f = existFieldMap.get(info.getId());
            } else {
                f = new SysModuleField();
                f.setModuleId(moduleId);
                f.setFieldType("COMBINE");
            }
            f.setFieldCode(info.getLogicalField());
            f.setTableName("*");
            f.setDisplayName(info.getDisplayName());
            try {
                f.setSourceMapping(objectMapper.writeValueAsString(info.getSourceMapping()));
            } catch (Exception e) {
                throw new BusinessException(500, "sourceMapping 序列化失败", e);
            }
            f.setTransformer(info.getTransformer());
            f.setEnabled(info.getEnabled() != null ? info.getEnabled() : 1);
            saveOrUpdateList.add(f);
        }

        if (!saveOrUpdateList.isEmpty()) {
            Db.saveOrUpdateBatch(saveOrUpdateList);
        }
    }

    private void saveModuleStatuses(Long moduleId, List<ModuleStatusDTO> statuses) {
        // 先删除旧状态
        LambdaQueryWrapper<SysModuleStatus> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModuleStatus::getModuleId, moduleId);
        sysModuleStatusMapper.delete(wrapper);

        // 重新保存
        for (ModuleStatusDTO info : statuses) {
            SysModuleStatus s = new SysModuleStatus();
            s.setModuleId(moduleId);
            s.setStatusPid(info.getStatusPid());
            s.setStatusId(info.getStatusId());
            LocalDateTime now = LocalDateTime.now();
            s.setCreatedDate(now);
            s.setUpdatedDate(now);
            sysModuleStatusMapper.insert(s);
        }
    }

    private void updateModuleInfo(SysModule module, SaveSysModuleReq request) {
        module.setModuleName(request.getModuleName());
        module.setModuleDesc(request.getModuleDesc());
        module.setPrimaryTable(request.getPrimaryTable());
        module.setModuleType(convertToModuleTypeEnum(request.getModuleType()));
        module.setApprovalRequired(request.getApprovalRequired());

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
    public List<SysModuleSimpleTreeResp> getAvailableModuleTree(String projectNo, Long subjectId) {
        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getProjectNo, projectNo);
        wrapper.eq(SysModule::getSubjectId, subjectId);
        wrapper.eq(SysModule::getModuleStatus, "active");
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
}
