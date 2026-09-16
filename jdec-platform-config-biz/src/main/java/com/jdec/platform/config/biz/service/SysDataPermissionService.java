package com.jdec.platform.config.biz.service;

import static org.jooq.impl.DSL.*;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysDataPermissionApi;
import com.jdec.platform.config.api.dto.request.DataPermissionQueryItem;
import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.SysDataPermissionResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import com.jdec.platform.config.biz.entity.SysDataPermission;
import com.jdec.platform.config.biz.entity.SysProjectSubject;
import com.jdec.platform.config.biz.mapper.SysDataPermissionMapper;
import com.jdec.platform.config.biz.mapper.SysProjectSubjectMapper;
import com.jdec.platform.shared.audit.annotation.DataAudit;
import com.jdec.platform.shared.audit.enums.OperationType;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import com.jdec.platform.shared.enums.CommonStatusEnum;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 数据权限类型 Service 实现 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysDataPermissionService implements SysDataPermissionApi {

    private final SysDataPermissionMapper sysDataPermissionMapper;
    private final SysProjectSubjectMapper sysProjectSubjectMapper;
    private final DataSourceResolver dataSourceResolver;

    @Override
    @DataAudit(
            module = "系统设置",
            subModule = "数据权限配置",
            operation = OperationType.UPDATE,
            tableName = "sys_data_permission",
            dataIdField = "#req.id")
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

            // 设置id到请求对象，用于审计切面重新解析dataId
            req.setId(entity.getId());
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

    @Override
    public List<SysDataPermissionResp> listDataPermission(QuerySysDataPermissionReq req) {
        // 1. 查询当前主体和项目编码下的数据权限节点
        List<SysDataPermission> list =
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
                                .eq(SysDataPermission::getStatus, CommonStatusEnum.ENABLE.getCode())
                                .orderByAsc(SysDataPermission::getSort)
                                .orderByAsc(SysDataPermission::getId));

        if (list.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 根据项目编码查询项目主体列表
        List<SysProjectSubject> subjects =
                sysProjectSubjectMapper.selectList(
                        new LambdaQueryWrapper<SysProjectSubject>()
                                .eq(SysProjectSubject::getProjectNo, AppContext.getProjectNo()));

        // 3. 收集所有 code 并转换为查询参数（编码形如「表名*字段名」）
        List<DataPermissionQueryItem> queryItems = new ArrayList<>();
        for (SysDataPermission permission : list) {
            if (permission.getCode() != null && permission.getCode().contains("*")) {
                String[] parts = permission.getCode().split("\\*");
                if (parts.length == 2) {
                    DataPermissionQueryItem item = new DataPermissionQueryItem();
                    item.setTableName(parts[0]);
                    item.setColumn(parts[1]);
                    queryItems.add(item);
                }
            }
        }

        // 4. 直连项目编码对应的数据源查询，按 主体ID -> 权限编码 缓存业务数据
        Map<Long, Map<String, List<String>>> subjectBizDataMap =
                querySubjectBizData(subjects, queryItems);

        // 5. 层层封装返回：权限节点 -> 主体列表 -> 主体业务数据
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
                            resp.setSubjectInfos(
                                    subjects.stream()
                                            .map(
                                                    subject -> {
                                                        SysDataPermissionResp.SubjectInfo info =
                                                                new SysDataPermissionResp
                                                                        .SubjectInfo();
                                                        info.setSubjectId(subject.getSubjectId());
                                                        info.setSubjectName(
                                                                subject.getSubjectName());
                                                        info.setBizNames(
                                                                subjectBizDataMap
                                                                        .getOrDefault(
                                                                                subject
                                                                                        .getSubjectId(),
                                                                                Collections
                                                                                        .emptyMap())
                                                                        .getOrDefault(
                                                                                type.getCode(),
                                                                                new ArrayList<>()));
                                                        return info;
                                                    })
                                            .collect(Collectors.toList()));
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    /** 组装查询项的主体ID后，查询当前项目下所有主体的业务数据 */
    private Map<Long, Map<String, List<String>>> querySubjectBizData(
            List<SysProjectSubject> subjects, List<DataPermissionQueryItem> queryItems) {
        if (CollUtil.isEmpty(queryItems) || CollUtil.isEmpty(subjects)) {
            return new HashMap<>();
        }

        List<Long> subjectIds =
                subjects.stream()
                        .map(SysProjectSubject::getSubjectId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
        if (subjectIds.isEmpty()) {
            return new HashMap<>();
        }

        queryItems.forEach(item -> item.setSubjectIds(subjectIds));
        return queryBizData(AppContext.getProjectNo(), queryItems);
    }

    /**
     * 直连项目编码对应的数据源，查询各权限编码下的业务数据。
     *
     * <p>权限编码形如「表名*字段名」，逐项拼接 SQL： {@code SELECT DISTINCT subject_id, 字段名 FROM 表名 WHERE subject_id
     * IN (主体ID列表)}， 结果按 主体ID -> 权限编码 归集，空值统一记为 "Null"。 单个查询项查询失败时仅记日志，不影响权限节点列表查询。
     */
    private Map<Long, Map<String, List<String>>> queryBizData(
            String projectNo, List<DataPermissionQueryItem> queryItems) {
        Map<Long, Map<String, List<String>>> subjectBizDataMap = new HashMap<>();
        if (CollUtil.isEmpty(queryItems)) {
            return subjectBizDataMap;
        }

        DSLContext dsl;
        try {
            dsl = dataSourceResolver.getDSLContextByProjectNo(projectNo);
        } catch (Exception e) {
            log.error("项目 {} 数据源解析失败，无法查询业务数据", projectNo, e);
            return subjectBizDataMap;
        }

        Field<String> subjectField = field(name("subject_id"), String.class);
        for (DataPermissionQueryItem item : queryItems) {
            List<String> subjectIds =
                    CollUtil.emptyIfNull(item.getSubjectIds()).stream()
                            .filter(Objects::nonNull)
                            .map(String::valueOf)
                            .collect(Collectors.toList());
            if (subjectIds.isEmpty()
                    || !StringUtils.hasText(item.getTableName())
                    || !StringUtils.hasText(item.getColumn())) {
                continue;
            }

            String dataPermissionCode = item.getTableName() + "*" + item.getColumn();
            try {
                Field<String> valueField = field(name(item.getColumn()), String.class);
                var records =
                        dsl.selectDistinct(subjectField, valueField)
                                .from(table(name(item.getTableName())))
                                .where(subjectField.in(subjectIds))
                                .fetch();

                for (Record record : records) {
                    Long subjectId = parseSubjectId(record.get(subjectField));
                    if (subjectId == null) {
                        continue;
                    }
                    String value = record.get(valueField);
                    // null 统一转换为 "Null"
                    subjectBizDataMap
                            .computeIfAbsent(subjectId, key -> new HashMap<>())
                            .computeIfAbsent(dataPermissionCode, key -> new ArrayList<>())
                            .add(value == null ? "Null" : value);
                }
            } catch (Exception e) {
                log.error(
                        "查询业务数据失败: projectNo={}, table={}, column={}",
                        projectNo,
                        item.getTableName(),
                        item.getColumn(),
                        e);
            }
        }
        return subjectBizDataMap;
    }

    /** 将查询结果中的主体ID转换为 Long，非法值返回 null */
    private Long parseSubjectId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            log.warn("主体ID非法: {}", value);
            return null;
        }
    }
}
