package com.jdec.platform.config.biz.service;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysDataPermissionApi;
import com.jdec.platform.config.api.dto.request.DataPermissionQueryItem;
import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.config.biz.entity.SysDataPermission;
import com.jdec.platform.config.biz.mapper.SysDataPermissionMapper;
import com.jdec.platform.config.biz.mapper.SysRoleDataPermissionMapper;
import com.jdec.platform.shared.context.AppContext;
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
import org.springframework.beans.factory.annotation.Value;
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
    private final SysRoleDataPermissionMapper sysRoleDataPermissionMapper;
    private final RestTemplate restTemplate;

    @Value("#{${biz.system.data-permission.urls:{:}}}")
    private Map<String, String> bizSystemUrls;

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
        final Map<String, List<BizDataItem>> bizDataMap;
        if (CollUtil.isNotEmpty(queryItems)) {
            Map<String, List<BizDataItem>> tempMap = new HashMap<>();
            // 根据项目编码获取接口地址
            String projectNo = AppContext.getProjectNo();
            String bizSystemUrl = bizSystemUrls.get(projectNo);

            if (bizSystemUrl == null) {
                log.warn("未配置项目 {} 的业务系统接口地址", projectNo);
                bizDataMap = new HashMap<>();
            } else {
                try {
                    BizSystemResponse response =
                            restTemplate.postForObject(
                                    bizSystemUrl, queryItems, BizSystemResponse.class);
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
                bizDataMap = tempMap;
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
                                List<BizDataItem> bizDataList =
                                        bizDataMap.getOrDefault(type.getCode(), new ArrayList<>());
                                resp.setDataList(bizDataList);
                            }
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    /**
     * 根据数据权限列表获取业务数据
     *
     * @param dataPermissions 数据权限列表
     * @return key 为 dataPermissionCode，value 为业务数据列表
     */
    public Map<String, List<BizDataItem>> fetchBizDataByPermissions(
            List<SysDataPermission> dataPermissions) {
        if (CollUtil.isEmpty(dataPermissions)) {
            return new HashMap<>();
        }

        // 1. 收集所有 code 并转换为查询参数
        List<DataPermissionQueryItem> queryItems = new ArrayList<>();
        for (SysDataPermission permission : dataPermissions) {
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

        if (CollUtil.isEmpty(queryItems)) {
            return new HashMap<>();
        }

        // 2. 根据项目编码获取接口地址并调用
        String projectNo = AppContext.getProjectNo();
        String bizSystemUrl = bizSystemUrls.get(projectNo);

        if (bizSystemUrl == null) {
            log.warn("未配置项目 {} 的业务系统接口地址", projectNo);
            return new HashMap<>();
        }

        try {
            BizSystemResponse response =
                    restTemplate.postForObject(bizSystemUrl, queryItems, BizSystemResponse.class);
            if (response != null
                    && response.getStatus() == 200
                    && CollUtil.isNotEmpty(response.getData())) {
                // 转换为 Map，key 为 dataPermissionCode
                return response.getData().stream()
                        .collect(
                                Collectors.toMap(
                                        BizDataPermissionResp::getDataPermissionCode,
                                        BizDataPermissionResp::getDataList,
                                        (v1, v2) -> v1));
            }
        } catch (Exception e) {
            log.error("调用业务系统接口失败: {}", bizSystemUrl, e);
        }

        return new HashMap<>();
    }
}
