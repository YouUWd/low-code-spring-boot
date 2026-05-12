package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.dto.request.MoveModuleReq;
import com.jdec.platform.config.api.dto.request.SaveModuleReq;
import com.jdec.platform.config.api.dto.request.SaveModuleReq.SaveSysModuleReq;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp.TableHeaderColumnInfo;
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
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * 模块管理 Service 实现
 *
 * <p>核心配置存储在 {@code config_center} 数据源。
 *
 * <p>对于读操作，显式接收 {@code projectNo} 作为隔离键；
 *
 * <p>对于写操作，优先基于主键 ID 定位记录，并利用 {@code AppContext} 自动填充租户上下文。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysModuleService implements SysModuleApi {

    private final SysModuleMapper sysModuleMapper;
    private final SysModuleTableMapper sysModuleTableMapper;
    private final SysModuleSimpleFieldMapper sysModuleSimpleFieldMapper;
    private final SysModuleCombineFieldMapper sysModuleCombineFieldMapper;
    private final SysModuleStatusMapper sysModuleStatusMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<SysModuleListResp> listModules(String projectNo) {
        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getProjectNo, projectNo);
        wrapper.orderByAsc(SysModule::getParentId).orderByAsc(SysModule::getSortOrder);
        List<SysModule> modules = sysModuleMapper.selectList(wrapper);

        return modules.stream()
                .map(
                        module -> {
                            SysModuleListResp resp = new SysModuleListResp();
                            resp.setId(module.getId());
                            resp.setProjectNo(module.getProjectNo());
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
    public SysModuleCompleteResp getModuleComplete(String projectNo, String moduleCode) {
        SysModule module =
                sysModuleMapper.selectOne(
                        Wrappers.<SysModule>lambdaQuery()
                                .eq(SysModule::getProjectNo, projectNo)
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
        moduleInfo.setProjectNo(module.getProjectNo());
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
        if (module.getParentId() != null && module.getParentId() > 0) {
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

            // 3. 新父组：从 DB 拉取当前子节点（已包含刚写入的 module），
            //    移除 module 后在目标位置插入，完整重编号
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

        // 重新从 DB 读取最新状态返回（洗牌后 sortOrder 已落库）
        return toModuleDetail(sysModuleMapper.selectById(moduleId));
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

    /**
     * 对列表按当前顺序完整重编号（0, 1, 2, ...），仅对 sortOrder 有变化的记录执行批量更新。
     *
     * <p>全量洗牌确保排序连续无断层，避免增量移位累积导致的混乱与不可预测性。
     *
     * @param modules 已按目标顺序排列的模块列表
     */
    private void reshuffleAndUpdate(List<SysModule> modules) {
        if (modules.isEmpty()) {
            return;
        }
        // 利用 MyBatis-Plus 默认 NOT_NULL 更新策略：
        // 构造仅含 id / sortOrder / updatedDate 的最小化对象，
        // updateBatchById 生成的 SQL 只含这两个业务字段，避免全字段更新。
        LocalDateTime now = LocalDateTime.now();
        List<SysModule> partials = new ArrayList<>();
        for (int i = 0; i < modules.size(); i++) {
            SysModule m = modules.get(i);
            if (!Integer.valueOf(i).equals(m.getSortOrder())) {
                // 同步内存对象，保持与 DB 最终状态一致
                m.setSortOrder(i);
                m.setUpdatedDate(now);
                // 构造 partial：只携带需要落库的字段
                SysModule partial = new SysModule();
                partial.setId(m.getId());
                partial.setSortOrder(i);
                partial.setUpdatedDate(now);
                partials.add(partial);
            }
        }
        if (!partials.isEmpty()) {
            Db.updateBatchById(partials);
        }
    }

    /**
     * 级联更新指定模块下所有子孙节点的 modulePath 和 moduleLevel。
     *
     * <p>在跨父模块移动后调用，确保子孙节点的路径与层级与新位置一致。
     *
     * @param rootModuleId 被移动模块的 ID（不含自身，从其直接子节点开始递归）
     * @param oldRootPath 被移动模块移动前的 modulePath
     * @param newRootPath 被移动模块移动后的 modulePath
     * @param levelDiff 层级差值（新 level - 旧 level）
     */
    private void updateDescendantPathsAndLevels(
            Long rootModuleId, String oldRootPath, String newRootPath, int levelDiff) {
        if (levelDiff == 0 && oldRootPath != null && oldRootPath.equals(newRootPath)) {
            // 路径和层级均未变化，无需更新
            return;
        }

        LambdaQueryWrapper<SysModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModule::getParentId, rootModuleId);
        List<SysModule> children = sysModuleMapper.selectList(wrapper);

        if (children.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<SysModule> partials = new ArrayList<>();
        for (SysModule child : children) {
            String childOldPath = child.getModulePath();
            String childNewPath;
            if (childOldPath != null
                    && oldRootPath != null
                    && childOldPath.startsWith(oldRootPath)) {
                childNewPath = newRootPath + childOldPath.substring(oldRootPath.length());
            } else {
                // 兜底：重新拼接
                childNewPath = newRootPath + "/" + child.getModuleCode();
            }
            // 同步内存对象
            child.setModulePath(childNewPath);
            if (child.getModuleLevel() != null) {
                child.setModuleLevel(child.getModuleLevel() + levelDiff);
            }
            child.setUpdatedDate(now);

            // 构造仅含变更字段的 partial，避免全字段更新
            SysModule partial = new SysModule();
            partial.setId(child.getId());
            partial.setModulePath(childNewPath);
            partial.setModuleLevel(child.getModuleLevel());
            partial.setUpdatedDate(now);
            partials.add(partial);

            // 递归处理孙节点
            updateDescendantPathsAndLevels(child.getId(), childOldPath, childNewPath, levelDiff);
        }

        if (!partials.isEmpty()) {
            Db.updateBatchById(partials);
        }
    }

    private SysModule createOrUpdateModule(SaveSysModuleReq request) {
        if (request.getParentId() == null) {
            throw new BusinessException(400, "父模块ID不能为空，根节点请传入0");
        }

        SysModule module = new SysModule();
        module.setProjectNo(AppContext.getProjectNo());
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
        if (module.getParentId() > 0) {
            SysModule parentModule = sysModuleMapper.selectById(module.getParentId());
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
            wrapper.eq(SysModule::getProjectNo, AppContext.getProjectNo())
                    .eq(SysModule::getParentId, module.getParentId())
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
                // 仅更新 leafFlag 字段，避免全字段覆盖
                SysModule partial = new SysModule();
                partial.setId(moduleId);
                partial.setLeafFlag(newLeafFlag);
                sysModuleMapper.updateById(partial);
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
        while (currentParentId > 0) {
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
        resp.setProjectNo(module.getProjectNo());
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
        resp.setTableHeader(parseTableHeader(module.getTableHeader()));

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

    /** 将表头 JSON 字符串解析为 List */
    private List<TableHeaderColumnInfo> parseTableHeader(String tableHeaderJson) {
        if (tableHeaderJson == null || tableHeaderJson.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(
                    tableHeaderJson, new TypeReference<List<TableHeaderColumnInfo>>() {});
        } catch (Exception e) {
            log.error("tableHeader 解析失败: {}", tableHeaderJson, e);
            return new ArrayList<>();
        }
    }
}
