package com.jdec.platform.common_business.biz.log.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.common_business.api.dto.response.MenuListResp;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.ConfigCenterApiOpenFeignClient;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.HrUserApiOpenFeignClient;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.MonitorLogApiOpenFeignClient;
import com.jdec.platform.common_business.biz.context.TokenContextHolder;
import com.jdec.platform.common_business.biz.dto.BizLogDTO;
import com.jdec.platform.common_business.biz.dto.LogContentDTO;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import com.jdec.platform.common_business.biz.enums.BizLogEnum;
import com.jdec.platform.common_business.biz.log.BizLog;
import com.jdec.platform.common_business.biz.util.JsonUtil;
import com.jdec.platform.config.api.dto.response.RoleConfigDetailResp;
import com.jdec.platform.config.api.dto.response.SubjectOptionResp;
import com.jdec.platform.hr.api.bo.UserBO;
import com.jdec.platform.monitorlog.api.dto.request.MonitorControlLogReq;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class DataUpdateBizLog implements BizLog {
    private final Set<BizLogEnum> referSets = Set.of(BizLogEnum.DATA_SAVE, BizLogEnum.DATA_UPDATE);

    private final MonitorLogApiOpenFeignClient monitorLogApiOpenFeignClient;

    private final ConfigCenterApiOpenFeignClient configCenterApiOpenFeignClient;

    private final HrUserApiOpenFeignClient hrUserApiOpenFeignClient;

    @Override
    public Boolean referToBizLog(BizLogEnum logEnum) {
        return referSets.contains(logEnum);
    }

    @Override
    public Boolean processBizLog(BizLogEnum logEnum, Object bizLog) {
        BizLogDTO bizLogDTO = (BizLogDTO) bizLog;
        if (bizLogDTO != null) {
            try {
                TokenContextHolder.setCurrentUserToken(bizLogDTO.getToken());
                MonitorControlLogReq req = getCommonReq(bizLogDTO);
                ConfigApiResponse<List<SubjectOptionResp>> subjectResponse =
                        configCenterApiOpenFeignClient.getAllSubjects(bizLogDTO.getSubjectId());
                if (ConfigApiResponse.isSuccessResponse(subjectResponse)
                        && CollectionUtil.isNotEmpty(subjectResponse.getData())) {
                    SubjectOptionResp resp =
                            subjectResponse.getData().stream()
                                    .filter(x -> x.getSubjectId().equals(bizLogDTO.getSubjectId()))
                                    .findFirst()
                                    .orElse(null);
                    if (resp != null) {
                        req.setSubjectName(resp.getSubjectName());
                    }
                }
                req.setActionName("已提交");
                req.setActionPath("http://192.168.60.8:9001/jdec-platform/image/create.svg");
                req.setActionTypeTitle(bizLogDTO.getModuleName() + "保存");

                Set<Long> userIds = new HashSet<>();
                userIds.add(bizLogDTO.getLoginUserId());
                userIds.add(bizLogDTO.getMockUserId());
                ConfigApiResponse<List<UserBO>> userResponse =
                        hrUserApiOpenFeignClient.listByIds(userIds);
                Map<Long, UserBO> userMap = new HashMap<>();
                if (ConfigApiResponse.isSuccessResponse(userResponse)
                        && CollectionUtil.isNotEmpty(userResponse.getData())) {
                    userMap =
                            userResponse.getData().stream()
                                    .collect(Collectors.toMap(x -> x.getId(), x -> x));
                }
                req.setLoginUserId(bizLogDTO.getLoginUserId());
                UserBO loginUserBO = userMap.get(bizLogDTO.getLoginUserId());
                if (loginUserBO != null) {
                    req.setLoginUserName(loginUserBO.getUserName());
                    req.setLoginAvater(loginUserBO.getUserAvatar());
                    req.setLoginUserNumber(loginUserBO.getWorkNumber());
                    req.setLoginUserDeptId(loginUserBO.getDepartmentId());
                    req.setLoginUserDeptName(loginUserBO.getDepartmentName());
                    req.setLoginUserJobId(loginUserBO.getJobId());
                    req.setLoginUserJobName(loginUserBO.getJobName());
                }
                Boolean mockFlag =
                        bizLogDTO.getLoginUserId() != null
                                && bizLogDTO.getMockUserId() != null
                                && !bizLogDTO.getMockUserId().equals(bizLogDTO.getLoginUserId());

                ConfigApiResponse<RoleConfigDetailResp> roleResponse =
                        configCenterApiOpenFeignClient.getRoleConfigDetail(
                                bizLogDTO.getSubjectId(), bizLogDTO.getRoleId());
                if (mockFlag) {
                    req.setMockFlag(1);
                    req.setMockUserId(bizLogDTO.getMockUserId());
                    UserBO mockUserBO = userMap.get(bizLogDTO.getMockUserId());
                    if (mockUserBO != null) {
                        req.setMockUserName(mockUserBO.getUserName());
                        req.setMockUserAvater(mockUserBO.getUserAvatar());
                        req.setMockUserNumber(mockUserBO.getWorkNumber());

                        req.setLoginUserDeptId(mockUserBO.getDepartmentId());
                        req.setLoginUserDeptName(mockUserBO.getDepartmentName());
                        req.setLoginUserJobId(mockUserBO.getJobId());
                        req.setLoginUserJobName(mockUserBO.getJobName());
                    }

                } else {
                    req.setMockFlag(0);
                }
                req.setLoginRoleId(bizLogDTO.getRoleId());
                if (roleResponse != null && roleResponse.getData() != null) {
                    req.setLoginRoleName(roleResponse.getData().getRoleName());
                }

                req.setModuleId(bizLogDTO.getModuleId());
                req.setModuleName(bizLogDTO.getModuleName());
                req.setOrderId("");
                req.setMenuId(bizLogDTO.getMenuId());
                req.setDataId(Long.valueOf(bizLogDTO.getDataId()));
                req.setLogRemark(bizLogDTO.getLogRemark());

                String menuTitle = "";
                ConfigApiResponse<List<MenuListResp>> resp =
                        configCenterApiOpenFeignClient.getMenuList(
                                bizLogDTO.getSubjectId(), Integer.valueOf("1"));
                if (ConfigApiResponse.isSuccessResponse(resp)
                        && CollectionUtil.isNotEmpty(resp.getData())) {
                    MenuListResp menu =
                            resp.getData().stream()
                                    .filter(x -> x.getId().equals(bizLogDTO.getMenuId()))
                                    .findFirst()
                                    .orElse(null);
                    if (menu != null) {
                        menuTitle = menu.getParam();
                        req.setMenuName(menu.getTitle());
                    }
                }
                String hrefUrl = null;
                if (bizLogDTO.getOrigin() != null) {
                    if (bizLogDTO.getOrigin().endsWith("/")) {
                        hrefUrl = bizLogDTO.getOrigin() + menuTitle + "/" + bizLogDTO.getDataId();
                    } else {
                        hrefUrl =
                                bizLogDTO.getOrigin()
                                        + "/"
                                        + menuTitle
                                        + "/"
                                        + bizLogDTO.getDataId();
                    }
                }
                req.setOrderId(bizLogDTO.getDataId());

                LogContentDTO logContentDTO =
                        LogContentDTO.builder()
                                .module(bizLogDTO.getModuleName())
                                .orderId(bizLogDTO.getDataId())
                                .hrefUrl(hrefUrl)
                                .build();
                if (logEnum == BizLogEnum.DATA_SAVE) {
                    logContentDTO.setControlName("新增数据");
                    req.setControlName("新增数据");
                } else {
                    logContentDTO.setControlName("修改数据");
                    req.setControlName("修改数据");
                }
                req.setLogContent(JsonUtil.obj2Json(logContentDTO));
                monitorLogApiOpenFeignClient.recordBusinessLog(req);
                return Boolean.TRUE;
            } catch (Exception e) {
                log.error("recordBizLog logEnum={},log={},e=", logEnum, bizLog.toString(), e);
            } finally {
                TokenContextHolder.clear();
            }
        }
        return Boolean.FALSE;
    }
}
