package com.jdec.platform.common_business.biz.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.jdec.platform.common_business.api.dto.common.ModuleFilterInfo;
import com.jdec.platform.common_business.api.dto.request.ModuleSaveReq;
import com.jdec.platform.common_business.biz.dto.BizWechatMsgDTO;
import com.jdec.platform.common_business.biz.dto.WechatMsgParameterDTO;
import com.jdec.platform.common_business.biz.dto.WechatTemplateDetailDTO;
import com.jdec.platform.common_business.biz.dto.configcenter.response.CombineField;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ModuleInfoResp;
import com.jdec.platform.common_business.biz.dto.configcenter.response.SimpleField;
import com.jdec.platform.common_business.biz.dto.configcenter.response.SourceMapping;
import com.jdec.platform.common_business.biz.enums.BizMsgEnum;
import com.jdec.platform.common_business.biz.log.ComBizMsgEventPublisher;
import com.jdec.platform.common_business.biz.service.ConfigCenterApiService;
import com.jdec.platform.common_business.biz.service.SystemModuleApprovalMsgService;
import com.jdec.platform.common_business.biz.util.LoginUserUtil;
import com.jdec.platform.config.api.dto.response.WechatTemplateParamEntryResp;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemModuleApprovalMsgServiceImpl implements SystemModuleApprovalMsgService {

    private final ConfigCenterApiService configCenterApiService;

    @Override
    public Boolean processApprovalMsg(
            Long subjectId,
            List<Map<String, Object>> processResult,
            ModuleFilterInfo moduleFilterInfo,
            List<ModuleSaveReq> moduleSaveReqList) {
        try {
            // 模块数据
            Map<String, Object> moduleData =
                    moduleSaveReqList.stream()
                            .filter(
                                    x ->
                                            x.getModuleId()
                                                    .equals(
                                                            moduleFilterInfo
                                                                    .getModuleInfo()
                                                                    .getModuleId()))
                            .findFirst()
                            .get()
                            .getModuleData()
                            .get(0);
            List<WechatMsgParameterDTO> parameterDTOList = convert(processResult);
            Map<Long, ModuleInfoResp> moduleInfoRespMap = new HashMap<>();
            for (WechatMsgParameterDTO parameterDTO : parameterDTOList) {
                WechatTemplateDetailDTO wechatTemplateDetailDTO =
                        configCenterApiService.getWechatTemplateDetail(
                                subjectId, parameterDTO.getTemplateId());
                if (wechatTemplateDetailDTO == null
                        || wechatTemplateDetailDTO.getWechatTemplateDetailResp() == null
                        || CollectionUtil.isEmpty(
                                wechatTemplateDetailDTO.getTemplateAcceptTypeEnum())) {
                    log.warn(
                            "processApprovalMsg wechatTemplate is empty or Acceptors is empty TemplateId={}",
                            parameterDTO.getTemplateId());
                    continue;
                }
                List<WechatTemplateParamEntryResp> paramEntryRespList =
                        wechatTemplateDetailDTO.getTemplateParamEntryRespList();
                Map<String, String> bizParam = new HashMap<>();
                if (CollectionUtil.isNotEmpty(paramEntryRespList)) {
                    for (WechatTemplateParamEntryResp resp : paramEntryRespList) {
                        // 只处理 关联了模块字段的 参数
                        if (resp.getFieldId() == null || resp.getFieldId() <= 0) {
                            continue;
                        }
                        if (!moduleInfoRespMap.containsKey(resp.getModuleId())) {
                            ModuleInfoResp moduleInfoResp =
                                    configCenterApiService.getModuleInfoComplete(
                                            subjectId, resp.getModuleId());
                            moduleInfoRespMap.put(resp.getModuleId(), moduleInfoResp);
                        }
                        ModuleInfoResp moduleInfo = moduleInfoRespMap.get(resp.getModuleId());

                        SimpleField simpleField =
                                moduleInfo.getSimpleFields().stream()
                                        .filter(
                                                x ->
                                                        resp.getFieldId()
                                                                .equals(x.getId().longValue()))
                                        .findFirst()
                                        .orElse(null);
                        // 简单字段处理
                        if (simpleField != null) {
                            String value =
                                    convertSimpleFieldParam(
                                            simpleField.getTableName(),
                                            simpleField.getColumnName(),
                                            moduleData,
                                            (resp.getConvertFlag() != null
                                                    && resp.getConvertFlag().intValue() == 1),
                                            resp.getTemplateParamConvertMethod());
                            if (value != null) {
                                bizParam.put(resp.getTemplateParamSlug(), value);
                                continue;
                            }
                        }

                        CombineField combineField =
                                moduleInfo.getCombineFields().stream()
                                        .filter(
                                                x ->
                                                        resp.getFieldId()
                                                                .equals(x.getId().longValue()))
                                        .findFirst()
                                        .orElse(null);
                        if (combineField != null
                                && CollectionUtil.isNotEmpty(combineField.getSourceMapping())) {
                            combineField
                                    .getSourceMapping()
                                    .sort(Comparator.comparing(SourceMapping::getSortOrder));
                            List<String> tableName = new ArrayList<>();
                            List<String> fieldNames = new ArrayList<>();
                            for (SourceMapping sm : combineField.getSourceMapping()) {
                                tableName.add(sm.getEntity());
                                fieldNames.add(sm.getField());
                            }
                            String value =
                                    convertCombineFieldParam(
                                            tableName,
                                            fieldNames,
                                            moduleData,
                                            (resp.getConvertFlag() != null
                                                    && resp.getConvertFlag().intValue() == 1),
                                            resp.getTemplateParamConvertMethod());
                            if (value != null) {
                                bizParam.put(resp.getTemplateParamSlug(), value);
                            }
                        }
                    }
                }
                Map<String, String> param = new HashMap<>();
                param.putAll(parameterDTO.getApprovalParam());
                param.putAll(bizParam);

                BizWechatMsgDTO bizWechatMsgDTO =
                        BizWechatMsgDTO.builder()
                                .token(LoginUserUtil.getTokenFromHeader())
                                .subjectId(subjectId)
                                .wechatTemplate(wechatTemplateDetailDTO)
                                .acceptorList(parameterDTO.getAcceptorList())
                                .param(param)
                                .build();
                ComBizMsgEventPublisher.publishMsgEvent(BizMsgEnum.APPROVAL_CHAIN, bizWechatMsgDTO);
            }
            return Boolean.TRUE;
        } catch (Exception e) {
            log.error("processApprovalMsg error param=" + processResult.toString() + ",e=", e);
        }
        return Boolean.FALSE;
    }

    private List<WechatMsgParameterDTO> convert(List<Map<String, Object>> processResult) {
        List<WechatMsgParameterDTO> wechatMsgParameterList = new ArrayList<>();
        for (Map<String, Object> map : processResult) {
            WechatMsgParameterDTO dto = new WechatMsgParameterDTO();
            dto.setApprovalParam((Map<String, String>) map.get("params"));
            List<String> receivers = (List<String>) map.get("receivers");
            if (CollectionUtil.isNotEmpty(receivers)) {
                dto.setAcceptorList(
                        receivers.stream().map(x -> Long.valueOf(x)).collect(Collectors.toList()));
            }
            dto.setTemplateId(Long.valueOf(map.get("templateId").toString()));
            wechatMsgParameterList.add(dto);
        }
        return wechatMsgParameterList;
    }

    private String convertSimpleFieldParam(
            String tableName,
            String field,
            Map<String, Object> moduleData,
            Boolean isCovert,
            String method) {
        if (!isCovert) {
            Object o = moduleData.get(tableName + "." + StrUtil.toCamelCase(field));
            if (o == null) {
                return null;
            }
            return o.toString();
        } else {
            return null;
        }
    }

    private String convertCombineFieldParam(
            List<String> tableNames,
            List<String> fields,
            Map<String, Object> moduleData,
            Boolean isCovert,
            String method) {
        if (!isCovert) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < tableNames.size(); i++) {
                Object o =
                        moduleData.get(
                                tableNames.get(i) + "." + StrUtil.toCamelCase(fields.get(i)));
                if (o != null) {
                    if (sb.length() > 0) {
                        sb.append(" ");
                    }
                    sb.append(o.toString());
                }
            }
            return sb.toString();
        } else {
            return null;
        }
    }
}
