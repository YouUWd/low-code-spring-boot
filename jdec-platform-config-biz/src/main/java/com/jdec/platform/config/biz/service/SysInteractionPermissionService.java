package com.jdec.platform.config.biz.service;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysInteractionPermissionApi;
import com.jdec.platform.config.api.dto.request.QuerySysInteractionPermissionReq;
import com.jdec.platform.config.api.dto.request.SysInteractionPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionDetailResp;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionListResp;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionOptionResp;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionResp;
import com.jdec.platform.config.biz.entity.SysInteractionPermission;
import com.jdec.platform.config.biz.mapper.SysInteractionPermissionMapper;
import com.jdec.platform.shared.context.AppCodeContext;
import com.jdec.platform.shared.context.SubjectContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
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

    @Override
    public void saveInteractionPermission(SysInteractionPermissionSaveReq req) {
        if (req.getId() == null) {
            // 新增
            SysInteractionPermission entity = new SysInteractionPermission();
            BeanUtils.copyProperties(req, entity);
            if (entity.getParentId() == null) {
                entity.setParentId(0L);
            }
            entity.setSubjectId(SubjectContext.get());
            entity.setAppCode(AppCodeContext.get());
            sysInteractionPermissionMapper.insert(entity);
        } else {
            // 编辑
            SysInteractionPermission entity =
                    sysInteractionPermissionMapper.selectOne(
                            Wrappers.<SysInteractionPermission>lambdaQuery()
                                    .eq(SysInteractionPermission::getId, req.getId())
                                    .eq(
                                            SysInteractionPermission::getSubjectId,
                                            SubjectContext.get())
                                    .eq(
                                            SysInteractionPermission::getAppCode,
                                            AppCodeContext.get()));
            if (entity == null) {
                throw new BusinessException("交互权限不存在");
            }
            // 校验不能把自己设为自己的父节点
            if (req.getId().equals(req.getParentId())) {
                throw new BusinessException("不能将自己设为自己的父节点");
            }
            entity.setName(req.getName());
            entity.setCode(req.getCode());
            entity.setParentId(req.getParentId() != null ? req.getParentId() : 0L);
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
                                .eq(SysInteractionPermission::getSubjectId, SubjectContext.get())
                                .eq(SysInteractionPermission::getAppCode, AppCodeContext.get()));
        if (!exists) {
            throw new BusinessException("交互权限不存在");
        }
        // 级联删除所有子节点
        Set<Long> ids = collectChildrenIds(id, SubjectContext.get());
        ids.add(id);
        sysInteractionPermissionMapper.deleteByIds(ids);
    }

    @Override
    public List<SysInteractionPermissionOptionResp> listParentInteractionPermissionOptions() {
        List<SysInteractionPermission> list =
                sysInteractionPermissionMapper.selectList(
                        new LambdaQueryWrapper<SysInteractionPermission>()
                                .eq(SysInteractionPermission::getAppCode, AppCodeContext.get())
                                .eq(SysInteractionPermission::getSubjectId, SubjectContext.get())
                                .eq(SysInteractionPermission::getParentId, 0L)
                                .eq(SysInteractionPermission::getStatus, 1)
                                .orderByAsc(SysInteractionPermission::getSort)
                                .orderByAsc(SysInteractionPermission::getId));
        return list.stream()
                .map(
                        permission -> {
                            SysInteractionPermissionOptionResp resp =
                                    new SysInteractionPermissionOptionResp();
                            resp.setId(permission.getId());
                            resp.setName(permission.getName());
                            resp.setCode(permission.getCode());
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public List<SysInteractionPermissionListResp> listInteractionPermission(
            QuerySysInteractionPermissionReq req) {
        List<SysInteractionPermission> list =
                sysInteractionPermissionMapper.selectList(
                        new LambdaQueryWrapper<SysInteractionPermission>()
                                .eq(SysInteractionPermission::getAppCode, AppCodeContext.get())
                                .eq(SysInteractionPermission::getSubjectId, SubjectContext.get())
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
                                        req.getParentId() != null,
                                        SysInteractionPermission::getParentId,
                                        req.getParentId())
                                .orderByAsc(SysInteractionPermission::getSort)
                                .orderByAsc(SysInteractionPermission::getId));
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        // 查询所有父节点
        Set<Long> parentIds =
                list.stream()
                        .map(SysInteractionPermission::getParentId)
                        .filter(pid -> pid != null && pid != 0L)
                        .collect(Collectors.toSet());
        Map<Long, SysInteractionPermission> parentMap = new HashMap<>();
        if (CollUtil.isNotEmpty(parentIds)) {
            parentMap =
                    sysInteractionPermissionMapper.selectByIds(parentIds).stream()
                            .collect(Collectors.toMap(SysInteractionPermission::getId, r -> r));
        }
        Map<Long, SysInteractionPermission> finalParentMap = parentMap;
        return list.stream()
                .map(
                        permission -> {
                            SysInteractionPermissionListResp resp =
                                    new SysInteractionPermissionListResp();
                            resp.setId(permission.getId());
                            resp.setParentId(permission.getParentId());
                            resp.setName(permission.getName());
                            resp.setCode(permission.getCode());
                            resp.setSort(permission.getSort());
                            resp.setStatus(permission.getStatus());
                            if (permission.getParentId() != null
                                    && permission.getParentId() != 0L) {
                                SysInteractionPermission parent =
                                        finalParentMap.get(permission.getParentId());
                                if (parent != null) {
                                    resp.setParentName(parent.getName());
                                } else {
                                    resp.setParentName("");
                                }
                            }
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
                                .eq(SysInteractionPermission::getSubjectId, SubjectContext.get())
                                .eq(SysInteractionPermission::getAppCode, AppCodeContext.get()));
        if (entity == null) {
            throw new BusinessException("交互权限不存在");
        }
        SysInteractionPermissionDetailResp resp = new SysInteractionPermissionDetailResp();
        resp.setId(entity.getId());
        resp.setParentId(entity.getParentId());
        resp.setName(entity.getName());
        resp.setCode(entity.getCode());
        resp.setSort(entity.getSort());
        resp.setStatus(entity.getStatus());
        resp.setSubjectId(entity.getSubjectId());
        resp.setAppCode(entity.getAppCode());
        if (entity.getCreatedDate() != null) {
            resp.setCreatedDate(entity.getCreatedDate().toString());
        }
        resp.setCreatedName(entity.getCreatedName());
        if (entity.getUpdatedDate() != null) {
            resp.setUpdatedDate(entity.getUpdatedDate().toString());
        }
        resp.setUpdatedName(entity.getUpdatedName());
        if (entity.getParentId() != null && entity.getParentId() != 0L) {
            SysInteractionPermission parent =
                    sysInteractionPermissionMapper.selectById(entity.getParentId());
            if (parent != null) {
                resp.setParentName(parent.getName());
            }
        }
        return resp;
    }

    @Override
    public List<SysInteractionPermissionResp> listInteractionPermissionTree() {
        // 查询所有交互权限
        List<SysInteractionPermission> allList =
                sysInteractionPermissionMapper.selectList(
                        new LambdaQueryWrapper<SysInteractionPermission>()
                                .eq(SysInteractionPermission::getAppCode, AppCodeContext.get())
                                .eq(SysInteractionPermission::getSubjectId, SubjectContext.get())
                                .orderByAsc(SysInteractionPermission::getSort)
                                .orderByAsc(SysInteractionPermission::getId));

        if (CollUtil.isEmpty(allList)) {
            return new ArrayList<>();
        }

        // 转换为Resp并建立ID映射
        Map<Long, SysInteractionPermissionResp> respMap = new LinkedHashMap<>();
        for (SysInteractionPermission entity : allList) {
            SysInteractionPermissionResp resp = new SysInteractionPermissionResp();
            resp.setId(entity.getId());
            resp.setParentId(entity.getParentId());
            resp.setName(entity.getName());
            resp.setCode(entity.getCode());
            resp.setSort(entity.getSort());
            resp.setStatus(entity.getStatus());
            respMap.put(entity.getId(), resp);
        }

        // 构建父子关系
        List<SysInteractionPermissionResp> rootList = new ArrayList<>();
        for (SysInteractionPermissionResp resp : respMap.values()) {
            Long parentId = resp.getParentId();
            if (parentId == null || parentId == 0L) {
                // 根节点
                rootList.add(resp);
            } else {
                // 子节点，找到父节点并添加
                SysInteractionPermissionResp parent = respMap.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(resp);
                } else {
                    // 父节点不存在，当作根节点处理
                    rootList.add(resp);
                }
            }
        }

        return rootList;
    }

    /** 收集所有子节点ID */
    private Set<Long> collectChildrenIds(Long parentId, Long subjectId) {
        Set<Long> result = new java.util.HashSet<>();
        List<SysInteractionPermission> children =
                sysInteractionPermissionMapper.selectList(
                        Wrappers.<SysInteractionPermission>lambdaQuery()
                                .eq(SysInteractionPermission::getParentId, parentId)
                                .eq(SysInteractionPermission::getSubjectId, subjectId)
                                .eq(SysInteractionPermission::getAppCode, AppCodeContext.get()));
        for (SysInteractionPermission child : children) {
            result.add(child.getId());
            result.addAll(collectChildrenIds(child.getId(), subjectId));
        }
        return result;
    }
}
