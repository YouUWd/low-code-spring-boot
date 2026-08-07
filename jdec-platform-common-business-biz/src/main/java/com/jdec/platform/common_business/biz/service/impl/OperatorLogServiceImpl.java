package com.jdec.platform.common_business.biz.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.jdec.platform.common_business.api.OperatorLogServiceApi;
import com.jdec.platform.common_business.api.dto.common.*;
import com.jdec.platform.common_business.api.dto.request.OpLogReq;
import com.jdec.platform.common_business.api.dto.response.MenuListResp;
import com.jdec.platform.common_business.api.dto.response.OpLogResp;
import com.jdec.platform.common_business.api.dto.response.UserResp;
import com.jdec.platform.common_business.biz.common.DataSourceConstants;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.ConfigCenterApiOpenFeignClient;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.MonitorLogApiOpenFeignClient;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import com.jdec.platform.common_business.biz.enums.FormButtonEnum;
import com.jdec.platform.common_business.biz.service.ModuleInfoWithPermissionService;
import com.jdec.platform.common_business.biz.util.JsonUtil;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogPageListResp;
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

    private final MonitorLogApiOpenFeignClient monitorLogApiOpenFeignClient;

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
        ConfigApiResponse<List<MonitorLogPageListResp>> logResponse =
                monitorLogApiOpenFeignClient.querySystemControlLogByModuleIdAndDataId(
                        targetTopMenu.getModuleId(), req.getDataId(), null);
        if (!ConfigApiResponse.isSuccessResponse(logResponse)
                || CollectionUtil.isEmpty(logResponse.getData())) {
            return new ArrayList<>();
        }
        List<OpLogResp> result = new ArrayList<>();
        for (MonitorLogPageListResp resp : logResponse.getData()) {
            if (StringUtils.isEmpty(resp.getLogRemark())) {
                continue;
            }
            OpLogResp logResp = new OpLogResp();
            logResp.setActionName(resp.getActionName());
            logResp.setActionPath(resp.getActionPath());
            logResp.setActionTypeTitle(resp.getActionTypeTitle());
            logResp.setCreatedDate(resp.getLogDate());

            if (resp.getMockFlag().equals(Integer.valueOf(1))) {
                UserResp mockUser = new UserResp();
                mockUser.setId(resp.getMockUserId());
                mockUser.setUserName(resp.getMockUserName());
                mockUser.setDepartName(resp.getLoginUserDeptName());
                mockUser.setUserAvatar(resp.getMockUserAvater());
                mockUser.setWorkNumber(resp.getMockUserNumber());
                mockUser.setPositionName(resp.getLoginUserJobName());
                logResp.setCreatedUser(mockUser);

                UserResp real = new UserResp();
                real.setId(resp.getLoginUserId());
                real.setUserName(resp.getLoginUserName());
                real.setUserAvatar(resp.getLoginAvater());
                real.setWorkNumber(resp.getLoginUserNumber());
                logResp.setRealUser(real);
            } else {
                UserResp real = new UserResp();
                real.setId(resp.getLoginUserId());
                real.setUserName(resp.getLoginUserName());
                real.setUserAvatar(resp.getLoginAvater());
                real.setWorkNumber(resp.getLoginUserNumber());
                real.setDepartName(resp.getLoginUserDeptName());
                real.setPositionName(resp.getLoginUserJobName());
                logResp.setRealUser(real);
                logResp.setCreatedUser(real);
            }
            DataAuditContentDTO dataAuditContent = null;
            try {
                dataAuditContent =
                        JsonUtil.json2Obj(
                                resp.getLogRemark(), new TypeReference<DataAuditContentDTO>() {});
            } catch (Exception e) {
                log.error(" json parse error,e=" + e);
            }
            if (dataAuditContent == null || CollectionUtil.isEmpty(dataAuditContent.getChange())) {
                continue;
            }
            List<ModuleDataChangeDTO> moduleDataList = new ArrayList<>();
            Map<Long, Map<String, Integer>> moduleMap = new HashMap<>();
            for (ModuleDataChangeDTO moduleDataChangeDTO : dataAuditContent.getChange()) {
                DataChangeActionDTO dataChangeActionDTO = moduleDataChangeDTO.getActions();
                // 没有变更字段
                if (dataChangeActionDTO == null
                        || (CollectionUtil.isEmpty(dataChangeActionDTO.getI())
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
                        getFilterRowChangeDTO(dataChangeActionDTO.getI(), fieldMap);
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
                actionDTO.setI(insertRows);
                actionDTO.setU(updateRows);
                actionDTO.setD(deleteRows);
                mChangeDTO.setActions(actionDTO);
                moduleDataList.add(mChangeDTO);
            }

            if (CollectionUtil.isEmpty(moduleDataList)) {
                continue;
            }
            logResp.setDescription(
                    DataAuditContentDTO.builder()
                            .change(moduleDataList)
                            .reason(dataAuditContent.getReason())
                            .build());
            result.add(logResp);
        }
        return result;
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
