package com.jdec.platform.externalobject.biz.controller;

import com.jdec.platform.externalobject.api.dto.request.CompanyBadgeNumReq;
import com.jdec.platform.externalobject.api.dto.request.CompanyRegStatusUpdateReq;
import com.jdec.platform.externalobject.api.dto.response.CompanyTreeNodeResp;
import com.jdec.platform.externalobject.biz.entity.CompanyContact;
import com.jdec.platform.externalobject.biz.service.CompanyCustomService;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/nature")
    @Operation(summary = "根据单据ID获取对象性质", description = "根据单据ID查询外部对象单据的对象性质(nature)")
    public ApiResponse<Object> getNature(
            @Parameter(description = "当前单据物理主键ID", required = true) @RequestParam("id") Long id) {
        try {
            Object nature = companyCustomService.getNature(id);
            return ApiResponse.success(nature);
        } catch (Exception ex) {
            log.error("CompanyCustom getNature failed: id={}, err={}", id, ex.getMessage(), ex);
            throw ex;
        }
    }

    @PostMapping("/badge-num")
    @Operation(
            summary = "获取菜单Tab对应的角标数",
            description = "获取当前单据下菜单Tab对应的数据数量，按菜单ID为key，数量为value返回结果。")
    public ApiResponse<Map<Long, Long>> getMenuNum(@Valid @RequestBody CompanyBadgeNumReq req) {
        try {
            Map<Long, Long> menuNum = companyCustomService.getMenuNum(req);
            return ApiResponse.success(menuNum);
        } catch (Exception ex) {
            log.error(
                    "CompanyCustom getMenuNum failed: id={}, menuIds={}, err={}",
                    req != null ? req.getId() : null,
                    req != null ? req.getMenuIds() : null,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    @GetMapping("/tree")
    @Operation(
            summary = "获取当前单据关联的公司树",
            description = "基于单据ID分别向上追溯顶级与向下发散全层级，严格按 parent_id 记录的版本精确匹配，构建完整公司树")
    public ApiResponse<CompanyTreeNodeResp> getCompanyTree(
            @Parameter(description = "当前单据物理主键ID", required = true) @RequestParam("id") Long id) {
        try {
            CompanyTreeNodeResp tree = companyCustomService.getCompanyTree(id);
            return ApiResponse.success(tree);
        } catch (Exception ex) {
            log.error(
                    "CompanyCustom getCompanyTree failed: id={}, err={}", id, ex.getMessage(), ex);
            throw ex;
        }
    }

    @GetMapping("/company-contact/list")
    @Operation(
            summary = "根据公司ID查询公司联系方式列表",
            description = "基于所属公司ID(company_id)查询未删除的公司联系方式列表，按排序号升序返回")
    public ApiResponse<List<CompanyContact>> listCompanyContacts(
            @Parameter(description = "所属公司ID（单据物理主键ID）", required = true) @RequestParam("companyId")
                    Long companyId) {
        try {
            List<CompanyContact> list = companyCustomService.listCompanyContacts(companyId);
            return ApiResponse.success(list);
        } catch (Exception ex) {
            log.error(
                    "CompanyCustom listCompanyContacts failed: companyId={}, err={}",
                    companyId,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }
}
