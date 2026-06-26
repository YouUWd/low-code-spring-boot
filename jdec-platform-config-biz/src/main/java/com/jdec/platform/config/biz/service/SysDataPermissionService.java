package com.jdec.platform.config.biz.service;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysDataPermissionApi;
import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.SysDataPermissionMappingResp;
import com.jdec.platform.config.api.dto.response.SysDataPermissionOptionResp;
import com.jdec.platform.config.api.dto.response.SysDataPermissionResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import com.jdec.platform.config.biz.entity.SysDataPermission;
import com.jdec.platform.config.biz.entity.SysDataPermissionMapping;
import com.jdec.platform.config.biz.entity.SysRoleDataPermission;
import com.jdec.platform.config.biz.mapper.SysDataPermissionMapper;
import com.jdec.platform.config.biz.mapper.SysDataPermissionMappingMapper;
import com.jdec.platform.config.biz.mapper.SysRoleDataPermissionMapper;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.enums.CommonStatusEnum;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
public class SysDataPermissionService implements SysDataPermissionApi {

    private final SysDataPermissionMapper sysDataPermissionMapper;
    private final SysRoleDataPermissionMapper sysRoleDataPermissionMapper;
    private final SysDataPermissionMappingMapper sysDataPermissionMappingMapper;

    @Override
    public SysRightResp saveDataPermission(SysDataPermissionSaveReq req) {
        SysDataPermission entity;
        if (req.getId() == null) {
            // 校验编码唯一性
            boolean exists =
                    sysDataPermissionMapper.exists(
                            Wrappers.<SysDataPermission>lambdaQuery()
                                    .eq(SysDataPermission::getCode, req.getCode())
                                    .eq(SysDataPermission::getSubjectId, AppContext.getSubjectId())
                                    .eq(
                                            SysDataPermission::getProjectNo,
                                            AppContext.getProjectNo()));
            if (exists) {
                throw new BusinessException("类型编码已存在");
            }
            // 新增
            entity = new SysDataPermission();
            BeanUtils.copyProperties(req, entity);
            entity.setSubjectId(AppContext.getSubjectId());
            entity.setProjectNo(AppContext.getProjectNo());
            sysDataPermissionMapper.insert(entity);
        } else {
            entity = sysDataPermissionMapper.selectById(req.getId());
            if (entity == null) {
                throw new BusinessException("数据权限类型不存在");
            }
            // 校验编码唯一性（排除自己）
            if (!entity.getCode().equals(req.getCode())) {
                boolean exists =
                        sysDataPermissionMapper.exists(
                                Wrappers.<SysDataPermission>lambdaQuery()
                                        .eq(SysDataPermission::getCode, req.getCode())
                                        .ne(SysDataPermission::getId, req.getId())
                                        .eq(
                                                SysDataPermission::getSubjectId,
                                                AppContext.getSubjectId())
                                        .eq(
                                                SysDataPermission::getProjectNo,
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
            sysDataPermissionMapper.updateById(entity);
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
    public void deleteDataPermission(Long id, boolean force) {
        boolean exists =
                sysDataPermissionMapper.exists(
                        Wrappers.<SysDataPermission>lambdaQuery()
                                .eq(SysDataPermission::getId, id)
                                .eq(SysDataPermission::getSubjectId, AppContext.getSubjectId())
                                .eq(SysDataPermission::getProjectNo, AppContext.getProjectNo()));
        if (!exists) {
            throw new BusinessException("数据权限节点不存在");
        }
        // 查询角色关联数量
        long roleCount =
                sysRoleDataPermissionMapper.selectCount(
                        Wrappers.<SysRoleDataPermission>lambdaQuery()
                                .eq(SysRoleDataPermission::getDataPermissionId, id));
        if (!force && roleCount > 0) {
            throw new BusinessException(
                    "删除确认：该数据权限类型已被 " + roleCount + " 个角色授权引用，删除后相关授权将被同步清除，请确是否删除");
        }
        // 级联删除 sys_role_data_permission
        sysRoleDataPermissionMapper.delete(
                Wrappers.<SysRoleDataPermission>lambdaQuery()
                        .eq(SysRoleDataPermission::getDataPermissionId, id));
        sysDataPermissionMapper.deleteById(id);
    }

    @Override
    public List<SysDataPermissionOptionResp> listDataPermissionOptions() {
        List<SysDataPermission> list =
                sysDataPermissionMapper.selectList(
                        new LambdaQueryWrapper<SysDataPermission>()
                                .eq(SysDataPermission::getProjectNo, AppContext.getProjectNo())
                                .eq(SysDataPermission::getSubjectId, AppContext.getSubjectId())
                                .eq(SysDataPermission::getStatus, 1)
                                .orderByAsc(SysDataPermission::getSort)
                                .orderByAsc(SysDataPermission::getId));
        return list.stream()
                .map(
                        type -> {
                            SysDataPermissionOptionResp resp = new SysDataPermissionOptionResp();
                            resp.setId(type.getId());
                            resp.setName(type.getName());
                            resp.setCode(type.getCode());
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public List<SysDataPermissionResp> listDataPermission(QuerySysDataPermissionReq req) {
        List<SysDataPermission> list = new ArrayList<>();

        // 如果传入了 roleId，则查询该角色关联的权限节点
        if (req.getRoleId() != null) {
            // 1. 查询该角色关联的所有数据权限节点 ID
            List<SysRoleDataPermission> roleDataPermissions =
                    sysRoleDataPermissionMapper.selectList(
                            Wrappers.<SysRoleDataPermission>lambdaQuery()
                                    .eq(SysRoleDataPermission::getRoleId, req.getRoleId())
                                    .select(SysRoleDataPermission::getDataPermissionId));

            if (roleDataPermissions.isEmpty()) {
                return new ArrayList<>();
            }

            List<Long> permissionTypeIds =
                    roleDataPermissions.stream()
                            .map(SysRoleDataPermission::getDataPermissionId)
                            .distinct()
                            .collect(Collectors.toList());

            if (CollUtil.isNotEmpty(permissionTypeIds)) {
                // 2. 根据权限类型 ID 批量查询权限类型信息
                list =
                        sysDataPermissionMapper.selectList(
                                new LambdaQueryWrapper<SysDataPermission>()
                                        .in(SysDataPermission::getId, permissionTypeIds)
                                        .eq(
                                                SysDataPermission::getProjectNo,
                                                AppContext.getProjectNo())
                                        .eq(
                                                SysDataPermission::getSubjectId,
                                                AppContext.getSubjectId())
                                        .like(
                                                StringUtils.hasText(req.getName()),
                                                SysDataPermission::getName,
                                                req.getName())
                                        .like(
                                                StringUtils.hasText(req.getCode()),
                                                SysDataPermission::getCode,
                                                req.getCode())
                                        .eq(
                                                SysDataPermission::getStatus,
                                                CommonStatusEnum.ENABLE.getCode())
                                        .orderByAsc(SysDataPermission::getSort)
                                        .orderByAsc(SysDataPermission::getId));
            }
        } else {
            // 没有传 roleId，查询所有权限类型
            list =
                    sysDataPermissionMapper.selectList(
                            new LambdaQueryWrapper<SysDataPermission>()
                                    .eq(SysDataPermission::getProjectNo, AppContext.getProjectNo())
                                    .eq(SysDataPermission::getSubjectId, AppContext.getSubjectId())
                                    .like(
                                            StringUtils.hasText(req.getName()),
                                            SysDataPermission::getName,
                                            req.getName())
                                    .like(
                                            StringUtils.hasText(req.getCode()),
                                            SysDataPermission::getCode,
                                            req.getCode())
                                    .eq(
                                            SysDataPermission::getStatus,
                                            CommonStatusEnum.ENABLE.getCode())
                                    .orderByAsc(SysDataPermission::getSort)
                                    .orderByAsc(SysDataPermission::getId));
        }

        if (list.isEmpty()) {
            return new ArrayList<>();
        }

        // 3. 收集所有权限节点的 dataPermissionCode（转换为 String）
        List<String> dataCodes =
                list.stream()
                        .map(SysDataPermission::getCode)
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .distinct()
                        .collect(Collectors.toList());

        // 4. 根据 bizTypeCode 批量查询数据权限
        List<SysDataPermissionMapping> allDataPermissions = new ArrayList<>();
        if (!dataCodes.isEmpty()) {
            allDataPermissions =
                    sysDataPermissionMappingMapper.selectList(
                            Wrappers.<SysDataPermissionMapping>lambdaQuery()
                                    .in(
                                            SysDataPermissionMapping::getDataPermissionCode,
                                            dataCodes));
        }

        // 5. 按 Code 分组数据权限
        Map<String, List<SysDataPermissionMapping>> dataPermissionMappingMap =
                allDataPermissions.stream()
                        .collect(
                                Collectors.groupingBy(
                                        SysDataPermissionMapping::getDataPermissionCode));

        // 6. 构建响应，填充数据权限列表
        return list.stream()
                .map(
                        type -> {
                            SysDataPermissionResp resp = new SysDataPermissionResp();
                            resp.setId(type.getId());
                            resp.setCode(type.getCode());
                            resp.setName(type.getName());
                            resp.setDescription(type.getDescription());
                            resp.setSort(type.getSort());
                            resp.setStatus(type.getStatus());

                            // 根据 bizTypeCode 填充关联的数据权限
                            if (type.getCode() != null) {
                                String bizTypeCodeStr = type.getCode();
                                List<SysDataPermissionMapping> permissionMappings =
                                        dataPermissionMappingMap.getOrDefault(
                                                bizTypeCodeStr, new ArrayList<>());
                                List<SysDataPermissionMappingResp> dataPermissions =
                                        permissionMappings.stream()
                                                .map(
                                                        dp -> {
                                                            SysDataPermissionMappingResp dpResp =
                                                                    new SysDataPermissionMappingResp();
                                                            dpResp.setId(dp.getId());
                                                            dpResp.setDataPermissionCode(
                                                                    dp.getDataPermissionCode());
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
    public SysDataPermissionResp getDataPermission(Long id) {
        SysDataPermission entity =
                sysDataPermissionMapper.selectOne(
                        Wrappers.<SysDataPermission>lambdaQuery()
                                .eq(SysDataPermission::getId, id)
                                .eq(SysDataPermission::getSubjectId, AppContext.getSubjectId())
                                .eq(SysDataPermission::getProjectNo, AppContext.getProjectNo()));
        if (entity == null) {
            throw new BusinessException("数据权限类型不存在");
        }
        SysDataPermissionResp resp = new SysDataPermissionResp();
        resp.setId(entity.getId());
        resp.setCode(entity.getCode());
        resp.setName(entity.getName());
        resp.setDescription(entity.getDescription());
        resp.setSort(entity.getSort());
        resp.setStatus(entity.getStatus());
        return resp;
    }
}
