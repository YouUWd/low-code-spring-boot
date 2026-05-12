package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysInteractionPermissionApi;
import com.jdec.platform.config.api.dto.request.QuerySysInteractionPermissionReq;
import com.jdec.platform.config.api.dto.request.SysInteractionPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionDetailResp;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionListResp;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionResp;
import com.jdec.platform.config.api.dto.response.SysModuleInteractionPermissionResp;
import com.jdec.platform.config.biz.entity.SysInteractionPermission;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.mapper.SysInteractionPermissionMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.Arrays;
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
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysInteractionPermissionService implements SysInteractionPermissionApi {

    private final SysInteractionPermissionMapper sysInteractionPermissionMapper;
    private final SysModuleMapper sysModuleMapper;

    @Transactional
    @Override
    public void saveInteractionPermission(SysInteractionPermissionSaveReq req) {
        if (req.getId() == null) {
            SysInteractionPermission entity = new SysInteractionPermission();
            BeanUtils.copyProperties(req, entity);
            entity.setSubjectId(AppContext.getSubjectId());
            entity.setProjectNo(AppContext.getProjectNo());
            sysInteractionPermissionMapper.insert(entity);
        } else {
            SysInteractionPermission entity =
                    sysInteractionPermissionMapper.selectOne(
                            Wrappers.<SysInteractionPermission>lambdaQuery()
                                    .eq(SysInteractionPermission::getId, req.getId())
                                    .eq(
                                            SysInteractionPermission::getSubjectId,
                                            AppContext.getSubjectId())
                                    .eq(
                                            SysInteractionPermission::getProjectNo,
                                            AppContext.getProjectNo()));
            if (entity == null) {
                throw new BusinessException("交互权限不存在");
            }
            entity.setType(req.getType());
            entity.setModuleId(req.getModuleId());
            entity.setModuleCode(req.getModuleCode());
            entity.setName(req.getName());
            entity.setCode(req.getCode());
            entity.setSort(req.getSort());
            entity.setStatus(req.getStatus());
            sysInteractionPermissionMapper.updateById(entity);
        }
    }

    @Transactional
    @Override
    public void deleteInteractionPermission(Long id) {
        boolean exists =
                sysInteractionPermissionMapper.exists(
                        Wrappers.<SysInteractionPermission>lambdaQuery()
                                .eq(SysInteractionPermission::getId, id)
                                .eq(
                                        SysInteractionPermission::getSubjectId,
                                        AppContext.getSubjectId())
                                .eq(
                                        SysInteractionPermission::getProjectNo,
                                        AppContext.getProjectNo()));
        if (!exists) {
            throw new BusinessException("交互权限不存在");
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
                            resp.setType(permission.getType());
                            resp.setModuleId(permission.getModuleId());
                            resp.setModuleCode(permission.getModuleCode());
                            resp.setName(permission.getName());
                            resp.setCode(permission.getCode());
                            resp.setSort(permission.getSort());
                            resp.setStatus(permission.getStatus());
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
        resp.setType(entity.getType());
        resp.setModuleId(entity.getModuleId());
        resp.setModuleCode(entity.getModuleCode());
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
                            resp.setType(entity.getType());
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
    public List<SysModuleInteractionPermissionResp> listModuleInteractionPermissionTree() {
        // 1. 查询当前项目下所有模块
        List<SysModule> allModules =
                sysModuleMapper.selectList(
                        new LambdaQueryWrapper<SysModule>()
                                .eq(SysModule::getProjectNo, AppContext.getProjectNo())
                                .orderByAsc(SysModule::getSortOrder)
                                .orderByAsc(SysModule::getId));

        // 2. 查询当前项目下所有交互权限，按 moduleId+type 分组
        List<SysInteractionPermission> allPermissions =
                sysInteractionPermissionMapper.selectList(
                        new LambdaQueryWrapper<SysInteractionPermission>()
                                .eq(SysInteractionPermission::getProjectNo, AppContext.getProjectNo())
                                .eq(SysInteractionPermission::getSubjectId, AppContext.getSubjectId())
                                .orderByAsc(SysInteractionPermission::getSort)
                                .orderByAsc(SysInteractionPermission::getId));

        Map<String, List<SysInteractionPermissionResp>> permissionMap =
                allPermissions.stream()
                        .collect(
                                Collectors.groupingBy(
                                        p -> p.getModuleId() + "_" + p.getType(),
                                        Collectors.mapping(
                                                p -> {
                                                    SysInteractionPermissionResp r =
                                                            new SysInteractionPermissionResp();
                                                    r.setId(p.getId());
                                                    r.setType(p.getType());
                                                    r.setModuleId(p.getModuleId());
                                                    r.setModuleCode(p.getModuleCode());
                                                    r.setName(p.getName());
                                                    r.setCode(p.getCode());
                                                    r.setSort(p.getSort());
                                                    r.setStatus(p.getStatus());
                                                    return r;
                                                },
                                                Collectors.toList())));

        // 3. 按 parentId 分组，用于构造树
        Map<Long, List<SysModule>> modulesByParent =
                allModules.stream().collect(Collectors.groupingBy(SysModule::getParentId));

        // 4. 从根节点（parentId=0）开始递归构建
        return buildModuleTree(modulesByParent, permissionMap, 0L);
    }

    private static final List<Integer> INTERACTION_TYPES = Arrays.asList(1, 2, 3);
    private static final Map<Integer, String> TYPE_LABEL =
            Map.of(1, "申请", 2, "编辑", 3, "查看");

    private List<SysModuleInteractionPermissionResp> buildModuleTree(
            Map<Long, List<SysModule>> modulesByParent,
            Map<String, List<SysInteractionPermissionResp>> permissionMap,
            Long parentId) {
        List<SysModule> children = modulesByParent.get(parentId);
        if (children == null || children.isEmpty()) {
            return new ArrayList<>();
        }
        List<SysModuleInteractionPermissionResp> result = new ArrayList<>();
        for (SysModule module : children) {
            SysModuleInteractionPermissionResp node = new SysModuleInteractionPermissionResp();
            node.setNodeKey(String.valueOf(module.getId()));
            node.setNodeName(module.getModuleName());
            node.setModuleId(module.getId());
            node.setModuleCode(module.getModuleCode());

            // 递归子模块
            List<SysModuleInteractionPermissionResp> subChildren =
                    buildModuleTree(modulesByParent, permissionMap, module.getId());

            // 为当前模块节点追加三个虚拟类型子节点
            for (Integer type : INTERACTION_TYPES) {
                String key = module.getId() + "_" + type;
                SysModuleInteractionPermissionResp typeNode =
                        new SysModuleInteractionPermissionResp();
                typeNode.setNodeKey(key);
                typeNode.setNodeName(module.getModuleName() + TYPE_LABEL.get(type));
                typeNode.setModuleId(module.getId());
                typeNode.setModuleCode(module.getModuleCode());
                typeNode.setType(type);
                typeNode.setPermissions(permissionMap.getOrDefault(key, new ArrayList<>()));
                subChildren.add(typeNode);
            }

            node.setChildren(subChildren);
            result.add(node);
        }
        return result;
    }
}
