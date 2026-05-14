package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jdec.platform.config.api.SysApprovalChainTypeApi;
import com.jdec.platform.config.api.dto.request.ApprovalChainTypePageReq;
import com.jdec.platform.config.api.dto.request.ApprovalChainTypeSaveReq;
import com.jdec.platform.config.api.dto.response.ApprovalChainCascadeResp;
import com.jdec.platform.config.api.dto.response.ApprovalChainTypePageResp;
import com.jdec.platform.config.biz.entity.SysApprovalChainType;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.mapper.SysApprovalChainTypeMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.model.PageResult;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 审批链类型 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysApprovalChainTypeService implements SysApprovalChainTypeApi {

    private final SysApprovalChainTypeMapper sysApprovalChainTypeMapper;
    private final SysModuleMapper sysModuleMapper;

    @Override
    public List<ApprovalChainCascadeResp> getCascadeOptions() {
        // 查询当前主体和应用下所有审批链分类
        List<SysApprovalChainType> chainTypes =
                sysApprovalChainTypeMapper.selectList(
                        new LambdaQueryWrapper<SysApprovalChainType>()
                                .eq(SysApprovalChainType::getSubjectId, AppContext.getSubjectId())
                                .eq(SysApprovalChainType::getProjectNo, AppContext.getProjectNo())
                                .eq(SysApprovalChainType::getEnabled, 1));

        if (chainTypes.isEmpty()) {
            return Collections.emptyList();
        }

        // 提取模块ID列表
        Set<Long> moduleIds =
                chainTypes.stream()
                        .map(SysApprovalChainType::getModuleId)
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
    @Transactional(rollbackFor = Exception.class)
    public void save(ApprovalChainTypeSaveReq request) {
        SysApprovalChainType entity = new SysApprovalChainType();
        entity.setId(request.getId());
        entity.setTitle(request.getTitle());
        entity.setModuleId(request.getModuleId());
        entity.setDefaultFlag(request.getDefaultFlag());
        entity.setEnabled(request.getEnabled());
        entity.setSubjectId(AppContext.getSubjectId());
        entity.setProjectNo(AppContext.getProjectNo());

        if (request.getId() == null) {
            // 新增
            sysApprovalChainTypeMapper.insert(entity);
        } else {
            // 编辑
            sysApprovalChainTypeMapper.updateById(entity);
        }
    }

    @Override
    public PageResult<ApprovalChainTypePageResp> getPage(ApprovalChainTypePageReq request) {
        Page<SysApprovalChainType> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<SysApprovalChainType> wrapper =
                new LambdaQueryWrapper<SysApprovalChainType>()
                        .eq(SysApprovalChainType::getSubjectId, AppContext.getSubjectId())
                        .eq(SysApprovalChainType::getProjectNo, AppContext.getProjectNo())
                        .eq(
                                request.getModuleId() != null,
                                SysApprovalChainType::getModuleId,
                                request.getModuleId())
                        .in(
                                request.getChainTypeIds() != null
                                        && !request.getChainTypeIds().isEmpty(),
                                SysApprovalChainType::getId,
                                request.getChainTypeIds())
                        .like(
                                StringUtils.hasText(request.getTitle()),
                                SysApprovalChainType::getTitle,
                                request.getTitle())
                        .orderByAsc(SysApprovalChainType::getId);

        IPage<SysApprovalChainType> typePage = sysApprovalChainTypeMapper.selectPage(page, wrapper);

        // 收集模块ID
        Set<Long> moduleIds =
                typePage.getRecords().stream()
                        .map(SysApprovalChainType::getModuleId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        // 批量查询模块信息
        Map<Long, String> moduleMap = Collections.emptyMap();
        if (!moduleIds.isEmpty()) {
            moduleMap =
                    sysModuleMapper
                            .selectList(
                                    new LambdaQueryWrapper<SysModule>()
                                            .in(SysModule::getId, moduleIds))
                            .stream()
                            .collect(Collectors.toMap(SysModule::getId, SysModule::getModuleName));
        }

        // 构建响应
        Map<Long, String> finalModuleMap = moduleMap;
        List<ApprovalChainTypePageResp> respList =
                typePage.getRecords().stream()
                        .map(
                                type -> {
                                    ApprovalChainTypePageResp resp =
                                            new ApprovalChainTypePageResp();
                                    resp.setId(type.getId());
                                    resp.setTitle(type.getTitle());
                                    resp.setModuleId(type.getModuleId());
                                    resp.setModuleName(finalModuleMap.get(type.getModuleId()));
                                    resp.setDefaultFlag(type.getDefaultFlag());
                                    resp.setEnabled(type.getEnabled());
                                    return resp;
                                })
                        .collect(Collectors.toList());

        return PageResult.of(
                request.getPageNum(), request.getPageSize(), typePage.getTotal(), respList);
    }
}
