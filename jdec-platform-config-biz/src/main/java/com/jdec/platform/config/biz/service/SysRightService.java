package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jdec.platform.config.api.SysRightApi;
import com.jdec.platform.config.api.dto.request.QuerySysRightReq;
import com.jdec.platform.config.api.dto.request.SysRightSaveReq;
import com.jdec.platform.config.api.dto.response.SysRightOptionResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import com.jdec.platform.config.biz.entity.SysRight;
import com.jdec.platform.config.biz.mapper.SysDataPermissionMapper;
import com.jdec.platform.config.biz.mapper.SysInteractionPermissionMapper;
import com.jdec.platform.config.biz.mapper.SysRightMapper;
import com.jdec.platform.config.biz.mapper.SysSpecialPermissionMapper;
import com.jdec.platform.shared.audit.annotation.DataAudit;
import com.jdec.platform.shared.audit.enums.OperationType;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.PageResult;
import com.jdec.platform.shared.utils.PageResultUtils;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 权限节点 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_ENGINE)
public class SysRightService implements SysRightApi {

    private final SysRightMapper sysRightMapper;
    private final SysInteractionPermissionMapper sysInteractionPermissionMapper;
    private final SysDataPermissionMapper sysDataPermissionTypeMapper;
    private final SysSpecialPermissionMapper sysSpecialPermissionMapper;

    @Override
    @DataAudit(
            module = "系统设置",
            subModule = "按钮配置",
            operation = OperationType.UPDATE,
            tableName = "sys_right",
            dataIdField = "#req.id")
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
            // 设置id到请求对象，用于审计切面重新解析dataId
            req.setId(entity.getId());
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
    @DataAudit(
            module = "系统设置",
            subModule = "按钮配置",
            operation = OperationType.DELETE,
            deleteDisplayField = "rightName",
            entityClass = SysRight.class,
            tableName = "sys_right",
            dataIdField = "#id")
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
    public PageResult<SysRightResp> listRight(QuerySysRightReq req) {
        // 使用自定义 SQL 实现三表联合分页查询
        Page<SysRightResp> page = new Page<>(req.getPageNum(), req.getPageSize());
        Page<SysRightResp> resultPage =
                sysRightMapper.selectRightPage(
                        page,
                        AppContext.getProjectNo(),
                        AppContext.getSubjectId(),
                        req.getRightName());

        return PageResultUtils.of(resultPage);
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
