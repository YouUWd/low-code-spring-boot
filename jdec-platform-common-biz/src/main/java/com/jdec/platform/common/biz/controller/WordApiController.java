package com.jdec.platform.common.biz.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.third.WordApiClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Word 外部服务的关系查询接口。
 *
 * <p>迁移自源项目 {@code com.jdec.student_manage.business.biz.controller.WordApiController}。
 *
 * <p><b>与源实现的差异：</b>源项目把 {@code WordApiClient} 放在 {@code student-manage-shared}，平台已有等价实现 {@code
 * com.jdec.platform.shared.third.WordApiClient}（方法签名完全一致，同为 HMAC-SHA256 签名 + 剥离 {@code
 * InnerApiResponse} 包装），因此这里只替换了注入的客户端类型，接口契约不变。
 *
 * <p><b>必须显式指定 bean 名：</b>平台里 {@code config.biz.controller.WordApiController}（{@code
 * /api/config/word}）与本类 同名，两个 {@code @RestController} 默认都叫 {@code wordApiController}，会让 Spring 抛
 * {@code ConflictingBeanDefinitionException} 而启动失败。这里显式起名 {@code studentWordApiController} 以共存。
 */
@RestController("studentWordApiController")
@RequestMapping("/api/student/word")
@RequiredArgsConstructor
@Tag(name = "Word 外部服务", description = "Word 外部 API 的关系查询接口")
public class WordApiController {

    private final WordApiClient wordApiClient;

    @GetMapping("/relation/info")
    @Operation(summary = "获取关系详情")
    public ApiResponse<Object> getRelationInfo(
            @Parameter(description = "业务编号", required = true) @RequestParam String businessNo) {
        Object data = wordApiClient.getRelationInfo(businessNo, new TypeReference<>() {});
        return ApiResponse.success(data);
    }

    @GetMapping("/relation/info/tree")
    @Operation(summary = "获取关系详情树")
    public ApiResponse<Object> getRelationInfoTree(
            @Parameter(description = "业务编号", required = true) @RequestParam String businessNo) {
        Object data = wordApiClient.getRelationInfoTree(businessNo, new TypeReference<>() {});
        return ApiResponse.success(data);
    }

    @GetMapping("/relation/word")
    @Operation(summary = "获取关系值")
    public ApiResponse<Object> getRelationWord(
            @Parameter(description = "业务编号", required = true) @RequestParam String businessNo) {
        Object data = wordApiClient.getRelationWord(businessNo, new TypeReference<>() {});
        return ApiResponse.success(data);
    }
}
