package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysDataPermissionTypeApi;
import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionTypeReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionTypeSaveReq;
import com.jdec.platform.config.api.dto.response.DataPermissionUserResp;
import com.jdec.platform.config.api.dto.response.SysDataPermissionTypeOptionResp;
import com.jdec.platform.config.api.dto.response.SysDataPermissionTypeResp;
import com.jdec.platform.config.biz.entity.SysDataPermissionTarget;
import com.jdec.platform.config.biz.entity.SysDataPermissionType;
import com.jdec.platform.config.biz.entity.SysRoleDataPermission;
import com.jdec.platform.config.biz.mapper.SysDataPermissionTargetMapper;
import com.jdec.platform.config.biz.mapper.SysDataPermissionTypeMapper;
import com.jdec.platform.config.biz.mapper.SysRoleDataPermissionMapper;
import com.jdec.platform.hr.api.UserApi;
import com.jdec.platform.hr.api.bo.UserBO;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.enums.CommonStatusEnum;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 数据权限类型 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysDataPermissionTypeService implements SysDataPermissionTypeApi {

    private final SysDataPermissionTypeMapper sysDataPermissionTypeMapper;
    private final SysRoleDataPermissionMapper sysRoleDataPermissionMapper;
    private final SysDataPermissionTargetMapper sysDataPermissionTargetMapper;
    private final UserApi userApi;

    @Override
    public void saveDataPermissionType(SysDataPermissionTypeSaveReq req) {
        if (req.getId() == null) {
            // 校验编码唯一性
            boolean exists =
                    sysDataPermissionTypeMapper.exists(
                            Wrappers.<SysDataPermissionType>lambdaQuery()
                                    .eq(SysDataPermissionType::getCode, req.getCode())
                                    .eq(
                                            SysDataPermissionType::getSubjectId,
                                            AppContext.getSubjectId())
                                    .eq(
                                            SysDataPermissionType::getProjectNo,
                                            AppContext.getProjectNo()));
            if (exists) {
                throw new BusinessException("类型编码已存在");
            }
            // 新增
            SysDataPermissionType entity = new SysDataPermissionType();
            BeanUtils.copyProperties(req, entity);
            entity.setSubjectId(AppContext.getSubjectId());
            entity.setProjectNo(AppContext.getProjectNo());
            entity.setTargetType("user");
            sysDataPermissionTypeMapper.insert(entity);
        } else {
            SysDataPermissionType entity = sysDataPermissionTypeMapper.selectById(req.getId());
            if (entity == null) {
                throw new BusinessException("数据权限类型不存在");
            }
            // 校验编码唯一性（排除自己）
            if (!entity.getCode().equals(req.getCode())) {
                boolean exists =
                        sysDataPermissionTypeMapper.exists(
                                Wrappers.<SysDataPermissionType>lambdaQuery()
                                        .eq(SysDataPermissionType::getCode, req.getCode())
                                        .ne(SysDataPermissionType::getId, req.getId())
                                        .eq(
                                                SysDataPermissionType::getSubjectId,
                                                AppContext.getSubjectId())
                                        .eq(
                                                SysDataPermissionType::getProjectNo,
                                                AppContext.getProjectNo()));
                if (exists) {
                    throw new BusinessException("类型编码已存在");
                }
            }
            entity.setCode(req.getCode());
            entity.setName(req.getName());
            entity.setDescription(req.getDescription());
            entity.setSort(req.getSort());
            entity.setStatus(req.getStatus());
            sysDataPermissionTypeMapper.updateById(entity);
        }
    }

    @Transactional
    @Override
    public void deleteDataPermissionType(Long id, boolean force) {
        boolean exists =
                sysDataPermissionTypeMapper.exists(
                        Wrappers.<SysDataPermissionType>lambdaQuery()
                                .eq(SysDataPermissionType::getId, id)
                                .eq(SysDataPermissionType::getSubjectId, AppContext.getSubjectId())
                                .eq(
                                        SysDataPermissionType::getProjectNo,
                                        AppContext.getProjectNo()));
        if (!exists) {
            throw new BusinessException("数据权限类型不存在");
        }
        // 查询角色关联数量
        long roleCount =
                sysRoleDataPermissionMapper.selectCount(
                        Wrappers.<SysRoleDataPermission>lambdaQuery()
                                .eq(SysRoleDataPermission::getDataPermissionTypeId, id));
        if (!force && roleCount > 0) {
            throw new BusinessException(
                    "删除确认：该数据权限类型已被 "
                            + roleCount
                            + " 个角色授权引用，删除后相关授权及其数据权限目标将被同步清除，请确认后携带 force=true 重新请求");
        }
        // 查出所有关联的 role_data_permission id
        List<Long> roleDataPermissionIds =
                sysRoleDataPermissionMapper
                        .selectList(
                                Wrappers.<SysRoleDataPermission>lambdaQuery()
                                        .eq(SysRoleDataPermission::getDataPermissionTypeId, id)
                                        .select(SysRoleDataPermission::getId))
                        .stream()
                        .map(SysRoleDataPermission::getId)
                        .collect(Collectors.toList());
        // 级联删除 sys_data_permission_target
        if (!roleDataPermissionIds.isEmpty()) {
            sysDataPermissionTargetMapper.delete(
                    Wrappers.<SysDataPermissionTarget>lambdaQuery()
                            .in(
                                    SysDataPermissionTarget::getRoleDataPermissionId,
                                    roleDataPermissionIds));
        }
        // 级联删除 sys_role_data_permission
        sysRoleDataPermissionMapper.delete(
                Wrappers.<SysRoleDataPermission>lambdaQuery()
                        .eq(SysRoleDataPermission::getDataPermissionTypeId, id));
        sysDataPermissionTypeMapper.deleteById(id);
    }

    @Override
    public List<SysDataPermissionTypeOptionResp> listDataPermissionTypeOptions() {
        List<SysDataPermissionType> list =
                sysDataPermissionTypeMapper.selectList(
                        new LambdaQueryWrapper<SysDataPermissionType>()
                                .eq(SysDataPermissionType::getProjectNo, AppContext.getProjectNo())
                                .eq(SysDataPermissionType::getSubjectId, AppContext.getSubjectId())
                                .eq(SysDataPermissionType::getStatus, 1)
                                .orderByAsc(SysDataPermissionType::getSort)
                                .orderByAsc(SysDataPermissionType::getId));
        return list.stream()
                .map(
                        type -> {
                            SysDataPermissionTypeOptionResp resp =
                                    new SysDataPermissionTypeOptionResp();
                            resp.setId(type.getId());
                            resp.setName(type.getName());
                            resp.setCode(type.getCode());
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public List<SysDataPermissionTypeResp> listDataPermissionType(
            QuerySysDataPermissionTypeReq req) {
        List<SysDataPermissionType> list =
                sysDataPermissionTypeMapper.selectList(
                        new LambdaQueryWrapper<SysDataPermissionType>()
                                .eq(SysDataPermissionType::getProjectNo, AppContext.getProjectNo())
                                .eq(SysDataPermissionType::getSubjectId, AppContext.getSubjectId())
                                .like(
                                        StringUtils.hasText(req.getName()),
                                        SysDataPermissionType::getName,
                                        req.getName())
                                .like(
                                        StringUtils.hasText(req.getCode()),
                                        SysDataPermissionType::getCode,
                                        req.getCode())
                                .eq(
                                        SysDataPermissionType::getStatus,
                                        CommonStatusEnum.ENABLE.getCode())
                                .orderByAsc(SysDataPermissionType::getSort)
                                .orderByAsc(SysDataPermissionType::getId));
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        // todo: 有了登录以后，替换成登录用户的主体id
        Long subjectId = 1L;
        return list.stream()
                .map(
                        type -> {
                            SysDataPermissionTypeResp resp = new SysDataPermissionTypeResp();
                            resp.setId(type.getId());
                            resp.setCode(type.getCode());
                            resp.setName(type.getName());
                            resp.setDescription(type.getDescription());
                            resp.setHasTarget(type.getHasTarget());
                            resp.setTargetType(type.getTargetType());
                            resp.setSort(type.getSort());
                            resp.setStatus(type.getStatus());
                            // hasTarget为1时，查询同一主体的用户列表
                            if (type.getHasTarget() != null && type.getHasTarget() == 1) {
                                List<UserBO> userList = userApi.listBySubjectId(subjectId);
                                List<DataPermissionUserResp> children =
                                        userList.stream()
                                                .map(this::toDataPermissionUserResp)
                                                .collect(Collectors.toList());
                                resp.setChildren(children);
                            }
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    private DataPermissionUserResp toDataPermissionUserResp(UserBO user) {
        DataPermissionUserResp resp = new DataPermissionUserResp();
        resp.setId(user.getId() != null ? user.getId() : null);
        resp.setUserName(user.getUserName());
        resp.setWorkNumber(user.getWorkNumber());
        resp.setPhone(user.getPhone());
        resp.setDepartmentName(user.getDepartmentName());
        resp.setShortName(user.getShortName());
        return resp;
    }

    @Override
    public SysDataPermissionTypeResp getDataPermissionType(Long id) {
        SysDataPermissionType entity =
                sysDataPermissionTypeMapper.selectOne(
                        Wrappers.<SysDataPermissionType>lambdaQuery()
                                .eq(SysDataPermissionType::getId, id)
                                .eq(SysDataPermissionType::getSubjectId, AppContext.getSubjectId())
                                .eq(
                                        SysDataPermissionType::getProjectNo,
                                        AppContext.getProjectNo()));
        if (entity == null) {
            throw new BusinessException("数据权限类型不存在");
        }
        SysDataPermissionTypeResp resp = new SysDataPermissionTypeResp();
        resp.setId(entity.getId());
        resp.setCode(entity.getCode());
        resp.setName(entity.getName());
        resp.setDescription(entity.getDescription());
        resp.setHasTarget(entity.getHasTarget());
        resp.setTargetType(entity.getTargetType());
        resp.setSort(entity.getSort());
        resp.setStatus(entity.getStatus());
        return resp;
    }
}
