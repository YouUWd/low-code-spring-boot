package com.jdec.platform.common.biz.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.config.api.SysApprovalChainConfigApi;
import com.jdec.platform.config.api.SysStatusApi;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.config.open.api.OpenMenuApi;
import com.jdec.platform.dataengine.api.DataEngineApi;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.api.dto.response.DynamicSaveResp;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.datasource.ProjectProperties;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.security.model.LoginUser;
import com.jdec.platform.validation.SubmitCheckRouter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

/** 提交接口校验入口测试：主单ID有值时查库校验，没有值时基于前端提交的数据校验。 */
class CommonApprovalServiceImplSubmitCheckTest {

    private static final Long INFO_MODULE_ID = 1001L;

    private final IApprovalEngine approvalEngine = mock(IApprovalEngine.class);

    private final OpenMenuApi openMenuApi = mock(OpenMenuApi.class);

    private final DataEngineApi dataEngineApi = mock(DataEngineApi.class);

    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final SysStatusApi sysStatusApi = mock(SysStatusApi.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final SysApprovalChainConfigApi sysApprovalChainConfigApi =
            mock(SysApprovalChainConfigApi.class);

    private final ProjectProperties projectProperties = mock(ProjectProperties.class);

    private final ConfigApi configApi = mock(ConfigApi.class);

    private final SubmitCheckRouter submitCheckRouter = mock(SubmitCheckRouter.class);

    private final CommonApprovalServiceImpl service =
            new CommonApprovalServiceImpl(
                    approvalEngine,
                    openMenuApi,
                    dataEngineApi,
                    eventPublisher,
                    sysStatusApi,
                    objectMapper,
                    sysApprovalChainConfigApi,
                    projectProperties,
                    configApi,
                    submitCheckRouter);

    @BeforeEach
    void setUp() {
        AppContext.setContext("company");
        UserContext.setLoginUser(
                LoginUser.builder().userId(1L).roleId(1L).username("admin").build());
        when(configApi.getModuleTree(eq("company"), any(), eq(INFO_MODULE_ID)))
                .thenReturn(infoModuleTree());
    }

    @AfterEach
    void tearDown() {
        AppContext.remove();
        UserContext.remove();
    }

    /** 主单ID有值：保存后按主单ID加载数据库整单数据校验。 */
    @Test
    void submitShouldCheckDatabaseWhenMainIdPresent() {
        when(dataEngineApi.save(any())).thenReturn(saved(7L));
        NestedModuleDataDTO nested = module(row("info.id", 7L, "info.company_name", "测试企业"));

        Long recordId = service.businessSubmit(req(nested, "submit"));

        assertEquals(7L, recordId);
        verify(submitCheckRouter).check(INFO_MODULE_ID, 7L, "CR001", 1);
        verify(submitCheckRouter, never()).checkOnSubmit(any(), any());
        verify(approvalEngine).handleApproval(any());
    }

    /** 主单ID没有值：落库前基于前端提交的数据校验，前端未提交的模块按校验不通过处理。 */
    @Test
    void submitShouldCheckFrontendDataBeforeSaveWhenMainIdAbsent() {
        when(dataEngineApi.save(any())).thenReturn(saved(null));
        NestedModuleDataDTO nested = module(row("info.company_name", "测试企业"));

        assertNull(service.businessSubmit(req(nested, "submit")));

        InOrder inOrder = inOrder(submitCheckRouter, dataEngineApi);
        inOrder.verify(submitCheckRouter).checkOnSubmit(INFO_MODULE_ID, nested);
        inOrder.verify(dataEngineApi).save(any());
        verify(submitCheckRouter, never()).check(any(), any(), any(), any());
        verify(approvalEngine).handleApproval(any());
    }

    /** 基于前端数据的校验不通过：不落库、不发起审批。 */
    @Test
    void submitShouldAbortWhenFrontendCheckFails() {
        NestedModuleDataDTO nested = module(row("info.company_name", "测试企业"));
        doThrow(new BusinessException("基本信息 - 统一社会信用代码"))
                .when(submitCheckRouter)
                .checkOnSubmit(INFO_MODULE_ID, nested);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.businessSubmit(req(nested, "submit")));

        assertEquals("基本信息 - 统一社会信用代码", exception.getMessage());
        verify(dataEngineApi, never()).save(any());
        verify(approvalEngine, never()).handleApproval(any());
    }

    /** 实时保存是详情页暂存子表行，不执行提交校验。 */
    @Test
    void realTimeSaveShouldSkipSubmitCheck() {
        when(dataEngineApi.save(any())).thenReturn(saved(null));
        NestedModuleDataDTO nested = module(row("member.name", "李四"));

        service.businessSubmit(req(nested, "realTime_save"));

        verify(submitCheckRouter, never()).checkOnSubmit(any(), any());
        verify(submitCheckRouter, never()).check(any(), any(), any(), any());
    }

    private static DynamicSaveResp saved(Long dataId) {
        return DynamicSaveResp.builder()
                .moduleId(INFO_MODULE_ID)
                .dataId(dataId)
                .businessNo("CR001")
                .versionNo(1)
                .build();
    }

    private static DynamicSaveReq req(NestedModuleDataDTO nested, String btnType) {
        return DynamicSaveReq.builder().nestedModuleData(nested).btnType(btnType).build();
    }

    private static NestedModuleDataDTO module(Map<String, Object> row) {
        return NestedModuleDataDTO.builder()
                .moduleId(INFO_MODULE_ID)
                .record(List.of(RecordDataDTO.builder().row(row).build()))
                .build();
    }

    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            row.put((String) pairs[i], pairs[i + 1]);
        }
        return row;
    }

    private static ModuleTreeDTO infoModuleTree() {
        return ModuleTreeDTO.builder()
                .rootModuleId(INFO_MODULE_ID)
                .rootModule(
                        ModuleInfo.builder()
                                .moduleId(INFO_MODULE_ID)
                                .parentId(0L)
                                .primaryTable("info")
                                .moduleName("外部对象")
                                .build())
                .build();
    }
}
