package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysSpecialPermissionApi;
import com.jdec.platform.config.api.dto.request.CheckUserSpecialPermissionReq;
import com.jdec.platform.config.api.dto.request.QuerySysSpecialPermissionReq;
import com.jdec.platform.config.api.dto.request.SysSpecialPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.CheckUserSpecialPermissionResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import com.jdec.platform.config.api.dto.response.SysSpecialPermissionOptionResp;
import com.jdec.platform.config.api.dto.response.SysSpecialPermissionResp;
import com.jdec.platform.config.biz.check.CheckConstant;
import com.jdec.platform.config.biz.check.ReferenceCheckManager;
import com.jdec.platform.config.biz.check.ReferenceContext;
import com.jdec.platform.config.biz.entity.SysRoleSpecialPermission;
import com.jdec.platform.config.biz.entity.SysRoleSubject;
import com.jdec.platform.config.biz.entity.SysRoleUser;
import com.jdec.platform.config.biz.entity.SysSpecialPermission;
import com.jdec.platform.config.biz.mapper.SysRoleSpecialPermissionMapper;
import com.jdec.platform.config.biz.mapper.SysRoleSubjectMapper;
import com.jdec.platform.config.biz.mapper.SysRoleUserMapper;
import com.jdec.platform.config.biz.mapper.SysSpecialPermissionMapper;
import com.jdec.platform.shared.audit.annotation.DataAudit;
import com.jdec.platform.shared.audit.enums.OperationType;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.exception.PopException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 特殊权限 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_ENGINE)
public class SysSpecialPermissionService implements SysSpecialPermissionApi {

    private final SysSpecialPermissionMapper sysSpecialPermissionMapper;
    private final SysRoleSpecialPermissionMapper sysRoleSpecialPermissionMapper;
    private final SysRoleUserMapper sysRoleUserMapper;
    private final SysRoleSubjectMapper sysRoleSubjectMapper;
    private final ReferenceCheckManager referenceCheckManager;

    @Transactional
    @Override
    @DataAudit(
            module = "系统设置",
            subModule = "其它权限",
            operation = OperationType.UPDATE,
            tableName = "sys_special_permission",
            dataIdField = "#req.id")
    public SysRightResp saveSpecialPermission(SysSpecialPermissionSaveReq req) {
        // 保存前先查询旧数据（用于审计）
        SysSpecialPermission oldEntity = null;
        if (req.getId() != null) {
            oldEntity =
                    sysSpecialPermissionMapper.selectOne(
                            Wrappers.<SysSpecialPermission>lambdaQuery()
                                    .eq(SysSpecialPermission::getId, req.getId())
                                    .eq(
                                            SysSpecialPermission::getSubjectId,
                                            AppContext.getSubjectId())
                                    .eq(
                                            SysSpecialPermission::getProjectNo,
                                            AppContext.getProjectNo()));
            if (oldEntity == null) {
                throw new BusinessException("特殊权限不存在");
            }
        }

        SysSpecialPermission entity;
        if (req.getId() == null) {
            // 校验编码唯一性
            boolean exists =
                    sysSpecialPermissionMapper.exists(
                            Wrappers.<SysSpecialPermission>lambdaQuery()
                                    .eq(SysSpecialPermission::getCode, req.getCode())
                                    .eq(
                                            SysSpecialPermission::getSubjectId,
                                            AppContext.getSubjectId())
                                    .eq(
                                            SysSpecialPermission::getProjectNo,
                                            AppContext.getProjectNo()));
            if (exists) {
                throw new BusinessException("权限编码已存在");
            }
            // 新增
            entity = new SysSpecialPermission();
            BeanUtils.copyProperties(req, entity);
            entity.setSubjectId(AppContext.getSubjectId());
            entity.setProjectNo(AppContext.getProjectNo());
            sysSpecialPermissionMapper.insert(entity);
            // 设置id到请求对象，用于审计切面重新解析dataId
            req.setId(entity.getId());
        } else {
            // 编辑
            entity = oldEntity;
            // 校验编码唯一性（排除自己）
            if (!entity.getCode().equals(req.getCode())) {
                boolean exists =
                        sysSpecialPermissionMapper.exists(
                                Wrappers.<SysSpecialPermission>lambdaQuery()
                                        .eq(SysSpecialPermission::getCode, req.getCode())
                                        .ne(SysSpecialPermission::getId, req.getId())
                                        .eq(
                                                SysSpecialPermission::getSubjectId,
                                                AppContext.getSubjectId())
                                        .eq(
                                                SysSpecialPermission::getProjectNo,
                                                AppContext.getProjectNo()));
                if (exists) {
                    throw new BusinessException("权限编码已存在");
                }
            }
            entity.setCode(req.getCode());
            entity.setName(req.getName());
            entity.setDescription(req.getDescription());
            entity.setSort(req.getSort());
            entity.setStatus(req.getStatus());
            sysSpecialPermissionMapper.updateById(entity);
        }

        // 构建返回对象
        SysRightResp resp = new SysRightResp();
        resp.setId(entity.getId());
        resp.setRightName(entity.getName());
        resp.setRightSlug(entity.getCode());
        resp.setNodeType(3);
        resp.setDescription(entity.getDescription());
        resp.setParentRightName(null);
        resp.setParentRightSlug(null);
        resp.setType(null);
        return resp;
    }

    @Transactional
    @Override
    @DataAudit(
            module = "系统设置",
            subModule = "其它权限",
            operation = OperationType.DELETE,
            deleteDisplayField = "name",
            entityClass = SysSpecialPermission.class,
            tableName = "sys_special_permission",
            dataIdField = "#id")
    public void deleteSpecialPermission(Long id, boolean force) {
        SysSpecialPermission sysSpecialPermission =
                sysSpecialPermissionMapper.selectOne(
                        Wrappers.<SysSpecialPermission>lambdaQuery()
                                .eq(SysSpecialPermission::getId, id)
                                .eq(SysSpecialPermission::getSubjectId, AppContext.getSubjectId())
                                .eq(SysSpecialPermission::getProjectNo, AppContext.getProjectNo()));
        if (sysSpecialPermission == null) {
            throw new BusinessException("特殊权限不存在");
        }
        // 引用检查
        ReferenceContext context =
                ReferenceContext.builder()
                        .targetType(CheckConstant.SYS_SPECIAL_PERMISSION)
                        .targetId(sysSpecialPermission.getId())
                        .targetName(sysSpecialPermission.getName())
                        .build();
        List<String> check = referenceCheckManager.check(context);
        if (!check.isEmpty()) {
            throw new PopException(String.join("\n", check));
        }
        sysSpecialPermissionMapper.deleteById(id);
    }

    @Override
    public List<SysSpecialPermissionOptionResp> listSpecialPermissionOptions() {
        List<SysSpecialPermission> list =
                sysSpecialPermissionMapper.selectList(
                        new LambdaQueryWrapper<SysSpecialPermission>()
                                .eq(SysSpecialPermission::getProjectNo, AppContext.getProjectNo())
                                .eq(SysSpecialPermission::getSubjectId, AppContext.getSubjectId())
                                .eq(SysSpecialPermission::getStatus, 1)
                                .orderByAsc(SysSpecialPermission::getSort)
                                .orderByAsc(SysSpecialPermission::getId));
        return list.stream()
                .map(
                        permission -> {
                            SysSpecialPermissionOptionResp resp =
                                    new SysSpecialPermissionOptionResp();
                            resp.setId(permission.getId());
                            resp.setName(permission.getName());
                            resp.setCode(permission.getCode());
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public List<SysSpecialPermissionResp> listSpecialPermission(QuerySysSpecialPermissionReq req) {
        List<SysSpecialPermission> list =
                sysSpecialPermissionMapper.selectList(
                        new LambdaQueryWrapper<SysSpecialPermission>()
                                .eq(SysSpecialPermission::getProjectNo, AppContext.getProjectNo())
                                .eq(SysSpecialPermission::getSubjectId, AppContext.getSubjectId())
                                .like(
                                        StringUtils.hasText(req.getName()),
                                        SysSpecialPermission::getName,
                                        req.getName())
                                .like(
                                        StringUtils.hasText(req.getCode()),
                                        SysSpecialPermission::getCode,
                                        req.getCode())
                                .eq(
                                        req.getStatus() != null,
                                        SysSpecialPermission::getStatus,
                                        req.getStatus())
                                .orderByAsc(SysSpecialPermission::getSort)
                                .orderByAsc(SysSpecialPermission::getId));
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        return list.stream()
                .map(
                        permission -> {
                            SysSpecialPermissionResp resp = new SysSpecialPermissionResp();
                            resp.setId(permission.getId());
                            resp.setCode(permission.getCode());
                            resp.setName(permission.getName());
                            resp.setDescription(permission.getDescription());
                            resp.setSort(permission.getSort());
                            resp.setStatus(permission.getStatus());
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public SysSpecialPermissionResp getSpecialPermission(Long id) {
        SysSpecialPermission entity =
                sysSpecialPermissionMapper.selectOne(
                        Wrappers.<SysSpecialPermission>lambdaQuery()
                                .eq(SysSpecialPermission::getId, id)
                                .eq(SysSpecialPermission::getSubjectId, AppContext.getSubjectId())
                                .eq(SysSpecialPermission::getProjectNo, AppContext.getProjectNo()));
        if (entity == null) {
            throw new BusinessException("特殊权限不存在");
        }
        SysSpecialPermissionResp resp = new SysSpecialPermissionResp();
        resp.setId(entity.getId());
        resp.setCode(entity.getCode());
        resp.setName(entity.getName());
        resp.setDescription(entity.getDescription());
        resp.setSort(entity.getSort());
        resp.setStatus(entity.getStatus());
        return resp;
    }

    @Override
    public CheckUserSpecialPermissionResp checkUserSpecialPermission(
            CheckUserSpecialPermissionReq req) {
        Long subjectId = AppContext.getSubjectId();
        if (subjectId == null) {
            throw new BusinessException("缺少主体id");
        }

        // 1. 根据特殊权限编码查询特殊权限ID
        SysSpecialPermission specialPermission =
                sysSpecialPermissionMapper.selectOne(
                        Wrappers.<SysSpecialPermission>lambdaQuery()
                                .eq(SysSpecialPermission::getCode, req.getRightCode())
                                .eq(SysSpecialPermission::getSubjectId, subjectId)
                                .eq(SysSpecialPermission::getProjectNo, AppContext.getProjectNo())
                                .eq(SysSpecialPermission::getStatus, 1));
        if (specialPermission == null) {
            throw new BusinessException("特殊权限编码不存在");
        }

        // 2. 根据用户ID和主体ID查询用户角色关联（状态有效且时效有效）
        LocalDate today = LocalDate.now();
        List<SysRoleUser> roleUsers =
                sysRoleUserMapper.selectList(
                        Wrappers.<SysRoleUser>lambdaQuery()
                                .eq(SysRoleUser::getUserId, req.getUserId())
                                .eq(SysRoleUser::getStatus, 0));

        // 过滤时效有效的记录
        List<Long> validRoleIds =
                roleUsers.stream()
                        .filter(
                                ru ->
                                        isEffectiveDateValid(
                                                ru.getEffectiveType(),
                                                ru.getEffectiveStartDate(),
                                                ru.getEffectiveEndDate(),
                                                today))
                        .map(SysRoleUser::getRoleId)
                        .distinct()
                        .toList();

        if (validRoleIds.isEmpty()) {
            return CheckUserSpecialPermissionResp.builder().hasPermission(false).build();
        }
        Long count =
                sysRoleSpecialPermissionMapper.selectCount(
                        Wrappers.<SysRoleSpecialPermission>lambdaQuery()
                                .eq(
                                        SysRoleSpecialPermission::getSpecialPermissionId,
                                        specialPermission.getId())
                                .in(SysRoleSpecialPermission::getRoleId, validRoleIds));
        if (count > 0) {
            return CheckUserSpecialPermissionResp.builder().hasPermission(true).build();
        }
        // 3. 查询角色主体关联表，进一步过滤角色（状态有效且时效有效）
        List<SysRoleSubject> roleSubjects =
                sysRoleSubjectMapper.selectList(
                        Wrappers.<SysRoleSubject>lambdaQuery()
                                .eq(SysRoleSubject::getSubjectId, subjectId)
                                .eq(SysRoleSubject::getStatus, 0));

        // 过滤时效有效的角色
        validRoleIds =
                roleSubjects.stream()
                        .filter(
                                rs ->
                                        isEffectiveDateValid(
                                                rs.getEffectiveType(),
                                                rs.getEffectiveStartDate(),
                                                rs.getEffectiveEndDate(),
                                                today))
                        .map(SysRoleSubject::getRoleId)
                        .distinct()
                        .toList();

        if (validRoleIds.isEmpty()) {
            return CheckUserSpecialPermissionResp.builder().hasPermission(false).build();
        }
        count =
                sysRoleSpecialPermissionMapper.selectCount(
                        Wrappers.<SysRoleSpecialPermission>lambdaQuery()
                                .eq(
                                        SysRoleSpecialPermission::getSpecialPermissionId,
                                        specialPermission.getId())
                                .in(SysRoleSpecialPermission::getRoleId, validRoleIds));
        if (count > 0) {
            return CheckUserSpecialPermissionResp.builder().hasPermission(true).build();
        }
        // 4. 查询角色特殊权限关联表
        return CheckUserSpecialPermissionResp.builder().hasPermission(false).build();
    }

    /**
     * 验证时效性
     *
     * @param effectiveType 时效类型：1-永久，2-自定义
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param today 当前日期
     * @return 是否有效
     */
    private boolean isEffectiveDateValid(
            Integer effectiveType, LocalDate startDate, LocalDate endDate, LocalDate today) {
        if (effectiveType == null) {
            return true;
        }
        // 1-永久有效
        if (effectiveType == 1) {
            return true;
        }
        // 2-自定义时间，需要校验时间范围
        if (effectiveType == 2) {
            if (startDate == null || endDate == null) {
                return false;
            }
            return (today.isEqual(startDate) || today.isAfter(startDate))
                    && (today.isEqual(endDate) || today.isBefore(endDate));
        }
        return false;
    }
}
