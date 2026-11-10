package com.jdec.platform.externalobject.biz.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jdec.platform.externalobject.api.dto.response.MemberTreeNodeResp;
import com.jdec.platform.externalobject.biz.service.CompanyCustomService;
import com.jdec.platform.shared.model.ApiResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyCustomControllerTest {

    @Mock private CompanyCustomService companyCustomService;

    @InjectMocks private CompanyCustomController companyCustomController;

    @Test
    @DisplayName("getMemberTree 应正常转发调用 service 并返回统一响应体")
    void getMemberTree_success() {
        Long companyId = 123L;
        MemberTreeNodeResp root = MemberTreeNodeResp.builder().id(1L).name("测试部门").build();
        when(companyCustomService.getMemberTree(companyId)).thenReturn(List.of(root));

        ApiResponse<List<MemberTreeNodeResp>> resp =
                companyCustomController.getMemberTree(companyId);

        assertThat(resp).isNotNull();
        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(resp.getData()).hasSize(1);
        assertThat(resp.getData().get(0).getName()).isEqualTo("测试部门");
        verify(companyCustomService).getMemberTree(companyId);
    }
}
