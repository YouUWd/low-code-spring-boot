package com.jdec.platform.config.biz.service;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysDataPermissionApi;
import com.jdec.platform.config.api.dto.request.DataPermissionQueryItem;
import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.BizDataPermissionResp;
import com.jdec.platform.config.api.dto.response.BizSystemResponse;
import com.jdec.platform.config.api.dto.response.SysDataPermissionResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import com.jdec.platform.config.biz.config.BizSystemConfig;
import com.jdec.platform.config.biz.entity.SysDataPermission;
import com.jdec.platform.config.biz.entity.SysProjectSubject;
import com.jdec.platform.config.biz.mapper.SysDataPermissionMapper;
import com.jdec.platform.config.biz.mapper.SysProjectSubjectMapper;
import com.jdec.platform.shared.audit.annotation.DataAudit;
import com.jdec.platform.shared.audit.enums.OperationType;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.TokenContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.enums.CommonStatusEnum;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/** 数据权限类型 Service 实现 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysDataPermissionService implements SysDataPermissionApi {

    private final SysDataPermissionMapper sysDataPermissionMapper;
    private final SysProjectSubjectMapper sysProjectSubjectMapper;
    private final RestTemplate restTemplate;
    private final BizSystemConfig bizSystemConfig;

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

        // 4. 遍历主体列表逐个调用业务系统接口，按 主体ID -> 权限编码 缓存业务数据
        Map<Long, Map<String, List<String>>> subjectBizDataMap = new HashMap<>();
        if (CollUtil.isNotEmpty(queryItems) && CollUtil.isNotEmpty(subjects)) {
            String projectNo = AppContext.getProjectNo();
            String bizSystemUrl = bizSystemConfig.getUrls().get(projectNo);
            if (!StringUtils.hasText(bizSystemUrl)) {
                log.warn("未配置项目 {} 的业务系统接口地址", projectNo);
            } else {
                for (SysProjectSubject subject : subjects) {
                    subjectBizDataMap.put(
                            subject.getSubjectId(),
                            queryBizData(
                                    bizSystemUrl, projectNo, subject.getSubjectId(), queryItems));
                }
            }
        }

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

    /**
     * 调用业务系统数据权限接口，查询指定主体下各权限编码对应的业务数据。
     *
     * <p>请求体为权限查询项（表名/字段名 + 主体ID），响应按 dataPermissionCode 归集，业务数据做去重处理。 接口调用失败时返回空 Map，不影响权限节点列表查询。
     */
    private Map<String, List<String>> queryBizData(
            String bizSystemUrl,
            String projectNo,
            Long subjectId,
            List<DataPermissionQueryItem> queryItems) {
        Map<String, List<String>> bizDataMap = new HashMap<>();
        try {
            // 每个查询项都带上当前主体ID
            List<DataPermissionQueryItem> items = new ArrayList<>();
            for (DataPermissionQueryItem queryItem : queryItems) {
                DataPermissionQueryItem item = new DataPermissionQueryItem();
                item.setTableName(queryItem.getTableName());
                item.setColumn(queryItem.getColumn());
                item.setSubjectIds(
                        subjectId == null
                                ? new ArrayList<>()
                                : Collections.singletonList(subjectId));
                items.add(item);
            }

            // 构建请求头，添加 token、subjectId 和 projectNo
            HttpHeaders headers = new HttpHeaders();
            String token = TokenContext.getToken();
            if (token != null) {
                headers.set("Authorization", "Bearer " + token);
            }
            if (subjectId != null) {
                headers.set("X-Subject-Id", String.valueOf(subjectId));
            }
            if (projectNo != null) {
                headers.set("X-Project-No", projectNo);
            }

            HttpEntity<List<DataPermissionQueryItem>> requestEntity =
                    new HttpEntity<>(items, headers);
            BizSystemResponse response =
                    restTemplate.postForObject(
                            bizSystemUrl, requestEntity, BizSystemResponse.class);
            if (response != null
                    && Integer.valueOf(200).equals(response.getStatus())
                    && CollUtil.isNotEmpty(response.getData())) {
                for (BizDataPermissionResp data : response.getData()) {
                    if (data.getDataPermissionCode() == null) {
                        continue;
                    }
                    // 业务数据去重，null 统一转换为 "Null"
                    List<String> bizNames =
                            CollUtil.emptyIfNull(data.getBizNames()).stream()
                                    .map(s -> s == null ? "Null" : s)
                                    .distinct()
                                    .collect(Collectors.toList());
                    bizDataMap.merge(
                            data.getDataPermissionCode(),
                            bizNames,
                            (v1, v2) -> {
                                List<String> merged = new ArrayList<>(v1);
                                merged.addAll(v2);
                                return merged;
                            });
                }
            }
        } catch (Exception e) {
            log.error("调用业务系统接口失败: {}, subjectId: {}", bizSystemUrl, subjectId, e);
        }
        return bizDataMap;
    }
}
