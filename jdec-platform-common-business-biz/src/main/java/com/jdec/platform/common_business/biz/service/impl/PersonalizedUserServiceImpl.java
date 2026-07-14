package com.jdec.platform.common_business.biz.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.jdec.platform.common_business.api.PersonalizedUserServiceApi;
import com.jdec.platform.common_business.api.dto.common.PersonalizedModuleDTO;
import com.jdec.platform.common_business.api.dto.request.PersonalizedModuleReq;
import com.jdec.platform.common_business.api.dto.response.PersonalizedModuleResp;
import com.jdec.platform.common_business.biz.dto.PersonalSettingColumnDTO;
import com.jdec.platform.common_business.biz.dto.configcenter.response.PersonalSettingResp;
import com.jdec.platform.common_business.biz.entity.PersonalizedUserColumn;
import com.jdec.platform.common_business.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.common_business.biz.mapper.PersonalizedUserColumnMapper;
import com.jdec.platform.common_business.biz.service.ConfigCenterApiService;
import com.jdec.platform.common_business.biz.util.JsonUtil;
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

    private final PersonalizedUserColumnMapper personalizedUserColumnMapper;

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
        LambdaQueryWrapper<PersonalizedUserColumn> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PersonalizedUserColumn::getUserId, userId);
        queryWrapper.in(PersonalizedUserColumn::getModuleId, moduleIds);
        queryWrapper.eq(PersonalizedUserColumn::getProjectNo, projectNo);
        List<PersonalizedUserColumn> columnList =
                personalizedUserColumnMapper.selectList(queryWrapper);
        Map<Long, PersonalizedUserColumn> columnMap = new HashMap<>();
        if (CollectionUtil.isEmpty(columnList)) {
            columnMap = columnList.stream().collect(Collectors.toMap(x -> x.getModuleId(), x -> x));
        }
        List<PersonalizedModuleResp> result = new ArrayList<>();
        try {
            for (PersonalSettingResp resp : respList) {
                PersonalizedUserColumn personalizedUserColumn = columnMap.get(resp.getId());
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
                LambdaQueryWrapper<PersonalizedUserColumn> queryWrapper = new LambdaQueryWrapper();
                queryWrapper.eq(PersonalizedUserColumn::getUserId, userId);
                queryWrapper.eq(PersonalizedUserColumn::getProjectNo, projectNo);
                queryWrapper.eq(PersonalizedUserColumn::getModuleId, req.getModuleId());
                PersonalizedUserColumn old = personalizedUserColumnMapper.selectOne(queryWrapper);
                if (old == null) {
                    PersonalizedUserColumn userColumn = new PersonalizedUserColumn();
                    userColumn.setUserId(userId);
                    userColumn.setProjectNo(projectNo);
                    userColumn.setModuleId(req.getModuleId());
                    userColumn.setColumnProperty(JsonUtil.obj2Json(req.getColumnRespList()));
                    userColumn.setCreatedBy(userId);
                    userColumn.setCreatedName(null);
                    userColumn.setCreatedDate(LocalDateTime.now());
                    userColumn.setUpdatedBy(userId);
                    userColumn.setUpdatedName(null);
                    userColumn.setUpdatedDate(LocalDateTime.now());
                    userColumn.setDeleted(Boolean.FALSE);
                    personalizedUserColumnMapper.insert(userColumn);
                } else {
                    LambdaUpdateWrapper<PersonalizedUserColumn> updateWrapper =
                            new LambdaUpdateWrapper();
                    updateWrapper.eq(PersonalizedUserColumn::getUserId, userId);
                    updateWrapper.eq(PersonalizedUserColumn::getProjectNo, projectNo);
                    updateWrapper.eq(PersonalizedUserColumn::getModuleId, req.getModuleId());
                    updateWrapper.set(PersonalizedUserColumn::getUpdatedBy, userId);
                    // updateWrapper.set(PersonalizedUserColumn::getUpdatedName)
                    updateWrapper.set(PersonalizedUserColumn::getUpdatedDate, LocalDateTime.now());
                    if (CollectionUtil.isNotEmpty(req.getColumnRespList())) {
                        updateWrapper.set(
                                PersonalizedUserColumn::getColumnProperty,
                                JsonUtil.obj2Json(req.getColumnRespList()));
                    } else {
                        updateWrapper.set(
                                PersonalizedUserColumn::getColumnProperty,
                                JsonUtil.obj2Json(List.of()));
                    }
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
            LambdaQueryWrapper<PersonalizedUserColumn> queryWrapper = new LambdaQueryWrapper();
            queryWrapper.eq(PersonalizedUserColumn::getUserId, userId);
            queryWrapper.eq(PersonalizedUserColumn::getProjectNo, projectNo);
            queryWrapper.eq(PersonalizedUserColumn::getModuleId, moduleId);
            PersonalizedUserColumn old = personalizedUserColumnMapper.selectOne(queryWrapper);
            if (old == null) {
                PersonalizedUserColumn userColumn = new PersonalizedUserColumn();
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
                LambdaUpdateWrapper<PersonalizedUserColumn> updateWrapper =
                        new LambdaUpdateWrapper();
                updateWrapper.eq(PersonalizedUserColumn::getUserId, userId);
                updateWrapper.eq(PersonalizedUserColumn::getProjectNo, projectNo);
                updateWrapper.eq(PersonalizedUserColumn::getModuleId, moduleId);
                updateWrapper.set(PersonalizedUserColumn::getUpdatedBy, userId);
                // updateWrapper.set(PersonalizedUserColumn::getUpdatedName)
                updateWrapper.set(PersonalizedUserColumn::getUpdatedDate, LocalDateTime.now());

                PersonalSettingResp settingResp = settingRespMap.get(moduleId);
                if (settingResp == null) {
                    updateWrapper.set(
                            PersonalizedUserColumn::getColumnProperty,
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
                            PersonalizedUserColumn::getColumnProperty, JsonUtil.obj2Json(rr));
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
        LambdaQueryWrapper<PersonalizedUserColumn> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PersonalizedUserColumn::getUserId, userId);
        queryWrapper.eq(PersonalizedUserColumn::getModuleId, moduleId);
        queryWrapper.eq(PersonalizedUserColumn::getProjectNo, projectNo);
        PersonalizedUserColumn personalizedUserColumn =
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
}
