package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目信息查询请求
 *
 * @author JDEC Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "项目信息查询请求")
public class ProjectInfoReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(
            description = "项目业务编号 (必填)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "P260525095218")
    @NotBlank(message = "项目业务编号 businessNo 不能为空")
    private String businessNo;

    @Schema(description = "需要查询的属性列列表 (如 title, logo, icon)，缺省时默认查询 title, logo, icon")
    private List<String> columns;
}
