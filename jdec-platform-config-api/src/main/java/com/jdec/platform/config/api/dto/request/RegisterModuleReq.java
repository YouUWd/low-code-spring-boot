package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.annotation.SysModule;
import com.jdec.platform.shared.dto.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 注册模块请求
 *
 * <p>用于创建新模块
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "注册模块请求")
public class RegisterModuleReq extends BaseRequest {

    /**
     * 模块编码
     */
    @NotBlank(message = "模块编码不能为空")
    @Schema(description = "模块编码", example = "user_base")
    private String moduleCode;

    /**
     * 模块名称
     */
    @NotBlank(message = "模块名称不能为空")
    @Schema(description = "模块名称", example = "用户基本信息")
    private String moduleName;

    /**
     * 模块描述
     */
    @Schema(description = "模块描述", example = "用户基本信息模块")
    private String description;

    /**
     * 验证请求对象
     *
     * @throws IllegalArgumentException 验证失败时抛出
     */
    @Override
    public void validate() {
        if (moduleCode == null || moduleCode.trim().isEmpty()) {
            throw new IllegalArgumentException("模块编码不能为空");
        }
        if (moduleName == null || moduleName.trim().isEmpty()) {
            throw new IllegalArgumentException("模块名称不能为空");
        }
    }
}
