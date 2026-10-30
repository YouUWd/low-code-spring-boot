package com.jdec.platform.externalobject.biz.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jdec.platform.externalobject.api.ExternalObjectRealtimeValidateApi;
import com.jdec.platform.externalobject.api.dto.request.CompanyRegStatusUpdateReq;
import com.jdec.platform.externalobject.api.dto.request.ExternalObjectRealtimeValidateReq;
import com.jdec.platform.externalobject.api.dto.response.ExternalObjectRealtimeValidateResp;
import com.jdec.platform.externalobject.biz.entity.Info;
import com.jdec.platform.externalobject.biz.mapper.InfoMapper;
import com.jdec.platform.externalobject.biz.service.CompanyCustomService;
import com.jdec.platform.externalobject.biz.service.impl.CompanyCustomServiceImpl;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.security.model.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CompanyCustomControllerTest {

    private InfoMapper infoMapper;
    private ExternalObjectRealtimeValidateApi realtimeValidateApi;
    private CompanyCustomServiceImpl service;
    private CompanyCustomController controller;

    @BeforeEach
    void setUp() {
        UserContext.setLoginUser(
                LoginUser.builder().userId(999L).roleId(1L).username("admin").build());

        infoMapper = mock(InfoMapper.class);
        realtimeValidateApi = mock(ExternalObjectRealtimeValidateApi.class);
        when(realtimeValidateApi.validate(any()))
                .thenReturn(ExternalObjectRealtimeValidateResp.ok());

        service = new CompanyCustomServiceImpl(infoMapper, realtimeValidateApi);
        controller = new CompanyCustomController(service);
    }

    @Test
    @DisplayName("Controller 端点 updateRegStatus 成功委托 Service 并返回 ApiResponse.success()")
    void testControllerUpdateRegStatus() {
        CompanyCustomService mockService = mock(CompanyCustomService.class);
        CompanyCustomController testController = new CompanyCustomController(mockService);

        CompanyRegStatusUpdateReq req =
                CompanyRegStatusUpdateReq.builder().id(100L).regStatus("存续").build();

        ApiResponse<Void> resp = testController.updateRegStatus(req);
        assertThat(resp).isNotNull();
        assertThat(resp.getStatus()).isEqualTo(200);
        verify(mockService).updateRegStatus(req);
    }

    @Test
    @DisplayName("参数校验: 请求体为空、id为空、regStatus为空白时抛出 IllegalArgumentException")
    void testValidateEmptyParams() {
        assertThatThrownBy(() -> service.updateRegStatus(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("请求参数不能为空");

        CompanyRegStatusUpdateReq noIdReq =
                CompanyRegStatusUpdateReq.builder().regStatus("存续").build();
        assertThatThrownBy(() -> service.updateRegStatus(noIdReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("单据 ID 不能为空");

        CompanyRegStatusUpdateReq blankStatusReq =
                CompanyRegStatusUpdateReq.builder().id(100L).regStatus("  ").build();
        assertThatThrownBy(() -> service.updateRegStatus(blankStatusReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("登记状态 regStatus 不能为空");
    }

    @Test
    @DisplayName("单据不存在或已被软删除时抛出 BusinessException")
    void testRecordNotFound() {
        when(infoMapper.selectById(100L)).thenReturn(null);

        CompanyRegStatusUpdateReq req =
                CompanyRegStatusUpdateReq.builder().id(100L).regStatus("存续").build();

        assertThatThrownBy(() -> service.updateRegStatus(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到对应的外部对象单据记录");

        Info deletedInfo = new Info();
        deletedInfo.setId(101L);
        deletedInfo.setDeleted(1);
        when(infoMapper.selectById(101L)).thenReturn(deletedInfo);

        CompanyRegStatusUpdateReq deletedReq =
                CompanyRegStatusUpdateReq.builder().id(101L).regStatus("存续").build();
        assertThatThrownBy(() -> service.updateRegStatus(deletedReq))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到对应的外部对象单据记录");
    }

    @Test
    @DisplayName("单据状态不为 99(启用) 且不为 -99(取消) 时抛出 BusinessException 拦截")
    void testInvalidBusinessStatus() {
        Info info = new Info();
        info.setId(100L);
        info.setBusinessStatus(1); // 待审核/草稿
        info.setDeleted(0);
        when(infoMapper.selectById(100L)).thenReturn(info);

        CompanyRegStatusUpdateReq req =
                CompanyRegStatusUpdateReq.builder().id(100L).regStatus("存续").build();

        assertThatThrownBy(() -> service.updateRegStatus(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有处于【已启用 (99)】或【已取消 (-99)】状态的单据才允许修改登记状态");
    }

    @Test
    @DisplayName("登记状态与对象性质校验不通过时抛出 BusinessException 拦截")
    void testValidateRegStatusFails() {
        Info info = new Info();
        info.setId(100L);
        info.setNature("[{\"word_describe\":\"个体工商户\"}]");
        info.setBusinessStatus(99);
        info.setDeleted(0);
        when(infoMapper.selectById(100L)).thenReturn(info);

        when(realtimeValidateApi.validate(any(ExternalObjectRealtimeValidateReq.class)))
                .thenReturn(ExternalObjectRealtimeValidateResp.fail("个体工商户只能选择存续、注销"));

        CompanyRegStatusUpdateReq req =
                CompanyRegStatusUpdateReq.builder().id(100L).regStatus("吊销").build();

        assertThatThrownBy(() -> service.updateRegStatus(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("个体工商户只能选择存续、注销");
    }

    @Test
    @DisplayName("启用(99)状态单据基于 ID 成功更新登记状态")
    void testUpdateRegStatusById_Active_Success() {
        Info info = new Info();
        info.setId(100L);
        info.setBusinessNo("CR202610300001");
        info.setBusinessStatus(99);
        info.setNature("[{\"word_describe\":\"企业\"}]");
        info.setRegStatus("存续");
        info.setDeleted(0);
        when(infoMapper.selectById(100L)).thenReturn(info);

        CompanyRegStatusUpdateReq req =
                CompanyRegStatusUpdateReq.builder().id(100L).regStatus("注销").build();

        service.updateRegStatus(req);

        ArgumentCaptor<Info> captor = ArgumentCaptor.forClass(Info.class);
        verify(infoMapper).updateById(captor.capture());
        Info updated = captor.getValue();
        assertThat(updated.getRegStatus()).isEqualTo("注销");
        assertThat(updated.getUpdatedBy()).isEqualTo(999L);
        assertThat(updated.getUpdatedDate()).isNotNull();
    }

    @Test
    @DisplayName("取消(-99)状态单据基于 ID 成功更新登记状态")
    void testUpdateRegStatusById_Cancel_Success() {
        Info info = new Info();
        info.setId(200L);
        info.setBusinessNo("CR202610300002");
        info.setVersionNo(2);
        info.setBusinessStatus(-99);
        info.setNature("[{\"word_describe\":\"企业\"}]");
        info.setRegStatus("存续");
        info.setDeleted(0);
        when(infoMapper.selectById(200L)).thenReturn(info);

        CompanyRegStatusUpdateReq req =
                CompanyRegStatusUpdateReq.builder().id(200L).regStatus("注销").build();

        service.updateRegStatus(req);

        ArgumentCaptor<Info> captor = ArgumentCaptor.forClass(Info.class);
        verify(infoMapper).updateById(captor.capture());
        Info updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(200L);
        assertThat(updated.getRegStatus()).isEqualTo("注销");
        assertThat(updated.getUpdatedBy()).isEqualTo(999L);
        assertThat(updated.getUpdatedDate()).isNotNull();
    }
}
