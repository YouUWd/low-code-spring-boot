package com.jdec.platform.common.biz.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.common.biz.preprocess.SavePrepositionProcessorService;
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
import com.jdec.platform.shared.datasource.ProjectProperties;
import com.jdec.platform.validation.SubmitCheckRouter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** 提交校验路由：变更提交按保存口径在落库前校验，普通提交仍按整单校验。 */
class CommonApprovalServiceImplTest {

    private final IApprovalEngine approvalEngine = mock(IApprovalEngine.class);

    private final OpenMenuApi openMenuApi = mock(OpenMenuApi.class);

    private final DataEngineApi dataEngineApi = mock(DataEngineApi.class);

    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final SysStatusApi sysStatusApi = mock(SysStatusApi.class);

    private final SysApprovalChainConfigApi sysApprovalChainConfigApi =
            mock(SysApprovalChainConfigApi.class);

    private final ConfigApi configApi = mock(ConfigApi.class);

    private final SubmitCheckRouter submitCheckRouter = mock(SubmitCheckRouter.class);

    private final SavePrepositionProcessorService savePrepositionProcessorService =
            mock(SavePrepositionProcessorService.class);

    private final CommonApprovalServiceImpl service =
            new CommonApprovalServiceImpl(
                    approvalEngine,
                    openMenuApi,
                    dataEngineApi,
                    eventPublisher,
                    sysStatusApi,
                    new ObjectMapper(),
                    sysApprovalChainConfigApi,
                    new ProjectProperties(),
                    configApi,
                    submitCheckRouter,
                    savePrepositionProcessorService);

    /** 变更提交（change_save）：落库前按保存口径校验，查重排除原单据；落库后不再整单校验 */
    @Test
    void changeSubmitShouldCheckOnSaveBeforeStoreAndSkipWholeOrderCheck() {
        NestedModuleDataDTO nestedModuleData = nestedModuleData();
        mockModuleTree();
        when(dataEngineApi.save(any(DynamicSaveReq.class))).thenReturn(saved());

        service.businessSubmit(submitReq(nestedModuleData, "change_save"));

        verify(submitCheckRouter).checkOnSave(eq(1001L), eq(nestedModuleData), eq(12L));
        verify(submitCheckRouter, never()).check(any(), any(), any(), any());
        verify(submitCheckRouter, never()).checkOnSubmit(any(), any());
    }

    /** 普通提交（主单ID有值）：仍按落库后的整单数据校验 */
    @Test
    void normalSubmitWithMainRecordShouldCheckWholeOrder() {
        NestedModuleDataDTO nestedModuleData = nestedModuleData();
        mockModuleTree();
        when(dataEngineApi.save(any(DynamicSaveReq.class))).thenReturn(saved());

        service.businessSubmit(submitReq(nestedModuleData, null));

        verify(submitCheckRouter).check(eq(1001L), eq(99L), eq("B001"), eq(2));
        verify(submitCheckRouter, never()).checkOnSave(any(), any(), any());
    }

    private void mockModuleTree() {
        when(configApi.getModuleTree(any(), any(), eq(1001L)))
                .thenReturn(
                        ModuleTreeDTO.builder()
                                .rootModuleId(1001L)
                                .rootModule(
                                        ModuleInfo.builder()
                                                .moduleId(1001L)
                                                .primaryTable("info")
                                                .build())
                                .build());
    }

    private static NestedModuleDataDTO nestedModuleData() {
        return NestedModuleDataDTO.builder()
                .moduleId(1001L)
                .record(List.of(RecordDataDTO.builder().row(Map.of("info.id", 12L)).build()))
                .build();
    }

    private static DynamicSaveReq submitReq(NestedModuleDataDTO nestedModuleData, String btnType) {
        return DynamicSaveReq.builder().nestedModuleData(nestedModuleData).btnType(btnType).build();
    }

    private static DynamicSaveResp saved() {
        return DynamicSaveResp.builder()
                .moduleId(1001L)
                .dataId(99L)
                .businessNo("B001")
                .versionNo(2)
                .build();
    }
}
