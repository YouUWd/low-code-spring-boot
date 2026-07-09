package com.jdec.platform.config.biz.service;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysDataPermissionApi;
import com.jdec.platform.config.api.bo.BizDataItemBO;
import com.jdec.platform.config.api.dto.request.DataPermissionQueryItem;
import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.BizDataPermissionResp;
import com.jdec.platform.config.api.dto.response.BizSystemResponse;
import com.jdec.platform.config.api.dto.response.SysDataPermissionResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import com.jdec.platform.config.biz.config.BizSystemConfig;
import com.jdec.platform.config.biz.entity.SysDataPermission;
import com.jdec.platform.config.biz.mapper.SysDataPermissionMapper;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.TokenContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.enums.CommonStatusEnum;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final RestTemplate restTemplate;
    private final BizSystemConfig bizSystemConfig;

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

        // 2. 收集所有 code 并转换为查询参数
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

        // 3. 调用业务系统接口查询业务数据
        final Map<String, List<SysDataPermissionResp.BizDataItemResp>> bizDataMap;
        if (CollUtil.isNotEmpty(queryItems)) {
            Map<String, List<BizDataItemBO>> tempMap = new HashMap<>();
            // 根据项目编码获取接口地址
            String projectNo = AppContext.getProjectNo();
            String bizSystemUrl = bizSystemConfig.getUrls().get(projectNo);

            if (bizSystemUrl == null) {
                log.warn("未配置项目 {} 的业务系统接口地址", projectNo);
                bizDataMap = new HashMap<>();
            } else {
                bizDataMap = new HashMap<>();
                try {
                    // 构建请求头，添加 token、subjectId 和 projectNo
                    HttpHeaders headers = new HttpHeaders();
                    String token = TokenContext.getToken();
                    if (token != null) {
                        headers.set("Authorization", "Bearer " + token);
                    }

                    Long subjectId = AppContext.getSubjectId();
                    if (subjectId != null) {
                        headers.set("Subject-Id", String.valueOf(subjectId));
                    }
                    if (projectNo != null) {
                        headers.set("X-Project-No", projectNo);
                    }

                    // 构建请求实体
                    HttpEntity<List<DataPermissionQueryItem>> requestEntity =
                            new HttpEntity<>(queryItems, headers);

                    BizSystemResponse response =
                            restTemplate.postForObject(
                                    bizSystemUrl, requestEntity, BizSystemResponse.class);
                    if (response != null
                            && response.getStatus() == 200
                            && CollUtil.isNotEmpty(response.getData())) {
                        // 转换为 Map，key 为 dataPermissionCode
                        tempMap =
                                response.getData().stream()
                                        .collect(
                                                Collectors.toMap(
                                                        BizDataPermissionResp
                                                                ::getDataPermissionCode,
                                                        BizDataPermissionResp::getDataList,
                                                        (v1, v2) -> v1));
                    }
                } catch (Exception e) {
                    log.error("调用业务系统接口失败: {}", bizSystemUrl, e);
                    // 接口调用失败时返回空数据，不影响节点列表查询
                }
                // 对业务数据进行distinct操作
                for (String key : tempMap.keySet()) {
                    List<BizDataItemBO> bizDataItemBOS = tempMap.get(key);

                    // 分离 bizName 为 null 和非 null 的数据
                    Map<Boolean, List<BizDataItemBO>> partitioned =
                            bizDataItemBOS.stream()
                                    .collect(
                                            Collectors.partitioningBy(
                                                    item -> item.getBizName() != null));

                    List<SysDataPermissionResp.BizDataItemResp> aggregatedList = new ArrayList<>();

                    // 处理 bizName 非 null 的数据：按 bizName 聚合，将相同 bizName 的 bizId 拼接
                    List<BizDataItemBO> nonNullItems = partitioned.get(true);
                    if (CollUtil.isNotEmpty(nonNullItems)) {
                        Map<String, String> aggregatedData =
                                nonNullItems.stream()
                                        .collect(
                                                Collectors.groupingBy(
                                                        BizDataItemBO::getBizName,
                                                        Collectors.mapping(
                                                                BizDataItemBO::getBizId,
                                                                Collectors.joining(","))));

                        // 转换为 List<BizDataItemResp> 格式
                        aggregatedData.entrySet().stream()
                                .map(
                                        entry -> {
                                            SysDataPermissionResp.BizDataItemResp item =
                                                    new SysDataPermissionResp.BizDataItemResp();
                                            item.setBizName(entry.getKey());
                                            item.setBizId(entry.getValue());
                                            return item;
                                        })
                                .forEach(aggregatedList::add);
                    }

                    // 处理 bizName 为 null 的数据：聚合所有 bizId 用逗号拼接
                    List<BizDataItemBO> nullItems = partitioned.get(false);
                    if (CollUtil.isNotEmpty(nullItems)) {
                        String aggregatedBizIds =
                                nullItems.stream()
                                        .map(BizDataItemBO::getBizId)
                                        .collect(Collectors.joining(","));

                        SysDataPermissionResp.BizDataItemResp resp =
                                new SysDataPermissionResp.BizDataItemResp();
                        resp.setBizName("Null");
                        resp.setBizId(aggregatedBizIds);
                        aggregatedList.add(resp);
                    }

                    bizDataMap.put(key, aggregatedList);
                }
            }
        } else {
            bizDataMap = new HashMap<>();
        }

        // 4. 组装返回数据
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

                            // 填充业务数据
                            if (type.getCode() != null) {
                                List<SysDataPermissionResp.BizDataItemResp> bizDataList =
                                        bizDataMap.getOrDefault(type.getCode(), new ArrayList<>());
                                resp.setDataList(bizDataList);
                            }
                            return resp;
                        })
                .collect(Collectors.toList());
    }
}
