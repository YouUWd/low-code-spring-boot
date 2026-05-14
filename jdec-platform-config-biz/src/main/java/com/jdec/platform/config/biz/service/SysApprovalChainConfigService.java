package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.jdec.platform.config.api.SysApprovalChainConfigApi;
import com.jdec.platform.config.api.dto.request.ApprovalChainConfigPageReq;
import com.jdec.platform.config.api.dto.request.ApprovalChainConfigSaveReq;
import com.jdec.platform.config.api.dto.response.ApprovalChainCascadeResp;
import com.jdec.platform.config.api.dto.response.ApprovalChainConfigDetailResp;
import com.jdec.platform.config.api.dto.response.ApprovalChainConfigPageResp;
import com.jdec.platform.config.api.dto.response.ApprovalChainConfigSaveResp;
import com.jdec.platform.config.biz.entity.*;
import com.jdec.platform.config.biz.mapper.*;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.PageResult;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 审批链配置 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysApprovalChainConfigService implements SysApprovalChainConfigApi {

    private final SysApprovalChainConfigMapper sysApprovalChainConfigMapper;
    private final SysApprovalChainTypeMapper sysApprovalChainTypeMapper;
    private final SysModuleMapper sysModuleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserMapper sysUserMapper;
    private final SysStatusMapper sysStatusMapper;
    private final SysConfigItemMapper sysConfigItemMapper;
    private final SysApprovalChainConfigButtonMapper sysApprovalChainConfigButtonMapper;

    @Override
    public List<ApprovalChainCascadeResp> getCascadeOptions() {
        // 查询当前主体和应用下所有审批链配置中使用的模块ID
        List<SysApprovalChainConfig> configs =
                sysApprovalChainConfigMapper.selectList(
                        new LambdaQueryWrapper<SysApprovalChainConfig>()
                                .eq(SysApprovalChainConfig::getSubjectId, AppContext.getSubjectId())
                                .eq(SysApprovalChainConfig::getProjectNo, AppContext.getProjectNo())
                                .select(SysApprovalChainConfig::getModuleId)
                                .groupBy(SysApprovalChainConfig::getModuleId));

        if (configs.isEmpty()) {
            return Collections.emptyList();
        }

        // 提取模块ID列表
        Set<Long> moduleIds =
                configs.stream()
                        .map(SysApprovalChainConfig::getModuleId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        if (moduleIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 查询模块信息
        List<SysModule> modules =
                sysModuleMapper.selectList(
                        new LambdaQueryWrapper<SysModule>()
                                .in(SysModule::getId, moduleIds)
                                .eq(SysModule::getSubjectId, AppContext.getSubjectId())
                                .eq(SysModule::getProjectNo, AppContext.getProjectNo())
                                .orderByAsc(SysModule::getSortOrder));

        // 查询所有审批链分类
        List<SysApprovalChainType> chainTypes =
                sysApprovalChainTypeMapper.selectList(
                        new LambdaQueryWrapper<SysApprovalChainType>()
                                .eq(SysApprovalChainType::getSubjectId, AppContext.getSubjectId())
                                .eq(SysApprovalChainType::getProjectNo, AppContext.getProjectNo())
                                .eq(SysApprovalChainType::getEnabled, 1)
                                .in(SysApprovalChainType::getModuleId, moduleIds));

        // 按模块ID分组审批链分类
        Map<Long, List<SysApprovalChainType>> chainTypeMap =
                chainTypes.stream()
                        .collect(Collectors.groupingBy(SysApprovalChainType::getModuleId));

        // 构建响应
        return modules.stream()
                .map(
                        module -> {
                            ApprovalChainCascadeResp resp = new ApprovalChainCascadeResp();
                            resp.setModuleId(module.getId());
                            resp.setModuleName(module.getModuleName());

                            List<SysApprovalChainType> types =
                                    chainTypeMap.getOrDefault(
                                            module.getId(), Collections.emptyList());
                            List<ApprovalChainCascadeResp.ApprovalChainTypeOption> options =
                                    types.stream()
                                            .map(
                                                    type -> {
                                                        ApprovalChainCascadeResp
                                                                        .ApprovalChainTypeOption
                                                                option =
                                                                        new ApprovalChainCascadeResp
                                                                                .ApprovalChainTypeOption();
                                                        option.setId(type.getId());
                                                        option.setTitle(type.getTitle());
                                                        option.setDefaultFlag(
                                                                type.getDefaultFlag());
                                                        return option;
                                                    })
                                            .collect(Collectors.toList());
                            resp.setChainTypes(options);
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<ApprovalChainConfigPageResp> getPage(ApprovalChainConfigPageReq request) {
        Page<SysApprovalChainConfig> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<SysApprovalChainConfig> wrapper =
                new LambdaQueryWrapper<SysApprovalChainConfig>()
                        .eq(SysApprovalChainConfig::getSubjectId, AppContext.getSubjectId())
                        .eq(SysApprovalChainConfig::getProjectNo, AppContext.getProjectNo())
                        .eq(
                                request.getModuleId() != null,
                                SysApprovalChainConfig::getModuleId,
                                request.getModuleId())
                        .in(
                                request.getApprovalChainTypeIds() != null
                                        && !request.getApprovalChainTypeIds().isEmpty(),
                                SysApprovalChainConfig::getApprovalChainTypeId,
                                request.getApprovalChainTypeIds())
                        .orderByAsc(SysApprovalChainConfig::getCurrentStep);

        IPage<SysApprovalChainConfig> configPage =
                sysApprovalChainConfigMapper.selectPage(page, wrapper);

        // 收集所有需要查询的ID
        Set<Long> moduleIds = new HashSet<>();
        Set<Long> chainTypeIds = new HashSet<>();
        Set<Long> roleIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        Set<Long> statusIds = new HashSet<>();
        Set<String> approvalRuleValues = new HashSet<>();

        for (SysApprovalChainConfig config : configPage.getRecords()) {
            if (config.getModuleId() != null) moduleIds.add(config.getModuleId());
            if (config.getApprovalChainTypeId() != null)
                chainTypeIds.add(config.getApprovalChainTypeId());
            if (config.getApproveRoleId() != null) roleIds.add(config.getApproveRoleId());
            if (config.getApproverId() != null) userIds.add(config.getApproverId());
            if (config.getDelegateApproverId() != null) userIds.add(config.getDelegateApproverId());
            if (config.getCurrentStatusGroupId() != null)
                statusIds.add(config.getCurrentStatusGroupId());
            if (config.getCurrentStatusChildId() != null)
                statusIds.add(config.getCurrentStatusChildId());
            if (config.getNextStatusGroupId() != null) statusIds.add(config.getNextStatusGroupId());
            if (config.getNextStatusChildId() != null) statusIds.add(config.getNextStatusChildId());
            if (config.getApprovalRule() != null) approvalRuleValues.add(config.getApprovalRule());
        }

        // 批量查询关联数据
        Map<Long, String> moduleMap = batchQueryModules(moduleIds);
        Map<Long, String> chainTypeMap = batchQueryChainTypes(chainTypeIds);
        Map<Long, String> roleMap = batchQueryRoles(roleIds);
        Map<Long, String> userMap = batchQueryUsers(userIds);
        Map<Long, String> statusMap = batchQueryStatuses(statusIds);
        Map<String, String> approvalRuleMap = batchQueryApprovalRules(approvalRuleValues);

        // 构建响应
        List<ApprovalChainConfigPageResp> respList =
                configPage.getRecords().stream()
                        .map(
                                config -> {
                                    ApprovalChainConfigPageResp resp =
                                            new ApprovalChainConfigPageResp();
                                    resp.setId(config.getId());
                                    resp.setModuleId(config.getModuleId());
                                    resp.setModuleName(moduleMap.get(config.getModuleId()));
                                    resp.setUpStep(config.getUpStep());
                                    resp.setCurrentStep(config.getCurrentStep());
                                    resp.setNextStep(config.getNextStep());
                                    resp.setApprovalChainTypeId(config.getApprovalChainTypeId());
                                    resp.setApprovalChainTypeName(
                                            chainTypeMap.get(config.getApprovalChainTypeId()));
                                    resp.setApprovalRule(config.getApprovalRule());
                                    resp.setApprovalRuleName(
                                            approvalRuleMap.get(config.getApprovalRule()));
                                    resp.setRoleApprovalPercent(config.getRoleApprovalPercent());
                                    resp.setApproveRoleId(config.getApproveRoleId());
                                    resp.setApproveRoleName(roleMap.get(config.getApproveRoleId()));
                                    resp.setApproverId(config.getApproverId());
                                    resp.setApproverName(userMap.get(config.getApproverId()));
                                    resp.setDelegateApproverId(config.getDelegateApproverId());
                                    resp.setDelegateApproverName(
                                            userMap.get(config.getDelegateApproverId()));
                                    resp.setSkipped(config.getSkipped());
                                    resp.setShowed(config.getShowed());
                                    resp.setChildModuleIds(config.getChildModuleIds());
                                    resp.setCurrentStatusGroupId(config.getCurrentStatusGroupId());
                                    resp.setCurrentStatusGroupName(
                                            statusMap.get(config.getCurrentStatusGroupId()));
                                    resp.setCurrentStatusChildId(config.getCurrentStatusChildId());
                                    resp.setCurrentStatusChildName(
                                            statusMap.get(config.getCurrentStatusChildId()));
                                    resp.setNextStatusGroupId(config.getNextStatusGroupId());
                                    resp.setNextStatusGroupName(
                                            statusMap.get(config.getNextStatusGroupId()));
                                    resp.setNextStatusChildId(config.getNextStatusChildId());
                                    resp.setNextStatusChildName(
                                            statusMap.get(config.getNextStatusChildId()));
                                    resp.setAutoApproved(config.getAutoApproved());
                                    resp.setAutoApprovedTime(config.getAutoApprovedTime());
                                    resp.setDeadlineTime(config.getDeadlineTime());
                                    resp.setButtonList(config.getButtonList());
                                    resp.setCollaborated(config.getCollaborated());
                                    resp.setRepeated(config.getRepeated());
                                    resp.setMsgTemplateIds(config.getMsgTemplateIds());
                                    resp.setDefaultFlag(config.getDefaultFlag());
                                    return resp;
                                })
                        .collect(Collectors.toList());

        return PageResult.of(
                request.getPageNum(), request.getPageSize(), configPage.getTotal(), respList);
    }

    private Map<Long, String> batchQueryModules(Set<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        return sysModuleMapper
                .selectList(new LambdaQueryWrapper<SysModule>().in(SysModule::getId, ids))
                .stream()
                .collect(Collectors.toMap(SysModule::getId, SysModule::getModuleName));
    }

    private Map<Long, String> batchQueryChainTypes(Set<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        return sysApprovalChainTypeMapper
                .selectList(
                        new LambdaQueryWrapper<SysApprovalChainType>()
                                .in(SysApprovalChainType::getId, ids))
                .stream()
                .collect(
                        Collectors.toMap(
                                SysApprovalChainType::getId, SysApprovalChainType::getTitle));
    }

    private Map<Long, String> batchQueryRoles(Set<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        return sysRoleMapper
                .selectList(new LambdaQueryWrapper<SysRole>().in(SysRole::getId, ids))
                .stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleName));
    }

    private Map<Long, String> batchQueryUsers(Set<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        return sysUserMapper
                .selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getId, ids))
                .stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getUserName));
    }

    private Map<Long, String> batchQueryStatuses(Set<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        return sysStatusMapper
                .selectList(new LambdaQueryWrapper<SysStatus>().in(SysStatus::getId, ids))
                .stream()
                .collect(Collectors.toMap(SysStatus::getId, SysStatus::getTitle));
    }

    private Map<String, String> batchQueryApprovalRules(Set<String> values) {
        if (values.isEmpty()) return Collections.emptyMap();
        return sysConfigItemMapper
                .selectList(
                        new LambdaQueryWrapper<SysConfigItem>()
                                .eq(SysConfigItem::getCategoryAlias, "approvalRule")
                                .in(SysConfigItem::getValue, values)
                                .eq(SysConfigItem::getStatus, 1))
                .stream()
                .collect(Collectors.toMap(SysConfigItem::getValue, SysConfigItem::getLabel));
    }

    @Override
    public ApprovalChainConfigDetailResp getDetail(Long id) {
        // 查询配置
        SysApprovalChainConfig config = sysApprovalChainConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("配置不存在");
        }

        // 查询按钮配置
        List<SysApprovalChainConfigButton> buttons =
                sysApprovalChainConfigButtonMapper.selectList(
                        new LambdaQueryWrapper<SysApprovalChainConfigButton>()
                                .eq(SysApprovalChainConfigButton::getPid, id));

        // 构建响应
        ApprovalChainConfigDetailResp resp = new ApprovalChainConfigDetailResp();
        resp.setId(config.getId());
        resp.setModuleId(config.getModuleId());
        resp.setUpStep(config.getUpStep());
        resp.setCurrentStep(config.getCurrentStep());
        resp.setNextStep(config.getNextStep());
        resp.setApprovalChainTypeId(config.getApprovalChainTypeId());
        resp.setApprovalRule(config.getApprovalRule());
        resp.setRoleApprovalPercent(config.getRoleApprovalPercent());
        resp.setApproveRoleId(config.getApproveRoleId());
        resp.setApproverId(config.getApproverId());
        resp.setDelegateApproverId(config.getDelegateApproverId());
        resp.setSkipped(config.getSkipped());
        resp.setShowed(config.getShowed());
        resp.setChildModuleIds(config.getChildModuleIds());
        resp.setCurrentStatusGroupId(config.getCurrentStatusGroupId());
        resp.setCurrentStatusChildId(config.getCurrentStatusChildId());
        resp.setNextStatusGroupId(config.getNextStatusGroupId());
        resp.setNextStatusChildId(config.getNextStatusChildId());
        resp.setAutoApproved(config.getAutoApproved());
        resp.setAutoApprovedTime(config.getAutoApprovedTime());
        resp.setDeadlineTime(config.getDeadlineTime());
        resp.setButtonList(config.getButtonList());
        resp.setCollaborated(config.getCollaborated());
        resp.setRepeated(config.getRepeated());
        resp.setMsgTemplateIds(config.getMsgTemplateIds());
        resp.setDefaultFlag(config.getDefaultFlag());

        // 转换按钮配置
        if (!buttons.isEmpty()) {
            List<ApprovalChainConfigDetailResp.ButtonConfigDetail> buttonDetails =
                    buttons.stream()
                            .map(
                                    btn -> {
                                        ApprovalChainConfigDetailResp.ButtonConfigDetail detail =
                                                new ApprovalChainConfigDetailResp
                                                        .ButtonConfigDetail();
                                        detail.setId(btn.getId());
                                        detail.setButtonTypeId(btn.getButtonTypeId());
                                        detail.setButtonId(btn.getButtonId());
                                        detail.setIcon(btn.getIcon());
                                        detail.setLogName(btn.getLogName());
                                        detail.setMsgTemplateIds(btn.getMsgTemplateIds());
                                        return detail;
                                    })
                            .collect(Collectors.toList());
            resp.setButtonConfigs(buttonDetails);
        }

        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalChainConfigSaveResp save(ApprovalChainConfigSaveReq request) {
        Long id = request.getId();

        // 设置默认值
        if (request.getUpStep() == null) {
            request.setUpStep(0);
        }
        if (request.getDefaultFlag() == null) {
            request.setDefaultFlag(0);
        }
        if (request.getAutoApproved() == null) {
            request.setAutoApproved(0);
        }
        if (request.getSkipped() == null) {
            request.setSkipped(0);
        }
        if (request.getShowed() == null) {
            request.setShowed(0);
        }

        // 计算步骤
        request.setCurrentStep(request.getUpStep() + 1);
        if (request.getNextStep() == null) {
            request.setNextStep(request.getCurrentStep() + 1);
        }

        // 校验：当前主状态不能与下一主状态相同
        if (request.getCurrentStatusGroupId().equals(request.getNextStatusGroupId())) {
            throw new BusinessException("当前主状态不能与下一主状态相同，请重新选择");
        }

        // 准备实体数据
        SysApprovalChainConfig config = convertToEntity(request);

        SysApprovalChainConfig upConfig;
        SysApprovalChainConfig nextConfig;

        if (id == null || id == 0) {
            // 新增逻辑
            // 唯一性检查：同类型+当前主状态+下一主状态
            SysApprovalChainConfig exist =
                    sysApprovalChainConfigMapper.selectOne(
                            new LambdaQueryWrapper<SysApprovalChainConfig>()
                                    .eq(
                                            SysApprovalChainConfig::getSubjectId,
                                            AppContext.getSubjectId())
                                    .eq(
                                            SysApprovalChainConfig::getProjectNo,
                                            AppContext.getProjectNo())
                                    .eq(
                                            SysApprovalChainConfig::getApprovalChainTypeId,
                                            config.getApprovalChainTypeId())
                                    .eq(
                                            SysApprovalChainConfig::getCurrentStatusGroupId,
                                            config.getCurrentStatusGroupId())
                                    .eq(
                                            SysApprovalChainConfig::getNextStatusGroupId,
                                            config.getNextStatusGroupId()));
            if (exist != null) {
                throw new BusinessException("当前步骤已存在，请重新选择");
            }

            // 插入
            config.setId(null);
            config.setSubjectId(AppContext.getSubjectId());
            config.setProjectNo(AppContext.getProjectNo());
            sysApprovalChainConfigMapper.insert(config);
            id = config.getId();

            // 查询上一步
            upConfig =
                    sysApprovalChainConfigMapper.selectOne(
                            new LambdaQueryWrapper<SysApprovalChainConfig>()
                                    .eq(
                                            SysApprovalChainConfig::getSubjectId,
                                            AppContext.getSubjectId())
                                    .eq(
                                            SysApprovalChainConfig::getProjectNo,
                                            AppContext.getProjectNo())
                                    .eq(SysApprovalChainConfig::getModuleId, config.getModuleId())
                                    .eq(SysApprovalChainConfig::getCurrentStep, config.getUpStep())
                                    .eq(
                                            SysApprovalChainConfig::getApprovalChainTypeId,
                                            config.getApprovalChainTypeId()));

            // 查询下一步
            nextConfig =
                    sysApprovalChainConfigMapper.selectOne(
                            new LambdaQueryWrapper<SysApprovalChainConfig>()
                                    .eq(
                                            SysApprovalChainConfig::getSubjectId,
                                            AppContext.getSubjectId())
                                    .eq(
                                            SysApprovalChainConfig::getProjectNo,
                                            AppContext.getProjectNo())
                                    .ne(SysApprovalChainConfig::getId, id)
                                    .eq(SysApprovalChainConfig::getModuleId, config.getModuleId())
                                    .eq(
                                            SysApprovalChainConfig::getCurrentStep,
                                            config.getCurrentStep())
                                    .eq(
                                            SysApprovalChainConfig::getApprovalChainTypeId,
                                            config.getApprovalChainTypeId()));

        } else {
            // 修改逻辑
            SysApprovalChainConfig exist = sysApprovalChainConfigMapper.selectById(id);
            if (exist == null) {
                throw new BusinessException("配置不存在");
            }

            // 查询上一步
            upConfig =
                    sysApprovalChainConfigMapper.selectOne(
                            new LambdaQueryWrapper<SysApprovalChainConfig>()
                                    .eq(
                                            SysApprovalChainConfig::getSubjectId,
                                            AppContext.getSubjectId())
                                    .eq(
                                            SysApprovalChainConfig::getProjectNo,
                                            AppContext.getProjectNo())
                                    .eq(SysApprovalChainConfig::getModuleId, exist.getModuleId())
                                    .eq(
                                            SysApprovalChainConfig::getApprovalChainTypeId,
                                            exist.getApprovalChainTypeId())
                                    .eq(SysApprovalChainConfig::getCurrentStep, exist.getUpStep()));

            // 查询下一步
            nextConfig =
                    sysApprovalChainConfigMapper.selectOne(
                            new LambdaQueryWrapper<SysApprovalChainConfig>()
                                    .eq(
                                            SysApprovalChainConfig::getSubjectId,
                                            AppContext.getSubjectId())
                                    .eq(
                                            SysApprovalChainConfig::getProjectNo,
                                            AppContext.getProjectNo())
                                    .eq(SysApprovalChainConfig::getModuleId, exist.getModuleId())
                                    .eq(
                                            SysApprovalChainConfig::getApprovalChainTypeId,
                                            exist.getApprovalChainTypeId())
                                    .eq(
                                            SysApprovalChainConfig::getCurrentStep,
                                            exist.getNextStep()));

            // 更新
            config.setId(id);
            sysApprovalChainConfigMapper.updateById(config);
        }

        // 处理按钮列表
        if (request.getButtonConfigs() != null && !request.getButtonConfigs().isEmpty()) {
            // 删除旧按钮
            sysApprovalChainConfigButtonMapper.delete(
                    new LambdaQueryWrapper<SysApprovalChainConfigButton>()
                            .eq(SysApprovalChainConfigButton::getPid, id));

            // 批量插入新按钮
            Long finalId = id;
            List<SysApprovalChainConfigButton> buttons =
                    request.getButtonConfigs().stream()
                            .map(
                                    btn -> {
                                        SysApprovalChainConfigButton button =
                                                new SysApprovalChainConfigButton();
                                        button.setPid(finalId);
                                        button.setButtonTypeId(btn.getButtonTypeId());
                                        button.setIcon(btn.getIcon());
                                        button.setButtonId(btn.getButtonId());
                                        button.setLogName(btn.getLogName());
                                        button.setMsgTemplateIds(btn.getMsgTemplateIds());
                                        button.setSubjectId(AppContext.getSubjectId());
                                        button.setProjectNo(AppContext.getProjectNo());
                                        return button;
                                    })
                            .collect(Collectors.toList());

            // 使用 Db.saveBatch 批量插入
            Db.saveBatch(buttons);
        }

        // 调整步骤关联
        rearrangeStepsAfterSave(config, id, upConfig, nextConfig);

        return new ApprovalChainConfigSaveResp(id, 1);
    }

    private SysApprovalChainConfig convertToEntity(ApprovalChainConfigSaveReq request) {
        SysApprovalChainConfig config = new SysApprovalChainConfig();
        config.setId(request.getId());
        config.setModuleId(request.getModuleId());
        config.setUpStep(request.getUpStep());
        config.setCurrentStep(request.getCurrentStep());
        config.setNextStep(request.getNextStep());
        config.setApprovalChainTypeId(request.getApprovalChainTypeId());
        config.setApprovalRule(request.getApprovalRule());
        config.setRoleApprovalPercent(request.getRoleApprovalPercent());
        config.setApproveRoleId(request.getApproveRoleId());
        config.setApproverId(request.getApproverId());
        config.setDelegateApproverId(request.getDelegateApproverId());
        config.setSkipped(request.getSkipped());
        config.setShowed(request.getShowed());
        config.setChildModuleIds(request.getChildModuleIds());
        config.setCurrentStatusGroupId(request.getCurrentStatusGroupId());
        config.setCurrentStatusChildId(request.getCurrentStatusChildId());
        config.setNextStatusGroupId(request.getNextStatusGroupId());
        config.setNextStatusChildId(request.getNextStatusChildId());
        config.setAutoApproved(request.getAutoApproved());
        config.setAutoApprovedTime(request.getAutoApprovedTime());
        config.setDeadlineTime(request.getDeadlineTime());
        config.setButtonList(request.getButtonList());
        config.setCollaborated(request.getCollaborated());
        config.setRepeated(request.getRepeated());
        config.setMsgTemplateIds(request.getMsgTemplateIds());
        config.setDefaultFlag(request.getDefaultFlag());
        return config;
    }

    private void rearrangeStepsAfterSave(
            SysApprovalChainConfig currentConfig,
            Long currentId,
            SysApprovalChainConfig upConfig,
            SysApprovalChainConfig nextConfig) {

        Long oldModuleId = 0L;
        Long oldApprovalChainTypeId = 0L;

        // 1. 处理上一步（如果存在）
        if (upConfig != null) {
            oldModuleId = upConfig.getModuleId();
            oldApprovalChainTypeId = upConfig.getApprovalChainTypeId();

            // 校验冲突：上一步的当前主状态不能等于当前步骤的当前主状态
            if (upConfig.getCurrentStatusGroupId()
                    .equals(currentConfig.getCurrentStatusGroupId())) {
                throw new BusinessException("当前步骤当前主状态与上一步骤主状态冲突，请重新选择");
            }
            // 上一步的当前主状态不能等于当前步骤的下一主状态
            if (upConfig.getCurrentStatusGroupId().equals(currentConfig.getNextStatusGroupId())) {
                throw new BusinessException("当前步骤之后主状态与上一步骤当前主状态冲突，请重新选择");
            }

            // 更新上一步的下一状态为当前步骤的当前状态
            upConfig.setNextStatusGroupId(currentConfig.getCurrentStatusGroupId());
            upConfig.setNextStatusChildId(currentConfig.getCurrentStatusChildId());
            sysApprovalChainConfigMapper.updateById(upConfig);
        }

        // 2. 处理下一步（如果存在）
        if (nextConfig != null) {
            oldModuleId = nextConfig.getModuleId();
            oldApprovalChainTypeId = nextConfig.getApprovalChainTypeId();

            // 校验冲突：下一步的下一主状态不能等于当前步骤的当前主状态
            if (nextConfig.getNextStatusGroupId().equals(currentConfig.getCurrentStatusGroupId())) {
                throw new BusinessException("当前步骤当前主状态与下一步骤之后主状态冲突，请重新选择");
            }
            // 下一步的下一主状态不能等于当前步骤的下一主状态
            if (nextConfig.getNextStatusGroupId().equals(currentConfig.getNextStatusGroupId())) {
                throw new BusinessException("当前步骤之后主状态与下一步骤之后主状态冲突，请重新选择");
            }

            // 更新下一步的当前状态为当前步骤的下一状态
            nextConfig.setCurrentStatusGroupId(currentConfig.getNextStatusGroupId());
            nextConfig.setCurrentStatusChildId(currentConfig.getNextStatusChildId());
            sysApprovalChainConfigMapper.updateById(nextConfig);
        }

        // 3. 如果分类或模块改变，需要更新同模块同类型下所有记录的module_id和type
        if ((oldModuleId > 0 && oldApprovalChainTypeId > 0)
                && (!oldModuleId.equals(currentConfig.getModuleId())
                        || !oldApprovalChainTypeId.equals(
                                currentConfig.getApprovalChainTypeId()))) {
            // 查询该模块下所有记录
            List<SysApprovalChainConfig> allList =
                    sysApprovalChainConfigMapper.selectList(
                            new LambdaQueryWrapper<SysApprovalChainConfig>()
                                    .eq(
                                            SysApprovalChainConfig::getSubjectId,
                                            AppContext.getSubjectId())
                                    .eq(
                                            SysApprovalChainConfig::getProjectNo,
                                            AppContext.getProjectNo())
                                    .eq(SysApprovalChainConfig::getModuleId, oldModuleId)
                                    .eq(
                                            SysApprovalChainConfig::getApprovalChainTypeId,
                                            oldApprovalChainTypeId)
                                    .orderByAsc(SysApprovalChainConfig::getCurrentStep));

            for (SysApprovalChainConfig item : allList) {
                item.setApprovalChainTypeId(currentConfig.getApprovalChainTypeId());
                item.setModuleId(currentConfig.getModuleId());
                item.setDefaultFlag(currentConfig.getDefaultFlag());
                sysApprovalChainConfigMapper.updateById(item);
            }
        }

        // 4. 重新编号当前步骤之后的所有步骤（如果当前步骤位置发生变化）
        // 查询所有步骤中，current_step > up_step 的记录（排除当前id）
        List<SysApprovalChainConfig> laterSteps =
                sysApprovalChainConfigMapper.selectList(
                        new LambdaQueryWrapper<SysApprovalChainConfig>()
                                .ne(SysApprovalChainConfig::getId, currentId)
                                .eq(SysApprovalChainConfig::getSubjectId, AppContext.getSubjectId())
                                .eq(SysApprovalChainConfig::getProjectNo, AppContext.getProjectNo())
                                .eq(
                                        SysApprovalChainConfig::getModuleId,
                                        currentConfig.getModuleId())
                                .gt(
                                        SysApprovalChainConfig::getCurrentStep,
                                        currentConfig.getUpStep())
                                .orderByAsc(SysApprovalChainConfig::getCurrentStep));

        if (!laterSteps.isEmpty()) {
            for (int i = 0; i < laterSteps.size(); i++) {
                SysApprovalChainConfig step = laterSteps.get(i);
                int newCurrentStep = currentConfig.getCurrentStep() + 1 + i;
                int newNextStep = currentConfig.getNextStep() + 1 + i;
                int newUpStep = currentConfig.getUpStep() + 1 + i;
                step.setCurrentStep(newCurrentStep);
                step.setNextStep(newNextStep);
                step.setUpStep(newUpStep);
                sysApprovalChainConfigMapper.updateById(step);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 查询要删除的配置
        SysApprovalChainConfig deletedConfig = sysApprovalChainConfigMapper.selectById(id);
        if (deletedConfig == null) {
            throw new BusinessException("配置不存在");
        }

        // 查询上一步配置
        SysApprovalChainConfig upStepConfig =
                sysApprovalChainConfigMapper.selectOne(
                        new LambdaQueryWrapper<SysApprovalChainConfig>()
                                .eq(SysApprovalChainConfig::getSubjectId, AppContext.getSubjectId())
                                .eq(SysApprovalChainConfig::getProjectNo, AppContext.getProjectNo())
                                .eq(
                                        SysApprovalChainConfig::getModuleId,
                                        deletedConfig.getModuleId())
                                .eq(
                                        SysApprovalChainConfig::getApprovalChainTypeId,
                                        deletedConfig.getApprovalChainTypeId())
                                .eq(
                                        SysApprovalChainConfig::getCurrentStep,
                                        deletedConfig.getUpStep()));

        // 查询下一步配置
        SysApprovalChainConfig nextStepConfig =
                sysApprovalChainConfigMapper.selectOne(
                        new LambdaQueryWrapper<SysApprovalChainConfig>()
                                .eq(SysApprovalChainConfig::getSubjectId, AppContext.getSubjectId())
                                .eq(SysApprovalChainConfig::getProjectNo, AppContext.getProjectNo())
                                .eq(
                                        SysApprovalChainConfig::getModuleId,
                                        deletedConfig.getModuleId())
                                .eq(
                                        SysApprovalChainConfig::getApprovalChainTypeId,
                                        deletedConfig.getApprovalChainTypeId())
                                .eq(
                                        SysApprovalChainConfig::getCurrentStep,
                                        deletedConfig.getNextStep()));

        // 删除配置
        sysApprovalChainConfigMapper.deleteById(id);

        // 删除关联的按钮配置
        sysApprovalChainConfigButtonMapper.delete(
                new LambdaQueryWrapper<SysApprovalChainConfigButton>()
                        .eq(SysApprovalChainConfigButton::getPid, id));

        // 重新编排步骤顺序
        rearrangeStepsAfterDelete(deletedConfig, upStepConfig, nextStepConfig);
    }

    /**
     * 删除后重新编排步骤顺序
     *
     * @param deletedConfig 被删除的配置
     * @param upStepConfig 上一步配置（可为空）
     * @param nextStepConfig 下一步配置（可为空）
     */
    private void rearrangeStepsAfterDelete(
            SysApprovalChainConfig deletedConfig,
            SysApprovalChainConfig upStepConfig,
            SysApprovalChainConfig nextStepConfig) {
        // 如果上一步和下一步都存在，则把下一步的当前状态改为上一步的下一状态（衔接）
        if (upStepConfig != null && nextStepConfig != null) {
            nextStepConfig.setCurrentStatusGroupId(upStepConfig.getNextStatusGroupId());
            nextStepConfig.setCurrentStatusChildId(upStepConfig.getNextStatusChildId());
            sysApprovalChainConfigMapper.updateById(nextStepConfig);
        }

        // 查询该模块下所有配置，按current_step升序排序
        List<SysApprovalChainConfig> allList =
                sysApprovalChainConfigMapper.selectList(
                        new LambdaQueryWrapper<SysApprovalChainConfig>()
                                .eq(SysApprovalChainConfig::getSubjectId, AppContext.getSubjectId())
                                .eq(SysApprovalChainConfig::getProjectNo, AppContext.getProjectNo())
                                .eq(
                                        SysApprovalChainConfig::getModuleId,
                                        deletedConfig.getModuleId())
                                .eq(
                                        SysApprovalChainConfig::getApprovalChainTypeId,
                                        deletedConfig.getApprovalChainTypeId())
                                .orderByAsc(SysApprovalChainConfig::getCurrentStep));

        if (!allList.isEmpty()) {
            // 重新编号：从1开始，依次递增
            for (int i = 0; i < allList.size(); i++) {
                SysApprovalChainConfig item = allList.get(i);
                int newStep = i + 1;
                int newNextStep = i + 2;
                item.setCurrentStep(newStep);
                item.setNextStep(newNextStep);
                item.setUpStep(i);
                sysApprovalChainConfigMapper.updateById(item);
            }
        }
    }
}
