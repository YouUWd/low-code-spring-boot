package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysStatusApi;
import com.jdec.platform.config.api.dto.request.SysStatusDragReq;
import com.jdec.platform.config.api.dto.request.SysStatusSaveReq;
import com.jdec.platform.config.api.dto.response.SysStatusTreeResp;
import com.jdec.platform.config.biz.entity.SysStatus;
import com.jdec.platform.config.biz.mapper.SysStatusMapper;
import com.jdec.platform.shared.context.AppCodeContext;
import com.jdec.platform.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * 状态 Service 实现
 */
@Service
@RequiredArgsConstructor
public class SysStatusService implements SysStatusApi {

    private final SysStatusMapper sysStatusMapper;

    @Override
    public List<SysStatusTreeResp> tree(Long subjectId) {
        if (subjectId == null) {
            subjectId = 0L;
        }
        List<SysStatus> list = sysStatusMapper.selectList(
                new LambdaQueryWrapper<SysStatus>()
                        .eq(SysStatus::getAppCode, AppCodeContext.get())
                        .eq(SysStatus::getSubjectId, subjectId)
                        .orderByAsc(SysStatus::getStatusValue)
                        .orderByAsc(SysStatus::getId)
        );
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        // 构建映射
        Map<Long, SysStatusTreeResp> nodeMap = new LinkedHashMap<>();
        for (SysStatus status : list) {
            SysStatusTreeResp resp = new SysStatusTreeResp();
            BeanUtils.copyProperties(status, resp);
            resp.setChildren(new ArrayList<>());
            nodeMap.put(status.getId(), resp);
        }
        // 组装树
        List<SysStatusTreeResp> tree = new ArrayList<>();
        for (SysStatus status : list) {
            SysStatusTreeResp node = nodeMap.get(status.getId());
            Long pid = status.getPid();
            if (pid == null || pid == 0L) {
                tree.add(node);
            } else {
                SysStatusTreeResp parent = nodeMap.get(pid);
                if (parent != null && parent.getChildren() != null) {
                    parent.getChildren().add(node);
                } else {
                    // 父节点不存在或已删除，挂到顶层
                    tree.add(node);
                }
            }
        }
        return tree;
    }

    @Override
    public void saveStatus(SysStatusSaveReq req) {
        Long subjectId = req.getSubjectId() != null ? req.getSubjectId() : 0L;
        if (req.getId() == null) {
            // 新增
            SysStatus entity = new SysStatus();
            BeanUtils.copyProperties(req, entity);
            if (entity.getPid() == null) {
                entity.setPid(0L);
            }
            entity.setSubjectId(subjectId);
            entity.setAppCode(AppCodeContext.get());
            // 校验同级名称是否重复（按主体、应用隔离）
            long count = sysStatusMapper.selectCount(
                    new LambdaQueryWrapper<SysStatus>()
                            .eq(SysStatus::getPid, entity.getPid())
                            .eq(SysStatus::getSubjectId, subjectId)
                            .eq(SysStatus::getAppCode, AppCodeContext.get())
                            .eq(SysStatus::getTitle, entity.getTitle())
            );
            if (count > 0) {
                throw new BusinessException("同级状态下名称已存在");
            }
            sysStatusMapper.insert(entity);
        } else {
            // 编辑
            boolean exists = sysStatusMapper.exists(
                    Wrappers.<SysStatus>lambdaQuery()
                            .eq(SysStatus::getId, req.getId())
                            .eq(SysStatus::getSubjectId, subjectId)
                            .eq(SysStatus::getAppCode, AppCodeContext.get())
            );
            if (!exists) {
                throw new BusinessException("状态不存在");
            }
            SysStatus entity = new SysStatus();
            BeanUtils.copyProperties(req, entity);
            entity.setId(req.getId());
            entity.setSubjectId(subjectId);
            // 不能把自己设为自己的父节点
            if (req.getId().equals(req.getPid())) {
                throw new BusinessException("不能将自己设为父状态");
            }
            // 校验是否形成了环（不能把自己子节点设为父节点）
            if (req.getPid() != null && req.getPid() != 0L) {
                checkCycle(req.getId(), req.getPid(), subjectId);
                // 校验同级名称是否重复（按主体、应用隔离）
                Long count = sysStatusMapper.selectCount(
                        Wrappers.<SysStatus>lambdaQuery()
                                .eq(SysStatus::getPid, req.getPid())
                                .eq(SysStatus::getSubjectId, subjectId)
                                .eq(SysStatus::getAppCode, AppCodeContext.get())
                                .eq(SysStatus::getTitle, req.getTitle())
                                .ne(SysStatus::getId, req.getId())
                );
                if (count > 0) {
                    throw new BusinessException("同级状态下名称已存在");
                }
            }
            sysStatusMapper.updateById(entity);
        }
    }

    @Transactional
    @Override
    public void deleteStatus(Long id, Long subjectId) {
        if (subjectId == null) {
            subjectId = 0L;
        }
        boolean exists = sysStatusMapper.exists(
                Wrappers.<SysStatus>lambdaQuery()
                        .eq(SysStatus::getId, id)
                        .eq(SysStatus::getSubjectId, subjectId)
                        .eq(SysStatus::getAppCode, AppCodeContext.get())
        );
        if (!exists) {
            throw new BusinessException("状态不存在");
        }
        // 级联删除所有子节点
        Set<Long> ids = collectChildrenIds(id, subjectId);
        ids.add(id);
        sysStatusMapper.deleteByIds(ids);
    }

    @Override
    public void dragStatus(SysStatusDragReq req) {
        Long id = req.getId();
        Long targetPid = req.getTargetPid();
        Long subjectId = req.getSubjectId() != null ? req.getSubjectId() : 0L;
        if (id == null) {
            throw new BusinessException("节点ID不能为空");
        }
        SysStatus node = sysStatusMapper.selectOne(
                Wrappers.<SysStatus>lambdaQuery()
                        .eq(SysStatus::getId, id)
                        .eq(SysStatus::getSubjectId, subjectId)
                        .eq(SysStatus::getAppCode, AppCodeContext.get())
        );
        if (node == null) {
            throw new BusinessException("状态不存在");
        }
        if (targetPid == null) {
            targetPid = 0L;
        }
        // 不能拖到自己下面
        if (id.equals(targetPid)) {
            throw new BusinessException("不能将自己设为自己的父节点");
        }
        // 不能拖到自己的子节点下面（形成环）
        if (targetPid != 0L) {
            checkCycle(id, targetPid, subjectId);
            boolean exists = sysStatusMapper.exists(
                    Wrappers.<SysStatus>lambdaQuery()
                            .eq(SysStatus::getId, targetPid)
                            .eq(SysStatus::getSubjectId, subjectId)
                            .eq(SysStatus::getAppCode, AppCodeContext.get())
            );
            if (!exists) {
                throw new BusinessException("目标父状态不存在");
            }
        }
        // 校验同级名称是否重复
        Long count = sysStatusMapper.selectCount(
                Wrappers.<SysStatus>lambdaQuery()
                        .eq(SysStatus::getPid, targetPid)
                        .eq(SysStatus::getSubjectId, subjectId)
                        .eq(SysStatus::getAppCode, AppCodeContext.get())
                        .eq(SysStatus::getTitle, node.getTitle())
                        .ne(SysStatus::getId, id)
        );
        if (count > 0) {
            throw new BusinessException("同级状态下名称已存在");
        }
        node.setPid(targetPid);
        sysStatusMapper.updateById(node);
    }

    /**
     * 收集所有子节点ID
     */
    private Set<Long> collectChildrenIds(Long parentId, Long subjectId) {
        Set<Long> result = new HashSet<>();
        List<SysStatus> children = sysStatusMapper.selectList(
                Wrappers.<SysStatus>lambdaQuery()
                        .eq(SysStatus::getPid, parentId)
                        .eq(SysStatus::getSubjectId, subjectId)
                        .eq(SysStatus::getAppCode, AppCodeContext.get())
        );
        for (SysStatus child : children) {
            result.add(child.getId());
            result.addAll(collectChildrenIds(child.getId(), subjectId));
        }
        return result;
    }

    /**
     * 检查是否形成环：targetPid 不能是 id 的子节点（包括自身）
     */
    private void checkCycle(Long id, Long targetPid, Long subjectId) {
        if (id.equals(targetPid)) {
            throw new BusinessException("不能将节点移动到自身或其子节点下");
        }
        List<SysStatus> children = sysStatusMapper.selectList(
                Wrappers.<SysStatus>lambdaQuery()
                        .eq(SysStatus::getPid, id)
                        .eq(SysStatus::getSubjectId, subjectId)
                        .eq(SysStatus::getAppCode, AppCodeContext.get())
        );
        for (SysStatus child : children) {
            checkCycle(child.getId(), targetPid, subjectId);
        }
    }
}
