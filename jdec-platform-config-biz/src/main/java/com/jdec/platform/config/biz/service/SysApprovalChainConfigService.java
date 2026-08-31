package com.jdec.platform.config.biz.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jdec.platform.config.api.SysApprovalChainConfigApi;
import com.jdec.platform.config.api.dto.request.ApprovalChainConfigPageReq;
import com.jdec.platform.config.api.dto.request.ApprovalChainConfigSaveReq;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.config.biz.audit.service.SysApprovalChainLogService;
import com.jdec.platform.config.biz.check.CheckConstant;
import com.jdec.platform.config.biz.check.ReferenceCheckResult;
import com.jdec.platform.config.biz.check.ReferenceChecker;
import com.jdec.platform.config.biz.check.ReferenceContext;
import com.jdec.platform.config.biz.entity.*;
import com.jdec.platform.config.biz.mapper.*;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.ApiCodeEnum;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.PageResult;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/** 审批链配置 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_ENGINE)
public class SysApprovalChainConfigService implements SysApprovalChainConfigApi, ReferenceChecker {

    private final SysApprovalChainConfigMapper sysApprovalChainConfigMapper;
    private final SysApprovalChainTypeMapper sysApprovalChainTypeMapper;
    private final SysModuleMapper sysModuleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserMapper sysUserMapper;
    private final SysStatusMapper sysStatusMapper;
    private final SysConfigItemMapper sysConfigItemMapper;
    private final SysApprovalChainConfigButtonMapper sysApprovalChainConfigButtonMapper;
    private final SysButtonMapper sysButtonMapper;
    private final SysWechatTemplateMapper sysWechatTemplateMapper;
    private final SysApprovalChainLogService sysApprovalChainLogService;

    @Override
    public List<ApprovalChainCascadeResp> getCascadeOptions() {
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

        Set<Long> moduleIds =
                configs.stream()
                        .map(SysApprovalChainConfig::getModuleId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        if (moduleIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<SysModule> modules =
                sysModuleMapper.selectList(
                        new LambdaQueryWrapper<SysModule>()
                                .in(SysModule::getId, moduleIds)
                                .eq(SysModule::getSubjectId, AppContext.getSubjectId())
                                .eq(SysModule::getProjectNo, AppContext.getProjectNo())
                                .orderByAsc(SysModule::getSortOrder));

        List<SysApprovalChainType> chainTypes =
                sysApprovalChainTypeMapper.selectList(
                        new LambdaQueryWrapper<SysApprovalChainType>()
                                .eq(SysApprovalChainType::getSubjectId, AppContext.getSubjectId())
                                .eq(SysApprovalChainType::getProjectNo, AppContext.getProjectNo())
                                .eq(SysApprovalChainType::getEnabled, 1)
                                .in(SysApprovalChainType::getModuleId, moduleIds));

        Map<Long, List<SysApprovalChainType>> chainTypeMap =
                chainTypes.stream()
                        .collect(Collectors.groupingBy(SysApprovalChainType::getModuleId));

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

        Set<Long> moduleIds = new HashSet<>();
        Set<Long> childModuleIds = new HashSet<>();
        Set<Long> chainTypeIds = new HashSet<>();
        Set<Long> roleIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        Set<String> approvalRuleValues = new HashSet<>();
        Set<String> rejectRuleValues = new HashSet<>();
        Set<Long> buttonConfigIds = new HashSet<>();

        for (SysApprovalChainConfig config : configPage.getRecords()) {
            if (config.getModuleId() != null) moduleIds.add(config.getModuleId());
            if (config.getApprovalChainTypeId() != null)
                chainTypeIds.add(config.getApprovalChainTypeId());
            if (config.getApproveRoleId() != null) roleIds.add(config.getApproveRoleId());
            if (config.getApproverId() != null) userIds.add(config.getApproverId());
            if (config.getDelegateApproverId() != null) userIds.add(config.getDelegateApproverId());
            if (config.getApprovalRule() != null) approvalRuleValues.add(config.getApprovalRule());
            if (config.getRejectRule() != null) rejectRuleValues.add(config.getRejectRule());

            if (config.getChildModuleIds() != null && !config.getChildModuleIds().isEmpty()) {
                for (String id : config.getChildModuleIds().split(",")) {
                    try {
                        childModuleIds.add(Long.parseLong(id.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            if (config.getButtonList() != null && !config.getButtonList().isEmpty()) {
                for (String id : config.getButtonList().split(",")) {
                    try {
                        buttonConfigIds.add(Long.parseLong(id.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        Map<Long, String> moduleMap = batchQueryModules(moduleIds);
        Map<Long, String> childModuleMap = batchQueryModules(childModuleIds);
        Map<Long, String> chainTypeMap = batchQueryChainTypes(chainTypeIds);
        Map<Long, String> roleMap = batchQueryRoles(roleIds);
        Map<Long, String> userMap = batchQueryUsers(userIds);
        Map<String, String> approvalRuleMap = batchQueryApprovalRules(approvalRuleValues);
        Map<String, String> rejectRuleMap = batchQueryRejectRules(rejectRuleValues);
        Map<Long, SysStatus> allStatusMap = loadAllStatuses();
        Map<Long, SysApprovalChainConfigButton> buttonConfigMap =
                batchQueryButtonConfigs(buttonConfigIds);

        Set<Long> buttonIds = new HashSet<>();
        Set<Long> msgTemplateIds = new HashSet<>();
        for (SysApprovalChainConfigButton buttonConfig : buttonConfigMap.values()) {
            if (buttonConfig.getButtonId() != null) buttonIds.add(buttonConfig.getButtonId());
            if (buttonConfig.getMsgTemplateIds() != null
                    && !buttonConfig.getMsgTemplateIds().isEmpty()) {
                for (String id : buttonConfig.getMsgTemplateIds().split(",")) {
                    try {
                        msgTemplateIds.add(Long.parseLong(id.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        Map<Long, SysButton> buttonMap = batchQueryButtons(buttonIds);
        Map<Long, SysWechatTemplate> msgTemplateMap = batchQueryMsgTemplates(msgTemplateIds);

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
                                    resp.setRejectRule(rejectRuleMap.get(config.getRejectRule()));
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

                                    if (config.getChildModuleIds() != null
                                            && !config.getChildModuleIds().isEmpty()) {
                                        List<String> names = new ArrayList<>();
                                        for (String id : config.getChildModuleIds().split(",")) {
                                            try {
                                                String name =
                                                        childModuleMap.get(
                                                                Long.parseLong(id.trim()));
                                                if (name != null) names.add(name);
                                            } catch (NumberFormatException ignored) {
                                            }
                                        }
                                        resp.setChildModuleNames(String.join(",", names));
                                    }

                                    resp.setCurrentStatusId(config.getCurrentStatusId());
                                    resp.setCurrentStatusChain(
                                            buildStatusChain(
                                                    config.getCurrentStatusId(),
                                                    allStatusMap,
                                                    ApprovalChainConfigPageResp.StatusNode.class));
                                    resp.setNextStatusId(config.getNextStatusId());
                                    resp.setNextStatusChain(
                                            buildStatusChain(
                                                    config.getNextStatusId(),
                                                    allStatusMap,
                                                    ApprovalChainConfigPageResp.StatusNode.class));
                                    resp.setAutoApproved(config.getAutoApproved());
                                    resp.setAutoApprovedTime(config.getAutoApprovedTime());
                                    resp.setDeadlineTime(config.getDeadlineTime());
                                    resp.setButtonList(config.getButtonList());

                                    // up_add / down_add 逻辑
                                    resp.setUpAdd(1);
                                    resp.setDownAdd(1);

                                    SysStatus nextStatus =
                                            sysStatusMapper.selectById(config.getNextStatusId());
                                    if (nextStatus != null && nextStatus.getStatusValue() == 99) {
                                        resp.setDownAdd(0);
                                    }

                                    if (config.getButtonList() != null
                                            && !config.getButtonList().isEmpty()) {
                                        List<ApprovalChainConfigPageResp.ButtonConfig>
                                                buttonDetails = new ArrayList<>();
                                        for (String buttonConfigIdStr :
                                                config.getButtonList().split(",")) {
                                            try {
                                                SysApprovalChainConfigButton buttonConfig =
                                                        buttonConfigMap.get(
                                                                Long.parseLong(
                                                                        buttonConfigIdStr.trim()));
                                                if (buttonConfig == null) continue;

                                                ApprovalChainConfigPageResp.ButtonConfig
                                                        buttonDetail =
                                                                new ApprovalChainConfigPageResp
                                                                        .ButtonConfig();
                                                buttonDetail.setId(buttonConfig.getId());
                                                buttonDetail.setButtonTypeId(
                                                        buttonConfig.getButtonTypeId());
                                                buttonDetail.setButtonId(
                                                        buttonConfig.getButtonId());
                                                buttonDetail.setIcon(buttonConfig.getIcon());
                                                buttonDetail.setLogName(buttonConfig.getLogName());
                                                buttonDetail.setAutoNextTaskFlag(
                                                        buttonConfig.getAutoNextTaskFlag());
                                                buttonDetail.setMsgTemplateIds(
                                                        buttonConfig.getMsgTemplateIds());

                                                // 设置按钮详细信息
                                                if (buttonConfig.getButtonId() != null) {
                                                    SysButton button =
                                                            buttonMap.get(
                                                                    buttonConfig.getButtonId());
                                                    if (button != null) {
                                                        buttonDetail.setButtonTitle(
                                                                button.getTitle());
                                                        buttonDetail.setButtonAlias(
                                                                button.getAlias());
                                                        buttonDetail.setButtonDescription(
                                                                button.getDescription());
                                                        buttonDetail.setDefaultBgColor(
                                                                button.getDefaultBgColor());
                                                        buttonDetail.setDefaultFontColor(
                                                                button.getDefaultFontColor());
                                                        buttonDetail.setLevitateBgColor(
                                                                button.getLevitateBgColor());
                                                        buttonDetail.setLevitateFontColor(
                                                                button.getLevitateFontColor());
                                                        buttonDetail.setSelectedBgColor(
                                                                button.getSelectedBgColor());
                                                        buttonDetail.setSelectedFontColor(
                                                                button.getSelectedFontColor());
                                                        buttonDetail.setButtonSortOrder(
                                                                button.getSortOrder());
                                                        buttonDetail.setButtonShowed(
                                                                button.getShowed());
                                                        buttonDetail.setButtonEnabled(
                                                                button.getEnabled());
                                                    }
                                                }

                                                if (buttonConfig.getMsgTemplateIds() != null
                                                        && !buttonConfig
                                                                .getMsgTemplateIds()
                                                                .isEmpty()) {
                                                    List<
                                                                    ApprovalChainConfigPageResp
                                                                            .MessageTemplateDetail>
                                                            msgTemplateDetails = new ArrayList<>();
                                                    for (String msgTemplateIdStr :
                                                            buttonConfig
                                                                    .getMsgTemplateIds()
                                                                    .split(",")) {
                                                        try {
                                                            SysWechatTemplate msgTemplate =
                                                                    msgTemplateMap.get(
                                                                            Long.parseLong(
                                                                                    msgTemplateIdStr
                                                                                            .trim()));
                                                            if (msgTemplate == null) continue;
                                                            ApprovalChainConfigPageResp
                                                                            .MessageTemplateDetail
                                                                    msgDetail =
                                                                            new ApprovalChainConfigPageResp
                                                                                    .MessageTemplateDetail();
                                                            msgDetail.setId(msgTemplate.getId());
                                                            msgDetail.setTemplateTitle(
                                                                    msgTemplate.getTemplateTitle());
                                                            msgDetail.setTemplateContent(
                                                                    msgTemplate
                                                                            .getTemplateContent());
                                                            msgDetail.setModuleId(
                                                                    msgTemplate.getModuleId());
                                                            msgDetail.setModuleName(
                                                                    msgTemplate.getModuleName());
                                                            msgTemplateDetails.add(msgDetail);
                                                        } catch (NumberFormatException ignored) {
                                                        }
                                                    }
                                                    buttonDetail.setMessageTemplates(
                                                            msgTemplateDetails);
                                                }

                                                buttonDetails.add(buttonDetail);
                                            } catch (NumberFormatException ignored) {
                                            }
                                        }
                                        resp.setButtonConfigs(buttonDetails);
                                    }

                                    resp.setCollaborated(config.getCollaborated());
                                    resp.setRepeated(config.getRepeated());
                                    resp.setDefaultFlag(config.getDefaultFlag());
                                    return resp;
                                })
                        .collect(Collectors.toList());

        return PageResult.of(
                request.getPageNum(), request.getPageSize(), configPage.getTotal(), respList);
    }

    /** 加载全量 sys_status，返回 id -> SysStatus 的 Map */
    private Map<Long, SysStatus> loadAllStatuses() {
        return sysStatusMapper.selectList(new LambdaQueryWrapper<SysStatus>()).stream()
                .collect(Collectors.toMap(SysStatus::getId, s -> s));
    }

    /**
     * 根据 statusId 沿 pid 向上追溯，构建从根到当前节点的状态链路。
     *
     * @param statusId 起始状态ID
     * @param allStatusMap 全量状态 map
     * @param nodeClass 目标节点类型
     */
    private <T> List<T> buildStatusChain(
            Long statusId, Map<Long, SysStatus> allStatusMap, Class<T> nodeClass) {
        if (statusId == null || statusId == 0) {
            return Collections.emptyList();
        }
        LinkedList<SysStatus> chain = new LinkedList<>();
        Set<Long> visited = new HashSet<>();
        Long current = statusId;
        while (current != null && current != 0 && !visited.contains(current)) {
            SysStatus status = allStatusMap.get(current);
            if (status == null) break;
            chain.addFirst(status);
            visited.add(current);
            current = (status.getPid() != null && status.getPid() != 0) ? status.getPid() : null;
        }
        return chain.stream()
                .map(s -> convertStatusNode(s, nodeClass))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private <T> T convertStatusNode(SysStatus s, Class<T> nodeClass) {
        if (nodeClass == ApprovalChainConfigPageResp.StatusNode.class) {
            ApprovalChainConfigPageResp.StatusNode node =
                    new ApprovalChainConfigPageResp.StatusNode();
            node.setId(s.getId());
            node.setPid(s.getPid());
            node.setTitle(s.getTitle());
            node.setStatusValue(s.getStatusValue());
            node.setStatusBackground(s.getStatusBackground());
            node.setStatusFontColor(s.getStatusFontColor());
            return (T) node;
        } else {
            ApprovalChainConfigDetailResp.StatusNode node =
                    new ApprovalChainConfigDetailResp.StatusNode();
            node.setId(s.getId());
            node.setPid(s.getPid());
            node.setTitle(s.getTitle());
            node.setStatusValue(s.getStatusValue());
            node.setStatusBackground(s.getStatusBackground());
            node.setStatusFontColor(s.getStatusFontColor());
            return (T) node;
        }
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
                .selectList(
                        new LambdaQueryWrapper<SysUser>()
                                .in(SysUser::getUserId, ids)
                                .eq(SysUser::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(SysUser::getUserId, SysUser::getUserName));
    }

    private Map<Long, SysUser> batchQueryUserMap(Set<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        return sysUserMapper
                .selectList(
                        new LambdaQueryWrapper<SysUser>()
                                .in(SysUser::getUserId, ids)
                                .eq(SysUser::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(SysUser::getUserId, u -> u));
    }

    private Map<String, String> batchQueryApprovalRules(Set<String> values) {
        if (values.isEmpty()) return Collections.emptyMap();
        return sysConfigItemMapper
                .selectList(
                        new LambdaQueryWrapper<SysConfigItem>()
                                .eq(SysConfigItem::getCategoryAlias, "approvalRule")
                                .eq(SysConfigItem::getSubjectId, AppContext.getSubjectId())
                                .eq(SysConfigItem::getProjectNo, AppContext.getProjectNo())
                                .in(SysConfigItem::getValue, values))
                .stream()
                .collect(Collectors.toMap(SysConfigItem::getValue, SysConfigItem::getLabel));
    }

    private Map<String, String> batchQueryRejectRules(Set<String> values) {
        if (values.isEmpty()) return Collections.emptyMap();
        return sysConfigItemMapper
                .selectList(
                        new LambdaQueryWrapper<SysConfigItem>()
                                .eq(SysConfigItem::getCategoryAlias, "rejectRule")
                                .eq(SysConfigItem::getSubjectId, AppContext.getSubjectId())
                                .eq(SysConfigItem::getProjectNo, AppContext.getProjectNo())
                                .in(SysConfigItem::getValue, values))
                .stream()
                .collect(Collectors.toMap(SysConfigItem::getValue, SysConfigItem::getLabel));
    }

    private Map<Long, SysApprovalChainConfigButton> batchQueryButtonConfigs(Set<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        return sysApprovalChainConfigButtonMapper
                .selectList(
                        new LambdaQueryWrapper<SysApprovalChainConfigButton>()
                                .in(SysApprovalChainConfigButton::getId, ids))
                .stream()
                .collect(Collectors.toMap(SysApprovalChainConfigButton::getId, btn -> btn));
    }

    private Map<Long, SysButton> batchQueryButtons(Set<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        return sysButtonMapper
                .selectList(new LambdaQueryWrapper<SysButton>().in(SysButton::getId, ids))
                .stream()
                .collect(Collectors.toMap(SysButton::getId, btn -> btn));
    }

    private Map<Long, SysWechatTemplate> batchQueryMsgTemplates(Set<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        return sysWechatTemplateMapper
                .selectList(
                        new LambdaQueryWrapper<SysWechatTemplate>()
                                .in(SysWechatTemplate::getId, ids))
                .stream()
                .collect(Collectors.toMap(SysWechatTemplate::getId, tpl -> tpl));
    }

    @Override
    public List<ApprovalChainConfigDetailResp> getDetail(Long id) {
        SysApprovalChainConfig config = sysApprovalChainConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ApiCodeEnum.WARNING.getCode(), "配置不存在");
        }

        // 查询同模块同类型的所有审批链配置（按步骤升序）
        List<SysApprovalChainConfig> chainConfigs =
                sysApprovalChainConfigMapper.selectList(
                        new LambdaQueryWrapper<SysApprovalChainConfig>()
                                .eq(SysApprovalChainConfig::getSubjectId, AppContext.getSubjectId())
                                .eq(SysApprovalChainConfig::getProjectNo, AppContext.getProjectNo())
                                .eq(SysApprovalChainConfig::getModuleId, config.getModuleId())
                                .eq(
                                        SysApprovalChainConfig::getApprovalChainTypeId,
                                        config.getApprovalChainTypeId())
                                .orderByAsc(SysApprovalChainConfig::getCurrentStep));

        if (chainConfigs.isEmpty()) {
            throw new BusinessException(ApiCodeEnum.WARNING.getCode(), "未找到审批链配置");
        }

        // 收集所有配置的ID，用于批量查询按钮配置
        Set<Long> configIds =
                chainConfigs.stream()
                        .map(SysApprovalChainConfig::getId)
                        .collect(Collectors.toSet());

        // 批量查询所有配置的按钮配置
        Map<Long, List<SysApprovalChainConfigButton>> buttonConfigMap = new HashMap<>();
        if (!configIds.isEmpty()) {
            List<SysApprovalChainConfigButton> allButtons =
                    sysApprovalChainConfigButtonMapper.selectList(
                            new LambdaQueryWrapper<SysApprovalChainConfigButton>()
                                    .in(SysApprovalChainConfigButton::getPid, configIds));
            buttonConfigMap =
                    allButtons.stream()
                            .collect(Collectors.groupingBy(SysApprovalChainConfigButton::getPid));
        }

        // 收集所有按钮ID和消息模板ID
        Set<Long> buttonIds = new HashSet<>();
        Set<Long> msgTemplateIds = new HashSet<>();
        for (List<SysApprovalChainConfigButton> buttons : buttonConfigMap.values()) {
            for (SysApprovalChainConfigButton buttonConfig : buttons) {
                if (buttonConfig.getButtonId() != null) {
                    buttonIds.add(buttonConfig.getButtonId());
                }
                if (buttonConfig.getMsgTemplateIds() != null
                        && !buttonConfig.getMsgTemplateIds().isEmpty()) {
                    for (String templateId : buttonConfig.getMsgTemplateIds().split(",")) {
                        try {
                            msgTemplateIds.add(Long.parseLong(templateId.trim()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }

        // 批量查询按钮和消息模板
        Map<Long, SysButton> buttonMap = batchQueryButtons(buttonIds);
        Map<Long, SysWechatTemplate> msgTemplateMap = batchQueryMsgTemplates(msgTemplateIds);
        Map<Long, SysStatus> allStatusMap = loadAllStatuses();

        // 批量查询模块名称和审批链分类名称
        Set<Long> moduleIds = new HashSet<>();
        Set<Long> chainTypeIds = new HashSet<>();
        for (SysApprovalChainConfig chainConfig : chainConfigs) {
            if (chainConfig.getModuleId() != null) {
                moduleIds.add(chainConfig.getModuleId());
            }
            if (chainConfig.getApprovalChainTypeId() != null) {
                chainTypeIds.add(chainConfig.getApprovalChainTypeId());
            }
        }
        Map<Long, String> moduleMap = batchQueryModules(moduleIds);
        Map<Long, String> chainTypeMap = batchQueryChainTypes(chainTypeIds);

        // 批量查询审批人信息
        Set<Long> approverIds = new HashSet<>();
        for (SysApprovalChainConfig chainConfig : chainConfigs) {
            if (chainConfig.getApproverId() != null) {
                approverIds.add(chainConfig.getApproverId());
            }
        }
        Map<Long, SysUser> approverMap = batchQueryUserMap(approverIds);

        // 构建响应列表
        Map<Long, List<SysApprovalChainConfigButton>> finalButtonConfigMap = buttonConfigMap;
        Map<Long, SysButton> finalButtonMap = buttonMap;
        Map<Long, SysWechatTemplate> finalMsgTemplateMap = msgTemplateMap;
        Map<Long, String> finalModuleMap = moduleMap;
        Map<Long, String> finalChainTypeMap = chainTypeMap;
        Map<Long, SysUser> finalApproverMap = approverMap;

        return chainConfigs.stream()
                .map(
                        chainConfig -> {
                            ApprovalChainConfigDetailResp resp =
                                    new ApprovalChainConfigDetailResp();
                            resp.setId(chainConfig.getId());
                            resp.setModuleId(chainConfig.getModuleId());
                            resp.setModuleName(finalModuleMap.get(chainConfig.getModuleId()));
                            resp.setUpStep(chainConfig.getUpStep());
                            resp.setCurrentStep(chainConfig.getCurrentStep());
                            resp.setNextStep(chainConfig.getNextStep());
                            resp.setApprovalChainTypeId(chainConfig.getApprovalChainTypeId());
                            resp.setApprovalChainTypeName(
                                    finalChainTypeMap.get(chainConfig.getApprovalChainTypeId()));

                            resp.setUpAdd(1);

                            int downAdd = 1;
                            if (chainConfig.getNextStatusId() != null) {
                                SysStatus nextStatus =
                                        sysStatusMapper.selectOne(
                                                Wrappers.<SysStatus>lambdaQuery()
                                                        .eq(
                                                                SysStatus::getId,
                                                                chainConfig.getNextStatusId())
                                                        .select(SysStatus::getStatusValue));
                                if (nextStatus != null && nextStatus.getStatusValue() == 99) {
                                    downAdd = 0;
                                }
                            }

                            resp.setDownAdd(downAdd);

                            resp.setApprovalRule(chainConfig.getApprovalRule());
                            resp.setRejectRule(chainConfig.getRejectRule());
                            resp.setRoleApprovalPercent(chainConfig.getRoleApprovalPercent());
                            resp.setApproveRoleId(chainConfig.getApproveRoleId());
                            resp.setApproverId(chainConfig.getApproverId());
                            if (chainConfig.getApproverId() != null) {
                                SysUser approver =
                                        finalApproverMap.get(chainConfig.getApproverId());
                                if (approver != null) {
                                    resp.setApproverName(approver.getUserName());
                                    resp.setApproverWorkNumber(approver.getWorkNumber());
                                }
                            }
                            resp.setDelegateApproverId(chainConfig.getDelegateApproverId());
                            resp.setSkipped(chainConfig.getSkipped());
                            resp.setShowed(chainConfig.getShowed());
                            resp.setChildModuleIds(chainConfig.getChildModuleIds());
                            resp.setCurrentStatusId(chainConfig.getCurrentStatusId());
                            resp.setCurrentStatusChain(
                                    buildStatusChain(
                                            chainConfig.getCurrentStatusId(),
                                            allStatusMap,
                                            ApprovalChainConfigDetailResp.StatusNode.class));
                            resp.setNextStatusId(chainConfig.getNextStatusId());
                            resp.setNextStatusChain(
                                    buildStatusChain(
                                            chainConfig.getNextStatusId(),
                                            allStatusMap,
                                            ApprovalChainConfigDetailResp.StatusNode.class));
                            resp.setAutoApproved(chainConfig.getAutoApproved());
                            resp.setAutoApprovedTime(chainConfig.getAutoApprovedTime());
                            resp.setDeadlineTime(chainConfig.getDeadlineTime());
                            resp.setButtonList(chainConfig.getButtonList());
                            resp.setCollaborated(chainConfig.getCollaborated());
                            resp.setRepeated(chainConfig.getRepeated());
                            resp.setMsgTemplateIds(chainConfig.getMsgTemplateIds());
                            resp.setDefaultFlag(chainConfig.getDefaultFlag());

                            // 设置按钮配置（包含按钮详情和消息模板）
                            List<SysApprovalChainConfigButton> buttons =
                                    finalButtonConfigMap.getOrDefault(
                                            chainConfig.getId(), Collections.emptyList());
                            if (!buttons.isEmpty()) {
                                List<ApprovalChainConfigDetailResp.ButtonConfigDetail>
                                        buttonDetails =
                                                buttons.stream()
                                                        .map(
                                                                btn -> {
                                                                    ApprovalChainConfigDetailResp
                                                                                    .ButtonConfigDetail
                                                                            detail =
                                                                                    new ApprovalChainConfigDetailResp
                                                                                            .ButtonConfigDetail();
                                                                    detail.setId(btn.getId());
                                                                    detail.setButtonTypeId(
                                                                            btn.getButtonTypeId());
                                                                    detail.setButtonId(
                                                                            btn.getButtonId());
                                                                    detail.setIcon(btn.getIcon());
                                                                    detail.setLogName(
                                                                            btn.getLogName());
                                                                    detail.setAutoNextTaskFlag(
                                                                            btn
                                                                                    .getAutoNextTaskFlag());
                                                                    detail.setMsgTemplateIds(
                                                                            btn
                                                                                    .getMsgTemplateIds());

                                                                    // 设置按钮详细信息
                                                                    if (btn.getButtonId() != null) {
                                                                        SysButton button =
                                                                                finalButtonMap.get(
                                                                                        btn
                                                                                                .getButtonId());
                                                                        if (button != null) {
                                                                            detail.setButtonTitle(
                                                                                    button
                                                                                            .getTitle());
                                                                            detail.setButtonAlias(
                                                                                    button
                                                                                            .getAlias());
                                                                            detail
                                                                                    .setButtonDescription(
                                                                                            button
                                                                                                    .getDescription());
                                                                            detail
                                                                                    .setDefaultBgColor(
                                                                                            button
                                                                                                    .getDefaultBgColor());
                                                                            detail
                                                                                    .setDefaultFontColor(
                                                                                            button
                                                                                                    .getDefaultFontColor());
                                                                            detail
                                                                                    .setLevitateBgColor(
                                                                                            button
                                                                                                    .getLevitateBgColor());
                                                                            detail
                                                                                    .setLevitateFontColor(
                                                                                            button
                                                                                                    .getLevitateFontColor());
                                                                            detail
                                                                                    .setSelectedBgColor(
                                                                                            button
                                                                                                    .getSelectedBgColor());
                                                                            detail
                                                                                    .setSelectedFontColor(
                                                                                            button
                                                                                                    .getSelectedFontColor());
                                                                            detail
                                                                                    .setButtonSortOrder(
                                                                                            button
                                                                                                    .getSortOrder());
                                                                            detail.setButtonShowed(
                                                                                    button
                                                                                            .getShowed());
                                                                            detail.setButtonEnabled(
                                                                                    button
                                                                                            .getEnabled());
                                                                        }
                                                                    }

                                                                    // 设置消息模板列表
                                                                    if (btn.getMsgTemplateIds()
                                                                                    != null
                                                                            && !btn.getMsgTemplateIds()
                                                                                    .isEmpty()) {
                                                                        List<
                                                                                        ApprovalChainConfigDetailResp
                                                                                                .MessageTemplateDetail>
                                                                                msgTemplateDetails =
                                                                                        new ArrayList<>();
                                                                        for (String
                                                                                msgTemplateIdStr :
                                                                                        btn.getMsgTemplateIds()
                                                                                                .split(
                                                                                                        ",")) {
                                                                            try {
                                                                                SysWechatTemplate
                                                                                        msgTemplate =
                                                                                                finalMsgTemplateMap
                                                                                                        .get(
                                                                                                                Long
                                                                                                                        .parseLong(
                                                                                                                                msgTemplateIdStr
                                                                                                                                        .trim()));
                                                                                if (msgTemplate
                                                                                        == null)
                                                                                    continue;

                                                                                ApprovalChainConfigDetailResp
                                                                                                .MessageTemplateDetail
                                                                                        msgDetail =
                                                                                                new ApprovalChainConfigDetailResp
                                                                                                        .MessageTemplateDetail();
                                                                                msgDetail.setId(
                                                                                        msgTemplate
                                                                                                .getId());
                                                                                msgDetail
                                                                                        .setTemplateTitle(
                                                                                                msgTemplate
                                                                                                        .getTemplateTitle());
                                                                                msgDetail
                                                                                        .setTemplateContent(
                                                                                                msgTemplate
                                                                                                        .getTemplateContent());
                                                                                msgDetail
                                                                                        .setModuleId(
                                                                                                msgTemplate
                                                                                                        .getModuleId());
                                                                                msgDetail
                                                                                        .setModuleName(
                                                                                                msgTemplate
                                                                                                        .getModuleName());
                                                                                msgTemplateDetails
                                                                                        .add(
                                                                                                msgDetail);
                                                                            } catch (
                                                                                    NumberFormatException
                                                                                            ignored) {
                                                                            }
                                                                        }
                                                                        detail.setMessageTemplates(
                                                                                msgTemplateDetails);
                                                                    }

                                                                    return detail;
                                                                })
                                                        .collect(Collectors.toList());
                                resp.setButtonConfigs(buttonDetails);
                            }

                            return resp;
                        })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalChainConfigSaveResp save(ApprovalChainConfigSaveReq request) {
        Long id = request.getTargetId();
        boolean isCreate = (id == null || id == 0);
        SysApprovalChainConfig oldConfig = null;

        if (request.getUpStep() == null) request.setUpStep(0);
        if (request.getDefaultFlag() == null) request.setDefaultFlag(0);
        if (request.getAutoApproved() == null) request.setAutoApproved(0);
        if (request.getSkipped() == null) request.setSkipped(0);
        if (request.getShowed() == null) request.setShowed(0);

        request.setCurrentStep(request.getUpStep() + 1);
        if (request.getNextStep() == null) {
            request.setNextStep(request.getCurrentStep() + 1);
        }

        if (request.getCurrentStatusId().equals(request.getNextStatusId())) {
            throw new BusinessException(ApiCodeEnum.WARNING.getCode(), "当前状态不能与下一状态相同，请重新选择");
        }

        SysApprovalChainConfig config = BeanUtil.toBean(request, SysApprovalChainConfig.class);

        // 处理自动审批字段的互斥逻辑
        if (config.getAutoApproved() != null) {
            if (config.getAutoApproved() == 1) {
                // 当 autoApproved 为 1 时，清空 deadlineTime
                config.setDeadlineTime(null);
            } else if (config.getAutoApproved() == 0) {
                // 当 autoApproved 为 0 时，清空 autoApprovedTime
                config.setAutoApprovedTime(null);
            }
        }

        SysApprovalChainConfig upConfig;
        SysApprovalChainConfig nextConfig;

        if (id == null || id == 0) {
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
                                            SysApprovalChainConfig::getCurrentStatusId,
                                            config.getCurrentStatusId())
                                    .eq(
                                            SysApprovalChainConfig::getNextStatusId,
                                            config.getNextStatusId()));
            if (exist != null) {
                throw new BusinessException(ApiCodeEnum.WARNING.getCode(), "当前步骤已存在，请重新选择");
            }

            config.setId(null);
            config.setButtonList(null);
            config.setSubjectId(AppContext.getSubjectId());
            config.setProjectNo(AppContext.getProjectNo());
            sysApprovalChainConfigMapper.insert(config);
            id = config.getId();

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
            oldConfig = sysApprovalChainConfigMapper.selectById(id);
            if (oldConfig == null) {
                throw new BusinessException(ApiCodeEnum.WARNING.getCode(), "配置不存在");
            }

            upConfig =
                    sysApprovalChainConfigMapper.selectOne(
                            new LambdaQueryWrapper<SysApprovalChainConfig>()
                                    .eq(
                                            SysApprovalChainConfig::getSubjectId,
                                            AppContext.getSubjectId())
                                    .eq(
                                            SysApprovalChainConfig::getProjectNo,
                                            AppContext.getProjectNo())
                                    .eq(
                                            SysApprovalChainConfig::getModuleId,
                                            oldConfig.getModuleId())
                                    .eq(
                                            SysApprovalChainConfig::getApprovalChainTypeId,
                                            oldConfig.getApprovalChainTypeId())
                                    .eq(
                                            SysApprovalChainConfig::getCurrentStep,
                                            oldConfig.getUpStep()));

            nextConfig =
                    sysApprovalChainConfigMapper.selectOne(
                            new LambdaQueryWrapper<SysApprovalChainConfig>()
                                    .eq(
                                            SysApprovalChainConfig::getSubjectId,
                                            AppContext.getSubjectId())
                                    .eq(
                                            SysApprovalChainConfig::getProjectNo,
                                            AppContext.getProjectNo())
                                    .eq(
                                            SysApprovalChainConfig::getModuleId,
                                            oldConfig.getModuleId())
                                    .eq(
                                            SysApprovalChainConfig::getApprovalChainTypeId,
                                            oldConfig.getApprovalChainTypeId())
                                    .eq(
                                            SysApprovalChainConfig::getCurrentStep,
                                            oldConfig.getNextStep()));

            config.setId(id);
            config.setButtonList(null);
            sysApprovalChainConfigMapper.updateById(config);
        }

        // 处理按钮列表：先删除该配置的所有旧按钮，再根据请求重新插入
        Long finalId = id;
        // 修改时先捕获旧按钮配置，用于审计日志的差异对比
        List<SysApprovalChainConfigButton> oldButtons = null;
        if (!isCreate) {
            oldButtons =
                    sysApprovalChainConfigButtonMapper.selectList(
                            new LambdaQueryWrapper<SysApprovalChainConfigButton>()
                                    .eq(SysApprovalChainConfigButton::getPid, finalId));
        }
        sysApprovalChainConfigButtonMapper.delete(
                new LambdaQueryWrapper<SysApprovalChainConfigButton>()
                        .eq(SysApprovalChainConfigButton::getPid, finalId));
        List<SysApprovalChainConfigButton> newButtons = null;
        if (request.getButtonConfigs() != null && !request.getButtonConfigs().isEmpty()) {
            newButtons =
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
                                        button.setAutoNextTaskFlag(btn.getAutoNextTaskFlag());
                                        button.setMsgTemplateIds(btn.getMsgTemplateIds());
                                        button.setSubjectId(AppContext.getSubjectId());
                                        button.setProjectNo(AppContext.getProjectNo());
                                        return button;
                                    })
                            .collect(Collectors.toList());

            sysApprovalChainConfigButtonMapper.insert(newButtons);

            String buttonListIds =
                    newButtons.stream()
                            .map(btn -> String.valueOf(btn.getId()))
                            .collect(Collectors.joining(","));

            SysApprovalChainConfig updateConfig = new SysApprovalChainConfig();
            updateConfig.setId(id);
            updateConfig.setButtonList(buttonListIds);
            sysApprovalChainConfigMapper.updateById(updateConfig);
        }

        rearrangeStepsAfterSave(config, id, upConfig, nextConfig);

        // 记录审计日志并维护快照
        if (isCreate) {
            sysApprovalChainLogService.logCreate(request, id, newButtons);
        } else {
            sysApprovalChainLogService.logUpdate(request, id, oldConfig, oldButtons, newButtons);
        }

        return new ApprovalChainConfigSaveResp(id, 1);
    }

    private void rearrangeStepsAfterSave(
            SysApprovalChainConfig currentConfig,
            Long currentId,
            SysApprovalChainConfig upConfig,
            SysApprovalChainConfig nextConfig) {

        Long oldModuleId = 0L;
        Long oldApprovalChainTypeId = 0L;

        if (upConfig != null) {
            oldModuleId = upConfig.getModuleId();
            oldApprovalChainTypeId = upConfig.getApprovalChainTypeId();

            if (upConfig.getCurrentStatusId().equals(currentConfig.getCurrentStatusId())) {
                throw new BusinessException(
                        ApiCodeEnum.WARNING.getCode(), "当前步骤当前状态与上一步骤状态冲突，请重新选择");
            }
            if (upConfig.getCurrentStatusId().equals(currentConfig.getNextStatusId())) {
                throw new BusinessException(
                        ApiCodeEnum.WARNING.getCode(), "当前步骤之后状态与上一步骤当前状态冲突，请重新选择");
            }

            upConfig.setNextStatusId(currentConfig.getCurrentStatusId());
            sysApprovalChainConfigMapper.updateById(upConfig);
        }

        if (nextConfig != null) {
            oldModuleId = nextConfig.getModuleId();
            oldApprovalChainTypeId = nextConfig.getApprovalChainTypeId();

            if (nextConfig.getNextStatusId().equals(currentConfig.getCurrentStatusId())) {
                throw new BusinessException(
                        ApiCodeEnum.WARNING.getCode(), "当前步骤当前状态与下一步骤之后状态冲突，请重新选择");
            }
            if (nextConfig.getNextStatusId().equals(currentConfig.getNextStatusId())) {
                throw new BusinessException(
                        ApiCodeEnum.WARNING.getCode(), "当前步骤之后状态与下一步骤之后状态冲突，请重新选择");
            }

            nextConfig.setCurrentStatusId(currentConfig.getNextStatusId());
            sysApprovalChainConfigMapper.updateById(nextConfig);
        }

        if ((oldModuleId > 0 && oldApprovalChainTypeId > 0)
                && (!oldModuleId.equals(currentConfig.getModuleId())
                        || !oldApprovalChainTypeId.equals(
                                currentConfig.getApprovalChainTypeId()))) {
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

        List<SysApprovalChainConfig> laterSteps =
                sysApprovalChainConfigMapper.selectList(
                        new LambdaQueryWrapper<SysApprovalChainConfig>()
                                .ne(SysApprovalChainConfig::getId, currentId)
                                .eq(SysApprovalChainConfig::getSubjectId, AppContext.getSubjectId())
                                .eq(SysApprovalChainConfig::getProjectNo, AppContext.getProjectNo())
                                .eq(
                                        SysApprovalChainConfig::getModuleId,
                                        currentConfig.getModuleId())
                                .eq(
                                        SysApprovalChainConfig::getApprovalChainTypeId,
                                        currentConfig.getApprovalChainTypeId())
                                .gt(
                                        SysApprovalChainConfig::getCurrentStep,
                                        currentConfig.getUpStep())
                                .orderByAsc(SysApprovalChainConfig::getCurrentStep));

        if (!laterSteps.isEmpty()) {
            for (int i = 0; i < laterSteps.size(); i++) {
                SysApprovalChainConfig step = laterSteps.get(i);
                step.setCurrentStep(currentConfig.getCurrentStep() + 1 + i);
                step.setNextStep(currentConfig.getNextStep() + 1 + i);
                step.setUpStep(currentConfig.getUpStep() + 1 + i);
                sysApprovalChainConfigMapper.updateById(step);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysApprovalChainConfig deletedConfig = sysApprovalChainConfigMapper.selectById(id);
        if (deletedConfig == null) {
            throw new BusinessException(ApiCodeEnum.WARNING.getCode(), "配置不存在");
        }

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

        sysApprovalChainConfigMapper.deleteById(id);

        sysApprovalChainConfigButtonMapper.delete(
                new LambdaQueryWrapper<SysApprovalChainConfigButton>()
                        .eq(SysApprovalChainConfigButton::getPid, id));

        rearrangeStepsAfterDelete(deletedConfig, upStepConfig, nextStepConfig);

        // 记录删除审计日志并删除快照
        sysApprovalChainLogService.logDelete(deletedConfig);
    }

    /** 查询审批链配置详情（返回同模块同类型的所有配置） */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ModuleApprovalChainConfigResp getApprovalConfig(Long moduleId, Long approvalTypeId) {
        // 1. 查询模块默认启用的审批链类型
        long chainTypeId = 0L;
        if (Objects.nonNull(approvalTypeId) && approvalTypeId > 0) {
            chainTypeId = approvalTypeId;
        } else {
            LambdaQueryWrapper<SysApprovalChainType> typeQuery = Wrappers.lambdaQuery();
            typeQuery.eq(SysApprovalChainType::getModuleId, moduleId);
            typeQuery.eq(SysApprovalChainType::getDefaultFlag, true).last("LIMIT 1");
            SysApprovalChainType chainType = sysApprovalChainTypeMapper.selectOne(typeQuery);
            chainTypeId = chainType != null ? chainType.getId() : 0L;
        }
        // 2. 依然为空则抛出异常
        if (chainTypeId == 0) {
            throw new BusinessException(ApiCodeEnum.WARNING.getCode(), "审批链分类不能为空");
        }

        // 3. 查询审批链配置列表
        LambdaQueryWrapper<SysApprovalChainConfig> chainConfigQuery = Wrappers.lambdaQuery();
        chainConfigQuery.eq(SysApprovalChainConfig::getModuleId, moduleId);
        chainConfigQuery.eq(SysApprovalChainConfig::getApprovalChainTypeId, chainTypeId);
        List<SysApprovalChainConfig> chainConfigs =
                sysApprovalChainConfigMapper.selectList(chainConfigQuery);

        if (CollectionUtils.isEmpty(chainConfigs)) {
            throw new BusinessException(ApiCodeEnum.WARNING.getCode(), "审批链节点不能为空");
        }

        ModuleApprovalChainConfigResp result = new ModuleApprovalChainConfigResp();
        result.setApprovalChainConfigList(new ArrayList<>());
        result.setButtonList(new ArrayList<>());
        result.setStatusList(new ArrayList<>());
        // 4. 遍历生成审批链
        for (SysApprovalChainConfig chainConfig : chainConfigs) {
            ModuleApprovalChainConfigResp.ApprovalChainConfigDetail resp =
                    new ModuleApprovalChainConfigResp.ApprovalChainConfigDetail();
            resp.setId(chainConfig.getId());
            resp.setModuleId(chainConfig.getModuleId());
            resp.setUpStep(chainConfig.getUpStep());
            resp.setCurrentStep(chainConfig.getCurrentStep());
            resp.setNextStep(chainConfig.getNextStep());
            resp.setApprovalChainTypeId(chainConfig.getApprovalChainTypeId());
            resp.setApprovalRule(chainConfig.getApprovalRule());
            resp.setRejectRule(chainConfig.getRejectRule());
            resp.setRoleApprovalPercent(chainConfig.getRoleApprovalPercent());
            resp.setApproveRoleId(chainConfig.getApproveRoleId());
            resp.setApproverId(chainConfig.getApproverId());
            resp.setDelegateApproverId(chainConfig.getDelegateApproverId());
            resp.setSkipped(chainConfig.getSkipped());
            resp.setShowed(chainConfig.getShowed());
            resp.setChildModuleIds(chainConfig.getChildModuleIds());
            resp.setCurrentStatusId(chainConfig.getCurrentStatusId());
            resp.setNextStatusId(chainConfig.getNextStatusId());
            resp.setAutoApproved(chainConfig.getAutoApproved());
            resp.setAutoApprovedTime(chainConfig.getAutoApprovedTime());
            resp.setDeadlineTime(chainConfig.getDeadlineTime());
            resp.setButtonList(chainConfig.getButtonList());
            resp.setCollaborated(chainConfig.getCollaborated());
            resp.setRepeated(chainConfig.getRepeated());
            resp.setMsgTemplateIds(chainConfig.getMsgTemplateIds());
            resp.setDefaultFlag(chainConfig.getDefaultFlag());
            result.getApprovalChainConfigList().add(resp);

            List<SysApprovalChainConfigButton> buttons =
                    sysApprovalChainConfigButtonMapper.selectList(
                            new LambdaQueryWrapper<SysApprovalChainConfigButton>()
                                    .in(SysApprovalChainConfigButton::getPid, chainConfig.getId()));
            for (SysApprovalChainConfigButton btn : buttons) {
                ModuleApprovalChainConfigResp.ButtonConfigDetail btndetail =
                        new ModuleApprovalChainConfigResp.ButtonConfigDetail();
                btndetail.setId(btn.getId());
                btndetail.setButtonTypeId(btn.getButtonTypeId());
                btndetail.setButtonId(btn.getButtonId());
                btndetail.setIcon(btn.getIcon());
                btndetail.setLogName(btn.getLogName());
                btndetail.setAutoNextTaskFlag(btn.getAutoNextTaskFlag());
                btndetail.setMsgTemplateIds(btn.getMsgTemplateIds());
                // 设置按钮详细信息
                if (btn.getButtonId() != null) {
                    SysButton button = sysButtonMapper.selectById(btn.getButtonId());
                    btndetail.setButtonTitle(button.getTitle());
                    btndetail.setButtonAlias(button.getAlias());
                    btndetail.setButtonDescription(button.getDescription());
                    btndetail.setDefaultBgColor(button.getDefaultBgColor());
                    btndetail.setDefaultFontColor(button.getDefaultFontColor());
                    btndetail.setLevitateBgColor(button.getLevitateBgColor());
                    btndetail.setLevitateFontColor(button.getLevitateFontColor());
                    btndetail.setSelectedBgColor(button.getSelectedBgColor());
                    btndetail.setSelectedFontColor(button.getSelectedFontColor());
                    btndetail.setButtonSortOrder(button.getSortOrder());
                    btndetail.setButtonShowed(button.getShowed());
                    btndetail.setButtonEnabled(button.getEnabled());
                }
                result.getButtonList().add(btndetail);
            }

            List<SysStatus> statusConfigs =
                    sysStatusMapper.selectList(
                            new LambdaQueryWrapper<SysStatus>()
                                    .in(
                                            SysStatus::getId,
                                            List.of(
                                                    chainConfig.getCurrentStatusId(),
                                                    chainConfig.getNextStatusId())));

            for (SysStatus statusConfig : statusConfigs) {
                ModuleApprovalChainConfigResp.StatusNode node =
                        new ModuleApprovalChainConfigResp.StatusNode();
                node.setId(statusConfig.getId());
                node.setPid(statusConfig.getPid());
                node.setTitle(statusConfig.getTitle());
                node.setStatusValue(statusConfig.getStatusValue());
                node.setStatusBackground(statusConfig.getStatusBackground());
                node.setStatusFontColor(statusConfig.getStatusFontColor());
                result.getStatusList().add(node);
            }
        }
        return result;
    }

    /** 根据模块ID和角色ID查询包含角色的审批链类型ID列表 */
    @Override
    public List<Long> getApprovalTypeIdList(List<Long> moduleIds, Long roleId) {
        // 1. 查询审批链配置列表
        LambdaQueryWrapper<SysApprovalChainConfig> chainConfigQuery = Wrappers.lambdaQuery();
        chainConfigQuery.in(SysApprovalChainConfig::getModuleId, moduleIds);
        chainConfigQuery.eq(SysApprovalChainConfig::getApproveRoleId, roleId);
        chainConfigQuery
                .select(SysApprovalChainConfig::getApprovalChainTypeId)
                .groupBy(SysApprovalChainConfig::getApprovalChainTypeId);
        List<SysApprovalChainConfig> chainConfigs =
                sysApprovalChainConfigMapper.selectList(chainConfigQuery);

        if (CollectionUtils.isEmpty(chainConfigs)) {
            return List.of();
        }
        return chainConfigs.stream()
                .map(SysApprovalChainConfig::getApprovalChainTypeId)
                .collect(Collectors.toList());
    }

    private void rearrangeStepsAfterDelete(
            SysApprovalChainConfig deletedConfig,
            SysApprovalChainConfig upStepConfig,
            SysApprovalChainConfig nextStepConfig) {
        if (upStepConfig != null && nextStepConfig != null) {
            nextStepConfig.setCurrentStatusId(upStepConfig.getNextStatusId());
            sysApprovalChainConfigMapper.updateById(nextStepConfig);
        }

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
            for (int i = 0; i < allList.size(); i++) {
                SysApprovalChainConfig item = allList.get(i);
                item.setCurrentStep(i + 1);
                item.setNextStep(i + 2);
                item.setUpStep(i);
                sysApprovalChainConfigMapper.updateById(item);
            }
        }
    }

    @Override
    public List<String> targetType() {
        return List.of(CheckConstant.SYS_ROLE);
    }

    @Override
    public ReferenceCheckResult check(ReferenceContext referenceContext) {
        switch (referenceContext.getTargetType()) {
            case CheckConstant.SYS_ROLE -> {
                return checkRole(referenceContext);
            }
            default -> {
                return ReferenceCheckResult.empty();
            }
        }
    }

    private ReferenceCheckResult checkRole(ReferenceContext referenceContext) {
        StringBuilder sb = new StringBuilder();
        Long count =
                sysApprovalChainConfigMapper.selectCount(
                        Wrappers.<SysApprovalChainConfig>lambdaQuery()
                                .eq(
                                        SysApprovalChainConfig::getApproveRoleId,
                                        referenceContext.getTargetId()));
        if (count == 0) {
            return ReferenceCheckResult.empty();
        }
        return ReferenceCheckResult.builder()
                .referenced(true)
                .count(count)
                .message("角色【" + referenceContext.getTargetName() + "】绑定了" + count + "个审批链配置")
                .build();
    }
}
