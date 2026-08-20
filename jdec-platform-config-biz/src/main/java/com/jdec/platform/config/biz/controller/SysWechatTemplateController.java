package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysWechatTemplateApi;
import com.jdec.platform.config.api.dto.request.CreateWechatTemplateParamReq;
import com.jdec.platform.config.api.dto.request.CreateWechatTemplateReq;
import com.jdec.platform.config.api.dto.request.SendWechatMessageReq;
import com.jdec.platform.config.api.dto.request.UpdateWechatTemplateParamReq;
import com.jdec.platform.config.api.dto.request.UpdateWechatTemplateReq;
import com.jdec.platform.config.api.dto.request.WechatTemplateParamQueryReq;
import com.jdec.platform.config.api.dto.request.WechatTemplateQueryReq;
import com.jdec.platform.config.api.dto.response.WechatTemplateDetailResp;
import com.jdec.platform.config.api.dto.response.WechatTemplateEntryResp;
import com.jdec.platform.config.api.dto.response.WechatTemplateParamEntryResp;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 企业微信消息通知模板管理 控制器 */
@Slf4j
@Tag(name = "微信模板管理", description = "企业微信消息通知模板及参数的配置与维护")
@RestController
@RequestMapping("/api/config/wechat/templates")
@RequiredArgsConstructor
public class SysWechatTemplateController {

    private final SysWechatTemplateApi sysWechatTemplateService;

    // --- 模板主表管理 ---

    @Operation(summary = "查询模板分页列表")
    @PostMapping("/page")
    public ApiResponse<PageResult<WechatTemplateEntryResp>> listTemplates(
            @RequestBody WechatTemplateQueryReq query) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("查询项目 {} 的微信模板列表", projectNo);
        return ApiResponse.success(
                sysWechatTemplateService.listTemplates(projectNo, subjectId, query));
    }

    @Operation(summary = "根据模块ID查询微信模板列表")
    @GetMapping("/module/{moduleId}")
    public ApiResponse<List<WechatTemplateEntryResp>> listTemplatesByModuleId(
            @Parameter(description = "模块ID", required = true) @PathVariable Long moduleId) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("根据模块ID查询微信模板列表: moduleId={}, projectNo={}", moduleId, projectNo);
        List<WechatTemplateEntryResp> result =
                sysWechatTemplateService.listTemplatesByModuleId(projectNo, subjectId, moduleId);
        return ApiResponse.success(result);
    }

    @Operation(summary = "获取模板详情")
    @GetMapping("/{id}")
    public ApiResponse<WechatTemplateDetailResp> getTemplateById(
            @Parameter(description = "模板ID", required = true) @PathVariable Long id) {
        log.info("查询模板详情: id={}", id);
        return ApiResponse.success(sysWechatTemplateService.getTemplateById(id));
    }

    @Operation(summary = "创建模板")
    @PostMapping
    public ApiResponse<WechatTemplateDetailResp> createTemplate(
            @RequestBody CreateWechatTemplateReq req) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("创建微信模板: title={}", req.getTemplateTitle());
        return ApiResponse.success(
                "创建成功", sysWechatTemplateService.createTemplate(projectNo, subjectId, req));
    }

    @Operation(summary = "更新模板")
    @PutMapping
    public ApiResponse<WechatTemplateDetailResp> updateTemplate(
            @RequestBody UpdateWechatTemplateReq req) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("更新微信模板: id={}, title={}", req.getId(), req.getTemplateTitle());
        return ApiResponse.success(
                "更新成功", sysWechatTemplateService.updateTemplate(projectNo, subjectId, req));
    }

    @Operation(summary = "删除模板")
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteTemplate(
            @Parameter(description = "模板ID", required = true) @PathVariable Long id) {
        log.info("逻辑删除微信模板: id={}", id);
        sysWechatTemplateService.deleteTemplate(id);
        return ApiResponse.success("删除成功", null);
    }

    @Operation(summary = "测试发送微信模板消息")
    @PostMapping("/send-test/{id}")
    public ApiResponse<Boolean> testSendMessage(
            @Parameter(description = "模板ID", required = true) @PathVariable Long id) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("测试发送微信模板消息: id={}, projectNo={}", id, projectNo);
        boolean result = sysWechatTemplateService.testSendMessage(projectNo, subjectId, id);
        return ApiResponse.success("发送测试消息成功", result);
    }

    @Operation(summary = "发送企业微信消息")
    @PostMapping("/send-message")
    public ApiResponse<Boolean> sendWechatMessage(@RequestBody SendWechatMessageReq req) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info(
                "发送企业微信消息: receiversCount={}, title={}, projectNo={}",
                req.getReceivers() != null ? req.getReceivers().size() : 0,
                req.getMessageTitle(),
                projectNo);
        boolean result = sysWechatTemplateService.sendWechatMessage(projectNo, subjectId, req);
        return ApiResponse.success("发送消息成功", result);
    }

    // --- 模板参数管理 ---

    @Operation(summary = "查询参数分页列表")
    @PostMapping("/params/page")
    public ApiResponse<PageResult<WechatTemplateParamEntryResp>> listParams(
            @RequestBody WechatTemplateParamQueryReq query) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("查询项目 {} 的微信模板参数列表", projectNo);
        return ApiResponse.success(
                sysWechatTemplateService.listParams(projectNo, subjectId, query));
    }

    @Operation(summary = "根据模块ID查询微信模板参数列表")
    @GetMapping("/params/module/{moduleId}")
    public ApiResponse<List<WechatTemplateParamEntryResp>> listParamsByModuleId(
            @Parameter(description = "模块ID", required = true) @PathVariable Long moduleId) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("根据模块ID查询微信模板参数列表: moduleId={}, projectNo={}", moduleId, projectNo);
        List<WechatTemplateParamEntryResp> result =
                sysWechatTemplateService.listParamsByModuleId(projectNo, subjectId, moduleId);
        return ApiResponse.success(result);
    }

    @Operation(summary = "根据参数ID列表查询微信模板参数列表")
    @PostMapping("/params/list-by-ids")
    public ApiResponse<List<WechatTemplateParamEntryResp>> listParamsByIds(
            @RequestBody List<Long> ids) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("根据参数ID列表查询微信模板参数列表: ids={}, projectNo={}", ids, projectNo);
        List<WechatTemplateParamEntryResp> result =
                sysWechatTemplateService.listParamsByIds(projectNo, subjectId, ids);
        return ApiResponse.success(result);
    }

    @Operation(summary = "获取参数详情")
    @GetMapping("/params/{id}")
    public ApiResponse<WechatTemplateParamEntryResp> getParamById(
            @Parameter(description = "参数ID", required = true) @PathVariable Long id) {
        log.info("查询模板参数详情: id={}", id);
        return ApiResponse.success(sysWechatTemplateService.getParamById(id));
    }

    @Operation(summary = "创建参数")
    @PostMapping("/params")
    public ApiResponse<WechatTemplateParamEntryResp> createParam(
            @RequestBody CreateWechatTemplateParamReq req) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("创建微信模板参数: name={}", req.getTemplateParamName());
        return ApiResponse.success(
                "创建成功", sysWechatTemplateService.createParam(projectNo, subjectId, req));
    }

    @Operation(summary = "更新参数")
    @PutMapping("/params")
    public ApiResponse<WechatTemplateParamEntryResp> updateParam(
            @RequestBody UpdateWechatTemplateParamReq req) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("更新微信模板参数: id={}, name={}", req.getId(), req.getTemplateParamName());
        return ApiResponse.success(
                "更新成功", sysWechatTemplateService.updateParam(projectNo, subjectId, req));
    }

    @Operation(summary = "删除参数")
    @DeleteMapping("/params/{id}")
    public ApiResponse<String> deleteParam(
            @Parameter(description = "参数ID", required = true) @PathVariable Long id) {
        log.info("逻辑删除微信模板参数: id={}", id);
        sysWechatTemplateService.deleteParam(id);
        return ApiResponse.success("删除成功", null);
    }
}
