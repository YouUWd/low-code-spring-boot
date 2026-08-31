package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysInteractionPermissionApi;
import com.jdec.platform.config.api.dto.request.QuerySysInteractionPermissionReq;
import com.jdec.platform.config.api.dto.request.SysInteractionPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.config.biz.check.CheckConstant;
import com.jdec.platform.config.biz.check.ReferenceCheckManager;
import com.jdec.platform.config.biz.check.ReferenceContext;
import com.jdec.platform.config.biz.entity.SysInteractionPermission;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.mapper.SysInteractionPermissionMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.shared.audit.annotation.DataAudit;
import com.jdec.platform.shared.audit.enums.OperationType;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.exception.PopException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 交互权限 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_ENGINE)
public class SysInteractionPermissionService implements SysInteractionPermissionApi {

    private final SysInteractionPermissionMapper sysInteractionPermissionMapper;
    private final SysModuleMapper sysModuleMapper;
    private final ReferenceCheckManager referenceCheckManager;

    @Transactional
    @Override
    @DataAudit(
            module = "系统设置",
            subModule = "交互权限",
            operation = OperationType.UPDATE,
            tableName = "sys_interaction_permission",
            dataIdField = "#req.id")
    public SysRightResp saveInteractionPermission(SysInteractionPermissionSaveReq req) {
        // 保存前先查询旧数据（用于审计）
        SysInteractionPermission oldEntity = null;
        if (req.getId() != null) {
            oldEntity =
                    sysInteractionPermissionMapper.selectOne(
                            Wrappers.<SysInteractionPermission>lambdaQuery()
                                    .eq(SysInteractionPermission::getId, req.getId())
                                    .eq(
                                            SysInteractionPermission::getSubjectId,
                                            AppContext.getSubjectId())
                                    .eq(
                                            SysInteractionPermission::getProjectNo,
                                            AppContext.getProjectNo()));
            if (oldEntity == null) {
                throw new BusinessException("交互权限不存在");
            }
        }

        SysInteractionPermission entity;
        if (req.getId() == null) {
            // 新增：校验名称唯一性
            boolean exists =
                    sysInteractionPermissionMapper.exists(
                            Wrappers.<SysInteractionPermission>lambdaQuery()
                                    .eq(SysInteractionPermission::getName, req.getName())
                                    .eq(
                                            SysInteractionPermission::getSubjectId,
                                            AppContext.getSubjectId())
                                    .eq(
                                            SysInteractionPermission::getProjectNo,
                                            AppContext.getProjectNo()));
            if (exists) {
                throw new BusinessException("交互权限名称已存在");
            }
            entity = new SysInteractionPermission();
            BeanUtils.copyProperties(req, entity);
            entity.setSubjectId(AppContext.getSubjectId());
            entity.setProjectNo(AppContext.getProjectNo());
            sysInteractionPermissionMapper.insert(entity);
            // 设置id到请求对象，用于审计切面重新解析dataId
            req.setId(entity.getId());
        } else {
            entity = oldEntity;
            // 编辑：校验名称唯一性（排除自己）
            if (!entity.getName().equals(req.getName())) {
                boolean exists =
                        sysInteractionPermissionMapper.exists(
                                Wrappers.<SysInteractionPermission>lambdaQuery()
                                        .eq(SysInteractionPermission::getName, req.getName())
                                        .ne(SysInteractionPermission::getId, req.getId())
                                        .eq(
                                                SysInteractionPermission::getSubjectId,
                                                AppContext.getSubjectId())
                                        .eq(
                                                SysInteractionPermission::getProjectNo,
                                                AppContext.getProjectNo()));
                if (exists) {
                    throw new BusinessException("交互权限名称已存在");
                }
            }
            entity.setModuleId(req.getModuleId());
            entity.setModuleCode(req.getModuleCode());
            entity.setType(req.getType());
            entity.setName(req.getName());
            entity.setCode(req.getCode());
            entity.setSort(req.getSort());
            entity.setStatus(req.getStatus());
            sysInteractionPermissionMapper.updateById(entity);
        }

        // 构建返回对象
        SysRightResp resp = new SysRightResp();
        resp.setId(entity.getId());
        resp.setRightName(entity.getName());
        resp.setRightSlug(entity.getCode());
        resp.setNodeType(2);
        resp.setDescription(null);
        resp.setType(entity.getType());

        // 查询父级模块信息
        if (entity.getModuleId() != null) {
            SysModule module = sysModuleMapper.selectById(entity.getModuleId());
            if (module != null) {
                resp.setParentRightName(module.getModuleName());
                resp.setParentRightSlug(module.getModuleCode());
            }
        }

        return resp;
    }

    @Transactional
    @Override
    @DataAudit(
            module = "系统设置",
            subModule = "交互权限",
            operation = OperationType.DELETE,
            deleteDisplayField = "name",
            entityClass = SysInteractionPermission.class,
            tableName = "sys_interaction_permission",
            dataIdField = "#id")
    public void deleteInteractionPermission(Long id, boolean force) {
        SysInteractionPermission sysInteractionPermission =
                sysInteractionPermissionMapper.selectOne(
                        Wrappers.<SysInteractionPermission>lambdaQuery()
                                .eq(SysInteractionPermission::getId, id)
                                .eq(
                                        SysInteractionPermission::getSubjectId,
                                        AppContext.getSubjectId())
                                .eq(
                                        SysInteractionPermission::getProjectNo,
                                        AppContext.getProjectNo()));
        if (sysInteractionPermission == null) {
            throw new BusinessException("交互权限不存在");
        }
        // 引用检查
        ReferenceContext context =
                ReferenceContext.builder()
                        .targetType(CheckConstant.SYS_INTERACTION_PERMISSION)
                        .targetId(id)
                        .targetName(sysInteractionPermission.getName())
                        .build();
        List<String> check = referenceCheckManager.check(context);
        if (!check.isEmpty()) {
            throw new PopException(String.join("\n", check));
        }
        sysInteractionPermissionMapper.deleteById(id);
    }

    @Override
    public List<SysInteractionPermissionListResp> listInteractionPermission(
            QuerySysInteractionPermissionReq req) {
        List<SysInteractionPermission> list =
                sysInteractionPermissionMapper.selectList(
                        new LambdaQueryWrapper<SysInteractionPermission>()
                                .eq(
                                        SysInteractionPermission::getProjectNo,
                                        AppContext.getProjectNo())
                                .eq(
                                        SysInteractionPermission::getSubjectId,
                                        AppContext.getSubjectId())
                                .like(
                                        StringUtils.hasText(req.getName()),
                                        SysInteractionPermission::getName,
                                        req.getName())
                                .like(
                                        StringUtils.hasText(req.getCode()),
                                        SysInteractionPermission::getCode,
                                        req.getCode())
                                .eq(
                                        req.getStatus() != null,
                                        SysInteractionPermission::getStatus,
                                        req.getStatus())
                                .eq(
                                        req.getType() != null,
                                        SysInteractionPermission::getType,
                                        req.getType())
                                .orderByAsc(SysInteractionPermission::getSort)
                                .orderByAsc(SysInteractionPermission::getId));
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        return list.stream()
                .map(
                        permission -> {
                            SysInteractionPermissionListResp resp =
                                    new SysInteractionPermissionListResp();
                            resp.setId(permission.getId());
                            resp.setModuleId(permission.getModuleId());
                            resp.setModuleCode(permission.getModuleCode());
                            resp.setType(permission.getType());
                            resp.setName(permission.getName());
                            resp.setCode(permission.getCode());
                            resp.setSort(permission.getSort());
                            resp.setStatus(permission.getStatus());
                            resp.setType(permission.getType());
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public SysInteractionPermissionDetailResp getInteractionPermission(Long id) {
        SysInteractionPermission entity =
                sysInteractionPermissionMapper.selectOne(
                        Wrappers.<SysInteractionPermission>lambdaQuery()
                                .eq(SysInteractionPermission::getId, id)
                                .eq(
                                        SysInteractionPermission::getSubjectId,
                                        AppContext.getSubjectId())
                                .eq(
                                        SysInteractionPermission::getProjectNo,
                                        AppContext.getProjectNo()));
        if (entity == null) {
            throw new BusinessException("交互权限不存在");
        }
        SysInteractionPermissionDetailResp resp = new SysInteractionPermissionDetailResp();
        resp.setId(entity.getId());
        resp.setModuleId(entity.getModuleId());
        resp.setModuleCode(entity.getModuleCode());
        resp.setType(entity.getType());
        resp.setName(entity.getName());
        resp.setCode(entity.getCode());
        resp.setSort(entity.getSort());
        resp.setStatus(entity.getStatus());
        resp.setSubjectId(entity.getSubjectId());
        resp.setProjectNo(entity.getProjectNo());
        if (entity.getCreatedDate() != null) {
            resp.setCreatedDate(entity.getCreatedDate().toString());
        }
        resp.setCreatedName(entity.getCreatedName());
        if (entity.getUpdatedDate() != null) {
            resp.setUpdatedDate(entity.getUpdatedDate().toString());
        }
        resp.setUpdatedName(entity.getUpdatedName());
        return resp;
    }

    @Override
    public List<SysInteractionPermissionResp> listAllInteractionPermissions() {
        List<SysInteractionPermission> list =
                sysInteractionPermissionMapper.selectList(
                        new LambdaQueryWrapper<SysInteractionPermission>()
                                .eq(
                                        SysInteractionPermission::getProjectNo,
                                        AppContext.getProjectNo())
                                .eq(
                                        SysInteractionPermission::getSubjectId,
                                        AppContext.getSubjectId())
                                .orderByAsc(SysInteractionPermission::getSort)
                                .orderByAsc(SysInteractionPermission::getId));
        return list.stream()
                .map(
                        entity -> {
                            SysInteractionPermissionResp resp = new SysInteractionPermissionResp();
                            resp.setId(entity.getId());
                            resp.setModuleId(entity.getModuleId());
                            resp.setModuleCode(entity.getModuleCode());
                            resp.setName(entity.getName());
                            resp.setCode(entity.getCode());
                            resp.setSort(entity.getSort());
                            resp.setStatus(entity.getStatus());
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public List<SysModuleInteractionPermissionResp> listModuleInteractionPermissionTree(
            Integer category) {
        // 1. 查询当前项目下所有模块
        List<SysModule> allModules =
                sysModuleMapper.selectList(
                        new LambdaQueryWrapper<SysModule>()
                                .eq(SysModule::getProjectNo, AppContext.getProjectNo())
                                .eq(SysModule::getSubjectId, AppContext.getSubjectId())
                                .orderByAsc(SysModule::getSortOrder)
                                .orderByAsc(SysModule::getId));

        // 2. 查询当前项目下所有交互权限，按 moduleId 分组
        List<SysInteractionPermission> allPermissions =
                sysInteractionPermissionMapper.selectList(
                        new LambdaQueryWrapper<SysInteractionPermission>()
                                .eq(
                                        SysInteractionPermission::getProjectNo,
                                        AppContext.getProjectNo())
                                .eq(
                                        SysInteractionPermission::getSubjectId,
                                        AppContext.getSubjectId())
                                .orderByAsc(SysInteractionPermission::getSort)
                                .orderByAsc(SysInteractionPermission::getId));

        // 转换为 DTO 并按 moduleId 和 type 分组
        Map<Long, Map<Integer, List<SysInteractionPermissionResp>>> permissionMap =
                allPermissions.stream()
                        .map(
                                p -> {
                                    SysInteractionPermissionResp r =
                                            new SysInteractionPermissionResp();
                                    r.setId(p.getId());
                                    r.setModuleId(p.getModuleId());
                                    r.setModuleCode(p.getModuleCode());
                                    r.setType(p.getType());
                                    r.setName(p.getName());
                                    r.setCode(p.getCode());
                                    r.setSort(p.getSort());
                                    r.setStatus(p.getStatus());
                                    return r;
                                })
                        .collect(
                                Collectors.groupingBy(
                                        SysInteractionPermissionResp::getModuleId,
                                        Collectors.groupingBy(
                                                SysInteractionPermissionResp::getType)));

        // 3. 构建响应列表
        List<SysModuleInteractionPermissionResp> result = new ArrayList<>();
        for (SysModule module : allModules) {
            SysModuleInteractionPermissionResp node = new SysModuleInteractionPermissionResp();
            node.setModuleId(module.getId());
            node.setModuleName(module.getModuleName());
            node.setModuleCode(module.getModuleCode());

            Map<Integer, List<SysInteractionPermissionResp>> typeMap =
                    permissionMap.getOrDefault(module.getId(), new java.util.HashMap<>());
            node.setApplyPermissions(typeMap.getOrDefault(1, new ArrayList<>()));
            node.setEditPermissions(typeMap.getOrDefault(2, new ArrayList<>()));
            node.setViewPermissions(typeMap.getOrDefault(3, new ArrayList<>()));
            node.setChildren(Collections.emptyList());
            result.add(node);
        }
        return result;
    }
}
