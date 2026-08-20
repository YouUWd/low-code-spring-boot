package com.jdec.platform.config.biz.controller;

import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.wechat.WeChatWorkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "测试工具", description = "开发阶段使用的测试接口")
@RestController
@RequestMapping("/api/config/test/wechat")
@RequiredArgsConstructor
public class TestWeChatController {

    private final WeChatWorkService weChatWorkService;

    @Operation(summary = "测试发送企业微信验证码", description = "直接调用企业微信服务发送验证码，用于验证配置和连接是否正常")
    @GetMapping("/send-code")
    public ApiResponse<String> sendCode(
            @Parameter(description = "主体ID", example = "1") @RequestParam Long subjectId,
            @Parameter(description = "企业微信用户ID", example = "WangXiaoEr") @RequestParam
                    String userId,
            @Parameter(description = "验证码", example = "123456") @RequestParam String code) {
        weChatWorkService.sendVerificationCode(subjectId, userId, code);
        return ApiResponse.success("发送指令已提交，请检查企业微信。");
    }

    @Operation(summary = "测试发送Markdown验证码", description = "发送带有样式的Markdown格式验证码")
    @GetMapping("/send-markdown-code")
    public ApiResponse<String> sendMarkdownCode(
            @Parameter(description = "主体ID", example = "1") @RequestParam Long subjectId,
            @Parameter(description = "项目编号", example = "P001") @RequestParam String projectNo,
            @Parameter(description = "项目名称", example = "人力资源系统-改") @RequestParam String projectName,
            @Parameter(description = "企业微信用户ID", example = "WangXiaoEr") @RequestParam
                    String userId,
            @Parameter(description = "验证码", example = "7010") @RequestParam String code,
            @Parameter(description = "接收人姓名", example = "邓由由") @RequestParam String userName) {
        weChatWorkService.sendMarkdownVerificationCode(
                subjectId, projectNo, projectName, userId, code, userName);
        return ApiResponse.success("Markdown验证码已提交发送。");
    }
}
