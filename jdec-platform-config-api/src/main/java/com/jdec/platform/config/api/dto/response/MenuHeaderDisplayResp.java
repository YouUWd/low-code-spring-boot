package com.jdec.platform.config.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 菜单多级列头展示响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "菜单多级列头展示响应")
public class MenuHeaderDisplayResp {

    @Schema(description = "列头根节点，无配置时为 null")
    private MenuHeaderColumnResp header;
}
