package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysDataPermissionTypeApi;
import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionTypeReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionTypeSaveReq;
import com.jdec.platform.config.api.dto.response.SysDataPermissionResp;
import com.jdec.platform.config.api.dto.response.SysDataPermissionTypeOptionResp;
import com.jdec.platform.config.api.dto.response.SysDataPermissionTypeResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import com.jdec.platform.config.biz.entity.SysDataPermission;
import com.jdec.platform.config.biz.entity.SysDataPermissionType;
import com.jdec.platform.config.biz.entity.SysRoleDataPermission;
import com.jdec.platform.config.biz.mapper.SysDataPermissionMapper;
import com.jdec.platform.config.biz.mapper.SysDataPermissionTypeMapper;
import com.jdec.platform.config.biz.mapper.SysRoleDataPermissionMapper;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.enums.CommonStatusEnum;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final SysDataPermissionMapper sysDataPermissionMapper;

    @Override
    public SysRightResp saveDataPermissionType(SysDataPermissionTypeSaveReq req) {
        SysDataPermissionType entity;
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
            entity = new SysDataPermissionType();
            BeanUtils.copyProperties(req, entity);
            entity.setSubjectId(AppContext.getSubjectId());
            entity.setProjectNo(AppContext.getProjectNo());
            sysDataPermissionTypeMapper.insert(entity);
        } else {
            entity = sysDataPermissionTypeMapper.selectById(req.getId());
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

        // 构建返回对象
        SysRightResp resp = new SysRightResp();
        resp.setId(entity.getId());
        resp.setRightName(entity.getName());
        resp.setRightSlug(entity.getCode());
        resp.setNodeType(1);
        resp.setDescription(entity.getDescription());
        resp.setParentRightName(null);
        resp.setParentRightSlug(null);
        resp.setType(null);
        return resp;
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
                            + " 个角色授权引用，删除后相关授权将被同步清除，请确认后携带 force=true 重新请求");
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
        List<SysDataPermissionType> list;

        // 如果传入了 roleId，则查询该角色关联的权限类型
        if (req.getRoleId() != null) {
            // 1. 查询该角色关联的所有数据权限类型 ID
            List<SysRoleDataPermission> roleDataPermissions =
                    sysRoleDataPermissionMapper.selectList(
                            Wrappers.<SysRoleDataPermission>lambdaQuery()
                                    .eq(SysRoleDataPermission::getRoleId, req.getRoleId())
                                    .select(SysRoleDataPermission::getDataPermissionTypeId));

            if (roleDataPermissions.isEmpty()) {
                return new ArrayList<>();
            }

            List<Long> permissionTypeIds =
                    roleDataPermissions.stream()
                            .map(SysRoleDataPermission::getDataPermissionTypeId)
                            .distinct()
                            .collect(Collectors.toList());

            // 2. 根据权限类型 ID 批量查询权限类型信息
            list =
                    sysDataPermissionTypeMapper.selectList(
                            new LambdaQueryWrapper<SysDataPermissionType>()
                                    .in(SysDataPermissionType::getId, permissionTypeIds)
                                    .eq(
                                            SysDataPermissionType::getProjectNo,
                                            AppContext.getProjectNo())
                                    .eq(
                                            SysDataPermissionType::getSubjectId,
                                            AppContext.getSubjectId())
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
        } else {
            // 没有传 roleId，查询所有权限类型
            list =
                    sysDataPermissionTypeMapper.selectList(
                            new LambdaQueryWrapper<SysDataPermissionType>()
                                    .eq(
                                            SysDataPermissionType::getProjectNo,
                                            AppContext.getProjectNo())
                                    .eq(
                                            SysDataPermissionType::getSubjectId,
                                            AppContext.getSubjectId())
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
        }

        if (list.isEmpty()) {
            return new ArrayList<>();
        }

        // 3. 收集所有权限类型的 bizTypeCode（转换为 String）
        List<String> bizTypeCodes =
                list.stream()
                        .map(SysDataPermissionType::getBizTypeCode)
                        .filter(bizTypeCode -> bizTypeCode != null)
                        .map(String::valueOf)
                        .distinct()
                        .collect(Collectors.toList());

        // 4. 根据 bizTypeCode 批量查询数据权限
        List<SysDataPermission> allDataPermissions = new ArrayList<>();
        if (!bizTypeCodes.isEmpty()) {
            allDataPermissions =
                    sysDataPermissionMapper.selectList(
                            Wrappers.<SysDataPermission>lambdaQuery()
                                    .in(SysDataPermission::getBizTypeCode, bizTypeCodes));
        }

        // 5. 按 bizTypeCode 分组数据权限
        Map<String, List<SysDataPermission>> dataPermissionMap =
                allDataPermissions.stream()
                        .collect(Collectors.groupingBy(SysDataPermission::getBizTypeCode));

        // 6. 构建响应，填充数据权限列表
        return list.stream()
                .map(
                        type -> {
                            SysDataPermissionTypeResp resp = new SysDataPermissionTypeResp();
                            resp.setId(type.getId());
                            resp.setCode(type.getCode());
                            resp.setName(type.getName());
                            resp.setDescription(type.getDescription());
                            resp.setSort(type.getSort());
                            resp.setStatus(type.getStatus());

                            // 根据 bizTypeCode 填充关联的数据权限
                            if (type.getBizTypeCode() != null) {
                                String bizTypeCodeStr = String.valueOf(type.getBizTypeCode());
                                List<SysDataPermission> permissions =
                                        dataPermissionMap.getOrDefault(
                                                bizTypeCodeStr, new ArrayList<>());
                                List<SysDataPermissionResp> dataPermissions =
                                        permissions.stream()
                                                .map(
                                                        dp -> {
                                                            SysDataPermissionResp dpResp =
                                                                    new SysDataPermissionResp();
                                                            dpResp.setId(dp.getId());
                                                            dpResp.setBizTypeCode(
                                                                    dp.getBizTypeCode());
                                                            dpResp.setBizTypeName(
                                                                    dp.getBizTypeName());
                                                            dpResp.setBizId(dp.getBizId());
                                                            dpResp.setBizName(dp.getBizName());
                                                            return dpResp;
                                                        })
                                                .collect(Collectors.toList());
                                resp.setDataPermissions(dataPermissions);
                            }
                            return resp;
                        })
                .collect(Collectors.toList());
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
        resp.setSort(entity.getSort());
        resp.setStatus(entity.getStatus());
        return resp;
    }
}
