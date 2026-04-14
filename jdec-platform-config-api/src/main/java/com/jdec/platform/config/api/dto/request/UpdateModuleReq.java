package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.dto.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 更新模块请求
 *
 * <p>用于更新现有模块
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "更新模块请求")
public class UpdateModuleReq extends BaseRequest {

    /**
     * 模块 ID
     */
    @NotNull(message = "模块 ID 不能为空")
    @Schema(description = "模块 ID", example = "1")
    private Long id;

    /**
     * 模块名称
     */
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
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("模块 ID 不能为空或无效");
        }
        if ((moduleName == null || moduleName.trim().isEmpty()) && 
            (description == null || description.trim().isEmpty())) {
            throw new IllegalArgumentException("至少需要提供一个更新字段");
        }
    }
}
