package com.jdec.platform.externalobject.biz.controller;

import com.jdec.platform.externalobject.api.dto.request.CompanyRegStatusUpdateReq;
import com.jdec.platform.externalobject.biz.service.CompanyCustomService;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 外部对象定制化业务控制器 (COMPANY-ROOT 模块) */
@Slf4j
@RestController
@RequestMapping("/api/company/custom")
@RequiredArgsConstructor
@Tag(name = "外部对象定制接口", description = "提供外部对象单据定制化业务操作，如修改登记状态等")
public class CompanyCustomController {

    private final CompanyCustomService companyCustomService;

    @PostMapping("/reg-status")
    @Operation(
            summary = "修改单据登记状态",
            description = "针对处于启用(99)或取消(-99)状态的外部对象单据，修改其登记状态(reg_status)，并同步校验对象性质(nature)规则")
    public ApiResponse<Void> updateRegStatus(@Valid @RequestBody CompanyRegStatusUpdateReq req) {
        try {
            companyCustomService.updateRegStatus(req);
            return ApiResponse.success();
        } catch (Exception ex) {
            log.error(
                    "CompanyCustom updateRegStatus failed: id={}, regStatus={}, err={}",
                    req != null ? req.getId() : null,
                    req != null ? req.getRegStatus() : null,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }
}
