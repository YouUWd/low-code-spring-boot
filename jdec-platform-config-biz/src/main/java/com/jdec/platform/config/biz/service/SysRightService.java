package com.jdec.platform.config.biz.service;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysRightApi;
import com.jdec.platform.config.api.dto.request.QuerySysRightReq;
import com.jdec.platform.config.api.dto.request.SysRightSaveReq;
import com.jdec.platform.config.api.dto.response.SysRightOptionResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import com.jdec.platform.config.biz.entity.SysDataPermissionType;
import com.jdec.platform.config.biz.entity.SysInteractionPermission;
import com.jdec.platform.config.biz.entity.SysRight;
import com.jdec.platform.config.biz.entity.SysSpecialPermission;
import com.jdec.platform.config.biz.mapper.SysDataPermissionTypeMapper;
import com.jdec.platform.config.biz.mapper.SysInteractionPermissionMapper;
import com.jdec.platform.config.biz.mapper.SysRightMapper;
import com.jdec.platform.config.biz.mapper.SysSpecialPermissionMapper;
import com.jdec.platform.shared.context.AppContext;
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

/** 权限节点 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysRightService implements SysRightApi {

    private final SysRightMapper sysRightMapper;
    private final SysInteractionPermissionMapper sysInteractionPermissionMapper;
    private final SysDataPermissionTypeMapper sysDataPermissionTypeMapper;
    private final SysSpecialPermissionMapper sysSpecialPermissionMapper;

    @Override
    public void saveRight(SysRightSaveReq req) {
        if (req.getId() == null) {
            // 新增
            SysRight entity = new SysRight();
            BeanUtils.copyProperties(req, entity);
            if (entity.getPid() == null) {
                entity.setPid(0L);
            }
            entity.setSubjectId(AppContext.getSubjectId());
            entity.setProjectNo(AppContext.getProjectNo());
            sysRightMapper.insert(entity);
        } else {
            // 编辑
            SysRight entity =
                    sysRightMapper.selectOne(
                            Wrappers.<SysRight>lambdaQuery()
                                    .eq(SysRight::getId, req.getId())
                                    .eq(SysRight::getSubjectId, AppContext.getSubjectId())
                                    .eq(SysRight::getProjectNo, AppContext.getProjectNo()));
            if (entity == null) {
                throw new BusinessException("权限节点不存在");
            }
            // 校验不能把自己设为自己的父节点
            if (req.getId().equals(req.getPid())) {
                throw new BusinessException("不能将自己设为自己的父节点");
            }
            entity.setRightName(req.getRightName());
            entity.setRightSlug(req.getRightSlug());
            entity.setPid(req.getPid() != null ? req.getPid() : 0L);
            entity.setDescription(req.getDescription());
            entity.setNodeType(req.getNodeType());
            sysRightMapper.updateById(entity);
        }
    }

    @Transactional
    @Override
    public void deleteRight(Long id) {
        boolean exists =
                sysRightMapper.exists(
                        Wrappers.<SysRight>lambdaQuery()
                                .eq(SysRight::getId, id)
                                .eq(SysRight::getSubjectId, AppContext.getSubjectId())
                                .eq(SysRight::getProjectNo, AppContext.getProjectNo()));
        if (!exists) {
            throw new BusinessException("权限节点不存在");
        }
        // 级联删除所有子节点
        Set<Long> ids = collectChildrenIds(id, AppContext.getSubjectId());
        ids.add(id);
        sysRightMapper.deleteByIds(ids);
    }

    @Override
    public List<SysRightOptionResp> listParentOptions(Integer nodeType) {
        List<SysRight> list =
                sysRightMapper.selectList(
                        new LambdaQueryWrapper<SysRight>()
                                .eq(SysRight::getProjectNo, AppContext.getProjectNo())
                                .eq(SysRight::getPid, 0L)
                                .eq(nodeType != null, SysRight::getNodeType, nodeType)
                                .orderByAsc(SysRight::getId));
        return list.stream()
                .map(
                        right -> {
                            SysRightOptionResp resp = new SysRightOptionResp();
                            resp.setId(right.getId());
                            resp.setRightName(right.getRightName());
                            resp.setRightSlug(right.getRightSlug());
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public List<SysRightResp> listRight(QuerySysRightReq req) {
        List<SysRightResp> result = new LinkedList<>();

        // 1. 查询数据权限类型（nodeType = 1）
        LambdaQueryWrapper<SysDataPermissionType> dataPermissionWrapper =
                new LambdaQueryWrapper<>();
        dataPermissionWrapper
                .eq(SysDataPermissionType::getProjectNo, AppContext.getProjectNo())
                .eq(SysDataPermissionType::getSubjectId, AppContext.getSubjectId())
                .like(
                        StringUtils.hasText(req.getRightName()),
                        SysDataPermissionType::getName,
                        req.getRightName())
                .orderByAsc(SysDataPermissionType::getSort)
                .orderByAsc(SysDataPermissionType::getId);
        List<SysDataPermissionType> dataPermissionList =
                sysDataPermissionTypeMapper.selectList(dataPermissionWrapper);
        for (SysDataPermissionType item : dataPermissionList) {
            SysRightResp resp = new SysRightResp();
            resp.setId(item.getId());
            resp.setPid(0L);
            resp.setRightName(item.getName());
            resp.setRightSlug(item.getCode());
            resp.setNodeType(1); // 数据权限
            resp.setDescription(item.getDescription());
            result.add(resp);
        }

        // 2. 查询交互权限（nodeType = 2），需要查询父级信息
        LambdaQueryWrapper<SysInteractionPermission> interactionWrapper =
                new LambdaQueryWrapper<>();
        interactionWrapper
                .eq(SysInteractionPermission::getProjectNo, AppContext.getProjectNo())
                .eq(SysInteractionPermission::getSubjectId, AppContext.getSubjectId())
                .like(
                        StringUtils.hasText(req.getRightName()),
                        SysInteractionPermission::getName,
                        req.getRightName())
                .orderByAsc(SysInteractionPermission::getSort)
                .orderByAsc(SysInteractionPermission::getId);
        List<SysInteractionPermission> interactionList =
                sysInteractionPermissionMapper.selectList(interactionWrapper);

        // 查询所有父级交互权限
        Set<Long> parentIds =
                interactionList.stream()
                        .map(SysInteractionPermission::getParentId)
                        .filter(pid -> pid != null && pid != 0L)
                        .collect(Collectors.toSet());
        Map<Long, SysInteractionPermission> parentMap = new HashMap<>();
        if (CollUtil.isNotEmpty(parentIds)) {
            parentMap =
                    sysInteractionPermissionMapper.selectByIds(parentIds).stream()
                            .collect(Collectors.toMap(SysInteractionPermission::getId, r -> r));
        }

        for (SysInteractionPermission item : interactionList) {
            SysRightResp resp = new SysRightResp();
            resp.setId(item.getId());
            resp.setPid(item.getParentId());
            resp.setRightName(item.getName());
            resp.setRightSlug(item.getCode());
            resp.setNodeType(2); // 交互权限
            // 设置父级信息
            if (item.getParentId() != null && item.getParentId() != 0L) {
                SysInteractionPermission parent = parentMap.get(item.getParentId());
                if (parent != null) {
                    resp.setParentRightName(parent.getName());
                    resp.setParentRightSlug(parent.getCode());
                }
            }
            result.add(resp);
        }

        // 3. 查询特殊权限（nodeType = 3）
        LambdaQueryWrapper<SysSpecialPermission> specialWrapper = new LambdaQueryWrapper<>();
        specialWrapper
                .eq(SysSpecialPermission::getProjectNo, AppContext.getProjectNo())
                .eq(SysSpecialPermission::getSubjectId, AppContext.getSubjectId())
                .like(
                        StringUtils.hasText(req.getRightName()),
                        SysSpecialPermission::getName,
                        req.getRightName())
                .orderByAsc(SysSpecialPermission::getSort)
                .orderByAsc(SysSpecialPermission::getId);
        List<SysSpecialPermission> specialList =
                sysSpecialPermissionMapper.selectList(specialWrapper);
        for (SysSpecialPermission item : specialList) {
            SysRightResp resp = new SysRightResp();
            resp.setId(item.getId());
            resp.setPid(0L);
            resp.setRightName(item.getName());
            resp.setRightSlug(item.getCode());
            resp.setNodeType(3); // 特殊权限（其它权限）
            resp.setDescription(item.getDescription());
            result.add(resp);
        }

        return result;
    }

    /** 收集所有子节点ID */
    private Set<Long> collectChildrenIds(Long parentId, Long subjectId) {
        Set<Long> result = new java.util.HashSet<>();
        List<SysRight> children =
                sysRightMapper.selectList(
                        Wrappers.<SysRight>lambdaQuery()
                                .eq(SysRight::getPid, parentId)
                                .eq(SysRight::getSubjectId, subjectId)
                                .eq(SysRight::getProjectNo, AppContext.getProjectNo()));
        for (SysRight child : children) {
            result.add(child.getId());
            result.addAll(collectChildrenIds(child.getId(), subjectId));
        }
        return result;
    }
}
