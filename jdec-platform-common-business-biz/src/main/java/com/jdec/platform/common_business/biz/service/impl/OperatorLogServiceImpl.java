package com.jdec.platform.common_business.biz.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.jdec.platform.common_business.api.OperatorLogServiceApi;
import com.jdec.platform.common_business.api.dto.common.*;
import com.jdec.platform.common_business.api.dto.request.OpLogReq;
import com.jdec.platform.common_business.api.dto.response.MenuListResp;
import com.jdec.platform.common_business.api.dto.response.OpLogResp;
import com.jdec.platform.common_business.biz.common.DataSourceConstants;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.ConfigCenterApiOpenFeignClient;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import com.jdec.platform.common_business.biz.entity.TableChangeLog;
import com.jdec.platform.common_business.biz.enums.FormButtonEnum;
import com.jdec.platform.common_business.biz.mapper.TableChangeLogMapper;
import com.jdec.platform.common_business.biz.service.ModuleInfoWithPermissionService;
import com.jdec.platform.common_business.biz.util.JsonUtil;
import com.jdec.platform.shared.datasource.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.STUDENT_MANAGE)
public class OperatorLogServiceImpl implements OperatorLogServiceApi {

    private final ConfigCenterApiOpenFeignClient configCenterApiClient;

    private final TableChangeLogMapper tableChangeLogMapper;

    private final ModuleInfoWithPermissionService moduleInfoWithPermissionService;

    @Override
    public List<OpLogResp> getModuleDataChangeLog(Long subjectId, Long roleId, OpLogReq req) {
        ConfigApiResponse<List<MenuListResp>> response =
                configCenterApiClient.getMenuList(subjectId, 1);
        if (!ConfigApiResponse.isSuccessResponse(response)) {
            return new ArrayList<>();
        }
        List<MenuListResp> menuRespList = response.getData();
        if (CollectionUtil.isEmpty(menuRespList)) {
            return new ArrayList<>();
        }
        // 过滤出菜单 绑定的模块
        MenuListResp targetTopMenu =
                response.getData().stream()
                        .filter(ele -> req.getMenuId().equals(ele.getId()))
                        .findFirst()
                        .orElse(null);
        if (targetTopMenu == null || targetTopMenu.getModuleId() == null) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<TableChangeLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TableChangeLog::getModuleId, targetTopMenu.getModuleId());
        queryWrapper.eq(TableChangeLog::getDataId, req.getDataId());
        queryWrapper.eq(TableChangeLog::getDeleted, Boolean.FALSE);
        queryWrapper.orderByDesc(TableChangeLog::getCreatedDate);
        List<TableChangeLog> changeLogList = tableChangeLogMapper.selectList(queryWrapper);
        if (CollectionUtil.isEmpty(changeLogList)) {
            return new ArrayList<>();
        }
        List<OpLogResp> opLogResps = new ArrayList<>();
        for (TableChangeLog changeLog : changeLogList) {
            if (StringUtils.isEmpty(changeLog.getChangeContent())) {
                continue;
            }
            OpLogResp logResp = new OpLogResp();
            BeanUtil.copyProperties(
                    changeLog, logResp, CopyOptions.create().ignoreNullValue().ignoreError());
            logResp.setModifyModules(null);

            List<ModuleDataChangeDTO> modifyModules = null;
            try {
                modifyModules =
                        JsonUtil.json2Obj(
                                changeLog.getChangeContent(),
                                new TypeReference<List<ModuleDataChangeDTO>>() {});
            } catch (Exception e) {
                log.error(" json parse error,e=" + e);
            }
            if (CollectionUtil.isEmpty(modifyModules)) {
                continue;
            }
            List<ModuleDataChangeDTO> moduleDataList = new ArrayList<>();
            Map<Long, Map<String, Integer>> moduleMap = new HashMap<>();
            for (ModuleDataChangeDTO moduleDataChangeDTO : modifyModules) {
                DataChangeActionDTO dataChangeActionDTO = moduleDataChangeDTO.getActions();
                // 没有变更字段
                if (dataChangeActionDTO == null
                        || (CollectionUtil.isEmpty(dataChangeActionDTO.getA())
                                && CollectionUtil.isEmpty(dataChangeActionDTO.getU())
                                && CollectionUtil.isEmpty(dataChangeActionDTO.getD()))) {
                    continue;
                }
                if (!moduleMap.containsKey(moduleDataChangeDTO.getMId())) {
                    ModuleFilterInfo moduleFilterInfo =
                            moduleInfoWithPermissionService.filterModuleInfo(
                                    subjectId,
                                    roleId,
                                    moduleDataChangeDTO.getMId(),
                                    FormButtonEnum.VIEW.getKey(),
                                    List.of());
                    Map<String, Integer> fieldMap = new HashMap<>();
                    if (CollectionUtil.isNotEmpty(moduleFilterInfo.getSimpleModuleFields())) {
                        for (ModuleField field : moduleFilterInfo.getSimpleModuleFields()) {
                            fieldMap.put(
                                    field.getTableName() + "." + field.getFieldCode(),
                                    field.getPermissionCode());
                        }
                    }
                    moduleMap.put(moduleDataChangeDTO.getMId(), fieldMap);
                }
                Map<String, Integer> fieldMap = moduleMap.get(moduleDataChangeDTO.getMId());

                List<RowChangeDTO> insertRows =
                        getFilterRowChangeDTO(dataChangeActionDTO.getA(), fieldMap);
                List<RowChangeDTO> updateRows =
                        getFilterRowChangeDTO(dataChangeActionDTO.getU(), fieldMap);
                List<RowChangeDTO> deleteRows =
                        getFilterRowChangeDTO(dataChangeActionDTO.getD(), fieldMap);

                // 过滤权限后 没有变更字段了
                if (CollectionUtil.isEmpty(insertRows)
                        && CollectionUtil.isEmpty(updateRows)
                        && CollectionUtil.isEmpty(deleteRows)) {
                    continue;
                }
                ModuleDataChangeDTO mChangeDTO = new ModuleDataChangeDTO();
                mChangeDTO.setName(moduleDataChangeDTO.getName());
                mChangeDTO.setMId(moduleDataChangeDTO.getMId());
                DataChangeActionDTO actionDTO = new DataChangeActionDTO();
                actionDTO.setA(insertRows);
                actionDTO.setU(updateRows);
                actionDTO.setD(deleteRows);
                mChangeDTO.setActions(actionDTO);
                moduleDataList.add(mChangeDTO);
            }

            if (CollectionUtil.isEmpty(moduleDataList)) {
                continue;
            }
            logResp.setModifyModules(moduleDataList);
            opLogResps.add(logResp);
        }
        return opLogResps;
    }

    private List<RowChangeDTO> getFilterRowChangeDTO(
            List<RowChangeDTO> rowChangeDTOS, Map<String, Integer> fieldMap) {
        if (CollectionUtil.isEmpty(rowChangeDTOS) || CollectionUtil.isEmpty(fieldMap)) {
            return null;
        }

        List<RowChangeDTO> result = new ArrayList<>();
        for (RowChangeDTO rowChangeDTO : rowChangeDTOS) {
            if (CollectionUtil.isEmpty(rowChangeDTO.getColumns())) {
                continue;
            }
            // 过滤没有权限的字段
            List<FieldChangeDTO> fieldChangeDTOS =
                    rowChangeDTO.getColumns().stream()
                            .filter(
                                    x ->
                                            fieldMap.get(x.getField()) != null
                                                    && fieldMap.get(x.getField()) >= 1)
                            .collect(Collectors.toList());
            if (CollectionUtil.isEmpty(fieldChangeDTOS)) {
                continue;
            }
            RowChangeDTO newRow = new RowChangeDTO();
            newRow.setName(rowChangeDTO.getName());
            newRow.setColumns(fieldChangeDTOS);
            result.add(newRow);
        }
        return result;
    }
}
