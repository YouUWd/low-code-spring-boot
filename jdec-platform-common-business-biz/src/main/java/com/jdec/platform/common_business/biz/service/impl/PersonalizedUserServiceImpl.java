package com.jdec.platform.common_business.biz.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.jdec.platform.common_business.api.PersonalizedUserServiceApi;
import com.jdec.platform.common_business.api.dto.common.PersonalizedModuleDTO;
import com.jdec.platform.common_business.api.dto.common.TableHeaderConf;
import com.jdec.platform.common_business.api.dto.request.PersonalizedModuleReq;
import com.jdec.platform.common_business.api.dto.response.PersonalizedModuleResp;
import com.jdec.platform.common_business.biz.dto.PersonalSettingColumnDTO;
import com.jdec.platform.common_business.biz.dto.configcenter.response.PersonalSettingResp;
import com.jdec.platform.common_business.biz.entity.ComPersonalizedUserColumn;
import com.jdec.platform.common_business.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.common_business.biz.mapper.ComPersonalizedUserColumnMapper;
import com.jdec.platform.common_business.biz.service.ConfigCenterApiService;
import com.jdec.platform.common_business.biz.util.JsonUtil;
import com.jdec.platform.common_business.biz.util.ToolUtils;
import com.jdec.platform.shared.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
@Slf4j
public class PersonalizedUserServiceImpl implements PersonalizedUserServiceApi {

    private final ComPersonalizedUserColumnMapper personalizedUserColumnMapper;

    private final ConfigCenterApiService configCenterApiService;

    @Override
    public List<PersonalizedModuleResp> getUserPersonalizedModuleSetting(
            Long userId, Long subjectId, Long roleId, String projectNo) {
        List<PersonalSettingResp> respList =
                configCenterApiService.getPersonalSettingByRole(subjectId, roleId, null);
        if (CollectionUtil.isEmpty(respList)) {
            return List.of();
        }
        List<Long> moduleIds = respList.stream().map(x -> x.getId()).collect(Collectors.toList());
        LambdaQueryWrapper<ComPersonalizedUserColumn> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ComPersonalizedUserColumn::getUserId, userId);
        queryWrapper.in(ComPersonalizedUserColumn::getModuleId, moduleIds);
        queryWrapper.eq(ComPersonalizedUserColumn::getProjectNo, projectNo);
        List<ComPersonalizedUserColumn> columnList =
                personalizedUserColumnMapper.selectList(queryWrapper);
        Map<Long, ComPersonalizedUserColumn> columnMap = new HashMap<>();
        if (CollectionUtil.isEmpty(columnList)) {
            columnMap = columnList.stream().collect(Collectors.toMap(x -> x.getModuleId(), x -> x));
        }
        List<PersonalizedModuleResp> result = new ArrayList<>();
        try {
            for (PersonalSettingResp resp : respList) {
                ComPersonalizedUserColumn personalizedUserColumn = columnMap.get(resp.getId());
                List<PersonalizedModuleDTO> personalizedModuleDTOList = new ArrayList<>();
                if (personalizedUserColumn != null
                        && StringUtils.isNotEmpty(personalizedUserColumn.getColumnProperty())) {
                    personalizedModuleDTOList =
                            JsonUtil.json2Obj(
                                    personalizedUserColumn.getColumnProperty(),
                                    new TypeReference<List<PersonalizedModuleDTO>>() {});
                }
                Map<String, PersonalizedModuleDTO> map =
                        personalizedModuleDTOList.stream()
                                .collect(
                                        Collectors.toMap(
                                                x -> x.getTable() + "." + x.getField(), x -> x));

                PersonalizedModuleResp r = new PersonalizedModuleResp();
                r.setModuleId(resp.getId());
                r.setModuleName(resp.getModuleName());

                List<PersonalizedModuleDTO> rr = new ArrayList<>();
                for (PersonalSettingColumnDTO settingColumnDTO : resp.getPersonalSettingColumns()) {
                    PersonalizedModuleDTO personalizedModuleDTO = new PersonalizedModuleDTO();
                    if (map.containsKey(
                            settingColumnDTO.getTable() + "." + settingColumnDTO.getField())) {
                        BeanUtil.copyProperties(
                                map.get(
                                        settingColumnDTO.getTable()
                                                + "."
                                                + settingColumnDTO.getField()),
                                personalizedModuleDTO,
                                CopyOptions.create().ignoreError().ignoreNullValue());
                        personalizedModuleDTO.setChecked(true);
                    } else {
                        BeanUtil.copyProperties(
                                settingColumnDTO,
                                personalizedModuleDTO,
                                CopyOptions.create().ignoreError().ignoreNullValue());
                        personalizedModuleDTO.setChecked(false);
                    }
                    rr.add(personalizedModuleDTO);
                }
                r.setColumnRespList(rr);
                result.add(r);
            }
        } catch (Exception e) {
            throw new BusinessException(
                    ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                    ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                    e);
        }

        return result;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public Boolean saveUserPersonalizedModuleSetting(
            Long userId, String projectNo, List<PersonalizedModuleReq> reqs) {
        if (CollectionUtil.isEmpty(reqs)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_REQUEST.getCode(),
                    ApiResponseCodeEnum.BAD_REQUEST.getMessage());
        }
        if (reqs.stream().anyMatch(x -> x.getModuleId() == null)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_REQUEST.getCode(),
                    ApiResponseCodeEnum.BAD_REQUEST.getMessage());
        }
        try {
            for (PersonalizedModuleReq req : reqs) {
                LambdaQueryWrapper<ComPersonalizedUserColumn> queryWrapper =
                        new LambdaQueryWrapper();
                queryWrapper.eq(ComPersonalizedUserColumn::getUserId, userId);
                queryWrapper.eq(ComPersonalizedUserColumn::getProjectNo, projectNo);
                queryWrapper.eq(ComPersonalizedUserColumn::getModuleId, req.getModuleId());
                ComPersonalizedUserColumn old =
                        personalizedUserColumnMapper.selectOne(queryWrapper);
                if (old == null) {
                    ComPersonalizedUserColumn userColumn = new ComPersonalizedUserColumn();
                    userColumn.setUserId(userId);
                    userColumn.setProjectNo(projectNo);
                    userColumn.setModuleId(req.getModuleId());
                    List<PersonalizedModuleDTO> columns = new ArrayList<>();
                    if (CollectionUtil.isNotEmpty(req.getColumnRespList())) {
                        columns =
                                req.getColumnRespList().stream()
                                        .filter(x -> (x.getChecked() != null && x.getChecked()))
                                        .collect(Collectors.toList());
                    }
                    if (CollectionUtil.isNotEmpty(columns)) {
                        columns.stream().forEach(x -> x.setChecked(null));
                        userColumn.setColumnProperty(JsonUtil.obj2Json(columns));
                    } else {
                        userColumn.setColumnProperty(JsonUtil.obj2Json(List.of()));
                    }
                    userColumn.setCreatedBy(userId);
                    userColumn.setCreatedName(null);
                    userColumn.setCreatedDate(LocalDateTime.now());
                    userColumn.setUpdatedBy(userId);
                    userColumn.setUpdatedName(null);
                    userColumn.setUpdatedDate(LocalDateTime.now());
                    userColumn.setDeleted(Boolean.FALSE);
                    personalizedUserColumnMapper.insert(userColumn);
                } else {
                    LambdaUpdateWrapper<ComPersonalizedUserColumn> updateWrapper =
                            new LambdaUpdateWrapper();
                    updateWrapper.eq(ComPersonalizedUserColumn::getUserId, userId);
                    updateWrapper.eq(ComPersonalizedUserColumn::getProjectNo, projectNo);
                    updateWrapper.eq(ComPersonalizedUserColumn::getModuleId, req.getModuleId());
                    updateWrapper.set(ComPersonalizedUserColumn::getUpdatedBy, userId);
                    // updateWrapper.set(PersonalizedUserColumn::getUpdatedName)
                    updateWrapper.set(
                            ComPersonalizedUserColumn::getUpdatedDate, LocalDateTime.now());

                    List<PersonalizedModuleDTO> columns = new ArrayList<>();
                    if (CollectionUtil.isNotEmpty(req.getColumnRespList())) {
                        columns =
                                req.getColumnRespList().stream()
                                        .filter(x -> (x.getChecked() != null && x.getChecked()))
                                        .collect(Collectors.toList());
                    }
                    if (CollectionUtil.isNotEmpty(columns)) {
                        columns.stream().forEach(x -> x.setChecked(null));
                        updateWrapper.set(
                                ComPersonalizedUserColumn::getColumnProperty,
                                JsonUtil.obj2Json(columns));
                    } else {
                        updateWrapper.set(
                                ComPersonalizedUserColumn::getColumnProperty,
                                JsonUtil.obj2Json(List.of()));
                    }
                    personalizedUserColumnMapper.update(updateWrapper);
                }
            }
            return Boolean.TRUE;
        } catch (Exception e) {
            throw new BusinessException(
                    ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                    ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                    e);
        }
    }

    @Override
    public Boolean restoreToDefaultSetting(
            Long userId, Long roleId, Long subjectId, String projectNo, Long moduleId) {
        List<PersonalSettingResp> respList =
                configCenterApiService.getPersonalSettingByRole(subjectId, roleId, moduleId);
        if (CollectionUtil.isEmpty(respList)) {
            return Boolean.FALSE;
        }
        Map<Long, PersonalSettingResp> settingRespMap =
                respList.stream().collect(Collectors.toMap(x -> x.getId(), x -> x));

        try {
            LambdaQueryWrapper<ComPersonalizedUserColumn> queryWrapper = new LambdaQueryWrapper();
            queryWrapper.eq(ComPersonalizedUserColumn::getUserId, userId);
            queryWrapper.eq(ComPersonalizedUserColumn::getProjectNo, projectNo);
            queryWrapper.eq(ComPersonalizedUserColumn::getModuleId, moduleId);
            ComPersonalizedUserColumn old = personalizedUserColumnMapper.selectOne(queryWrapper);
            if (old == null) {
                ComPersonalizedUserColumn userColumn = new ComPersonalizedUserColumn();
                userColumn.setUserId(userId);
                userColumn.setProjectNo(projectNo);
                userColumn.setModuleId(moduleId);

                PersonalSettingResp settingResp = settingRespMap.get(moduleId);
                if (settingResp == null) {
                    userColumn.setColumnProperty(JsonUtil.obj2Json(List.of()));
                } else {
                    List<PersonalizedModuleDTO> rr = new ArrayList<>();
                    for (PersonalSettingColumnDTO settingColumnDTO :
                            settingResp.getPersonalSettingColumns()) {
                        PersonalizedModuleDTO personalizedModuleDTO = new PersonalizedModuleDTO();
                        BeanUtil.copyProperties(
                                settingColumnDTO,
                                personalizedModuleDTO,
                                CopyOptions.create().ignoreError().ignoreNullValue());
                        rr.add(personalizedModuleDTO);
                    }
                    userColumn.setColumnProperty(JsonUtil.obj2Json(rr));
                }
                userColumn.setCreatedBy(userId);
                userColumn.setCreatedName(null);
                userColumn.setCreatedDate(LocalDateTime.now());
                userColumn.setUpdatedBy(userId);
                userColumn.setUpdatedName(null);
                userColumn.setUpdatedDate(LocalDateTime.now());
                userColumn.setDeleted(Boolean.FALSE);
                personalizedUserColumnMapper.insert(userColumn);
            } else {
                LambdaUpdateWrapper<ComPersonalizedUserColumn> updateWrapper =
                        new LambdaUpdateWrapper();
                updateWrapper.eq(ComPersonalizedUserColumn::getUserId, userId);
                updateWrapper.eq(ComPersonalizedUserColumn::getProjectNo, projectNo);
                updateWrapper.eq(ComPersonalizedUserColumn::getModuleId, moduleId);
                updateWrapper.set(ComPersonalizedUserColumn::getUpdatedBy, userId);
                // updateWrapper.set(PersonalizedUserColumn::getUpdatedName)
                updateWrapper.set(ComPersonalizedUserColumn::getUpdatedDate, LocalDateTime.now());

                PersonalSettingResp settingResp = settingRespMap.get(moduleId);
                if (settingResp == null) {
                    updateWrapper.set(
                            ComPersonalizedUserColumn::getColumnProperty,
                            JsonUtil.obj2Json(List.of()));
                } else {
                    List<PersonalizedModuleDTO> rr = new ArrayList<>();
                    for (PersonalSettingColumnDTO settingColumnDTO :
                            settingResp.getPersonalSettingColumns()) {
                        PersonalizedModuleDTO personalizedModuleDTO = new PersonalizedModuleDTO();
                        BeanUtil.copyProperties(
                                settingColumnDTO,
                                personalizedModuleDTO,
                                CopyOptions.create().ignoreError().ignoreNullValue());
                        rr.add(personalizedModuleDTO);
                    }
                    updateWrapper.set(
                            ComPersonalizedUserColumn::getColumnProperty, JsonUtil.obj2Json(rr));
                }
                personalizedUserColumnMapper.update(updateWrapper);
            }
            return Boolean.TRUE;

        } catch (Exception e) {
            throw new BusinessException(
                    ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                    ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                    e);
        }
    }

    @Override
    public PersonalizedModuleResp getUserPersonalizedModuleSettingByModuleId(
            Long userId, Long roleId, Long subjectId, String projectNo, Long moduleId) {
        List<PersonalSettingResp> respList =
                configCenterApiService.getPersonalSettingByRole(subjectId, roleId, moduleId);
        if (CollectionUtil.isEmpty(respList)) {
            return null;
        }
        PersonalSettingResp resp =
                respList.stream().filter(x -> x.getId().equals(moduleId)).findFirst().orElse(null);
        if (resp == null) {
            return null;
        }
        LambdaQueryWrapper<ComPersonalizedUserColumn> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ComPersonalizedUserColumn::getUserId, userId);
        queryWrapper.eq(ComPersonalizedUserColumn::getModuleId, moduleId);
        queryWrapper.eq(ComPersonalizedUserColumn::getProjectNo, projectNo);
        ComPersonalizedUserColumn personalizedUserColumn =
                personalizedUserColumnMapper.selectOne(queryWrapper);
        try {
            List<PersonalizedModuleDTO> personalizedModuleDTOList = new ArrayList<>();
            if (personalizedUserColumn != null
                    && StringUtils.isNotEmpty(personalizedUserColumn.getColumnProperty())) {
                personalizedModuleDTOList =
                        JsonUtil.json2Obj(
                                personalizedUserColumn.getColumnProperty(),
                                new TypeReference<List<PersonalizedModuleDTO>>() {});
            }
            Map<String, PersonalizedModuleDTO> map =
                    personalizedModuleDTOList.stream()
                            .collect(
                                    Collectors.toMap(
                                            x -> x.getTable() + "." + x.getField(), x -> x));

            PersonalizedModuleResp r = new PersonalizedModuleResp();
            r.setModuleId(resp.getId());
            r.setModuleName(resp.getModuleName());

            List<PersonalizedModuleDTO> rr = new ArrayList<>();
            for (PersonalSettingColumnDTO settingColumnDTO : resp.getPersonalSettingColumns()) {
                PersonalizedModuleDTO personalizedModuleDTO = new PersonalizedModuleDTO();
                if (map.containsKey(
                        settingColumnDTO.getTable() + "." + settingColumnDTO.getField())) {
                    BeanUtil.copyProperties(
                            map.get(
                                    settingColumnDTO.getTable()
                                            + "."
                                            + settingColumnDTO.getField()),
                            personalizedModuleDTO,
                            CopyOptions.create().ignoreError().ignoreNullValue());
                    personalizedModuleDTO.setChecked(true);
                } else {
                    BeanUtil.copyProperties(
                            settingColumnDTO,
                            personalizedModuleDTO,
                            CopyOptions.create().ignoreError().ignoreNullValue());
                    personalizedModuleDTO.setChecked(false);
                }
                rr.add(personalizedModuleDTO);
            }
            r.setColumnRespList(rr);
            return r;
        } catch (Exception e) {
            throw new BusinessException(
                    ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                    ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                    e);
        }
    }

    @Override
    public List<TableHeaderConf> getUserPersonalizedModuleHeader(
            String projectNo, Long subjectId, Long roleId, Long loginUserId, Long moduleId) {
        List<PersonalSettingResp> respList =
                configCenterApiService.getPersonalSettingByRole(subjectId, roleId, moduleId);
        if (CollectionUtil.isEmpty(respList)) {
            return List.of();
        }
        PersonalSettingResp resp =
                respList.stream().filter(x -> x.getId().equals(moduleId)).findFirst().orElse(null);
        if (resp == null) {
            return List.of();
        }
        LambdaQueryWrapper<ComPersonalizedUserColumn> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ComPersonalizedUserColumn::getUserId, loginUserId);
        queryWrapper.eq(ComPersonalizedUserColumn::getModuleId, moduleId);
        queryWrapper.eq(ComPersonalizedUserColumn::getProjectNo, projectNo);
        ComPersonalizedUserColumn personalizedUserColumn =
                personalizedUserColumnMapper.selectOne(queryWrapper);
        if (personalizedUserColumn == null
                || StringUtils.isEmpty(personalizedUserColumn.getColumnProperty())) {
            return List.of();
        }
        List<TableHeaderConf> confs = new ArrayList<>();
        try {
            List<PersonalizedModuleDTO> personalizedModuleDTOList =
                    JsonUtil.json2Obj(
                            personalizedUserColumn.getColumnProperty(),
                            new TypeReference<List<PersonalizedModuleDTO>>() {});

            Map<String, PersonalSettingColumnDTO> fields =
                    resp.getPersonalSettingColumns().stream()
                            .collect(
                                    Collectors.toMap(
                                            x -> x.getTable() + "." + x.getField(), x -> x));
            for (PersonalizedModuleDTO personalizedModuleDTO : personalizedModuleDTOList) {
                if (fields.containsKey(
                        personalizedModuleDTO.getTable()
                                + "."
                                + personalizedModuleDTO.getField())) {
                    TableHeaderConf conf = new TableHeaderConf();
                    conf.setName(personalizedModuleDTO.getName());
                    conf.setTable(personalizedModuleDTO.getTable());
                    conf.setField(ToolUtils.snakeToCamelCase(personalizedModuleDTO.getField()));
                    conf.setWidth(personalizedModuleDTO.getWidth());
                    conf.setSortable(personalizedModuleDTO.getSortable());
                    conf.setSearchType(personalizedModuleDTO.getSearchType());
                    conf.setFixed(personalizedModuleDTO.getFixed());
                    conf.setEllipsis(personalizedModuleDTO.getEllipsis());
                    conf.setSortOrder(personalizedModuleDTO.getSortOrder());
                    confs.add(conf);
                }
            }
            return confs;
        } catch (Exception e) {
            throw new BusinessException(
                    ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                    ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                    e);
        }
    }
}
