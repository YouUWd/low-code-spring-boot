package com.jdec.platform.config.biz.service;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysRightApi;
import com.jdec.platform.config.api.dto.request.QuerySysRightReq;
import com.jdec.platform.config.api.dto.request.SysRightSaveReq;
import com.jdec.platform.config.api.dto.response.SysRightOptionResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import com.jdec.platform.config.biz.entity.SysRight;
import com.jdec.platform.config.biz.mapper.SysRightMapper;
import com.jdec.platform.shared.context.AppCodeContext;
import com.jdec.platform.shared.context.SubjectContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限节点 Service 实现
 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysRightService implements SysRightApi {

    private final SysRightMapper sysRightMapper;

    @Override
    public void saveRight(SysRightSaveReq req) {
        if (req.getId() == null) {
            // 新增
            SysRight entity = new SysRight();
            BeanUtils.copyProperties(req, entity);
            if (entity.getPid() == null) {
                entity.setPid(0L);
            }
            entity.setSubjectId(SubjectContext.get());
            entity.setAppCode(AppCodeContext.get());
            sysRightMapper.insert(entity);
        } else {
            // 编辑
            SysRight entity = sysRightMapper.selectOne(
                    Wrappers.<SysRight>lambdaQuery()
                            .eq(SysRight::getId, req.getId())
                            .eq(SysRight::getSubjectId, SubjectContext.get())
                            .eq(SysRight::getAppCode, AppCodeContext.get())
            );
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
        boolean exists = sysRightMapper.exists(
                Wrappers.<SysRight>lambdaQuery()
                        .eq(SysRight::getId, id)
                        .eq(SysRight::getSubjectId, SubjectContext.get())
                        .eq(SysRight::getAppCode, AppCodeContext.get())
        );
        if (!exists) {
            throw new BusinessException("权限节点不存在");
        }
        // 级联删除所有子节点
        Set<Long> ids = collectChildrenIds(id, SubjectContext.get());
        ids.add(id);
        sysRightMapper.deleteByIds(ids);
    }

    @Override
    public List<SysRightOptionResp> listParentOptions(Integer nodeType) {
        List<SysRight> list = sysRightMapper.selectList(
                new LambdaQueryWrapper<SysRight>()
                        .eq(SysRight::getAppCode, AppCodeContext.get())
                        .eq(SysRight::getPid, 0L)
                        .eq(nodeType != null, SysRight::getNodeType, nodeType)
                        .orderByAsc(SysRight::getId)
        );
        return list.stream().map(right -> {
            SysRightOptionResp resp = new SysRightOptionResp();
            resp.setId(right.getId());
            resp.setRightName(right.getRightName());
            resp.setRightSlug(right.getRightSlug());
            return resp;
        }).collect(Collectors.toList());
    }

    @Override
    public List<SysRightResp> listRight(QuerySysRightReq req) {
        List<SysRight> list = sysRightMapper.selectList(
                new LambdaQueryWrapper<SysRight>()
                        .eq(SysRight::getAppCode, AppCodeContext.get())
                        .eq(SysRight::getSubjectId, SubjectContext.get())
                        .like(StringUtils.hasText(req.getRightName()), SysRight::getRightName, req.getRightName())
                        .orderByAsc(SysRight::getId)
        );
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        // 查询所有父节点
        Set<Long> parentIds = list.stream()
                .map(SysRight::getPid)
                .filter(pid -> pid != null && pid != 0L)
                .collect(Collectors.toSet());
        Map<Long, SysRight> parentMap = new HashMap<>();
        if (CollUtil.isNotEmpty(parentIds)) {
            parentMap = sysRightMapper.selectByIds(parentIds).stream()
                    .collect(Collectors.toMap(SysRight::getId, r -> r));
        }
        Map<Long, SysRight> finalParentMap = parentMap;
        return list.stream().map(right -> {
            SysRightResp resp = new SysRightResp();
            resp.setId(right.getId());
            resp.setPid(right.getPid());
            resp.setRightName(right.getRightName());
            resp.setRightSlug(right.getRightSlug());
            resp.setNodeType(right.getNodeType());
            resp.setDescription(right.getDescription());
            if (right.getPid() != null && right.getPid() != 0L) {
                SysRight parent = finalParentMap.get(right.getPid());
                if (parent != null) {
                    resp.setParentRightName(parent.getRightName());
                    resp.setParentRightSlug(parent.getRightSlug());
                } else {
                    resp.setParentRightName("");
                    resp.setParentRightSlug("");
                }
            }
            return resp;
        }).collect(Collectors.toList());
    }

    /**
     * 收集所有子节点ID
     */
    private Set<Long> collectChildrenIds(Long parentId, Long subjectId) {
        Set<Long> result = new java.util.HashSet<>();
        List<SysRight> children = sysRightMapper.selectList(
                Wrappers.<SysRight>lambdaQuery()
                        .eq(SysRight::getPid, parentId)
                        .eq(SysRight::getSubjectId, subjectId)
                        .eq(SysRight::getAppCode, AppCodeContext.get())
        );
        for (SysRight child : children) {
            result.add(child.getId());
            result.addAll(collectChildrenIds(child.getId(), subjectId));
        }
        return result;
    }
}
