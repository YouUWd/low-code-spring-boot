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

    @Schema(
            description =
                    "初始化标识：1-初始化（尚未进行任何表头设置，数据全部由菜单/字段动态生成）；0-非初始化（已从 sys_menu_header 取到已保存配置）")
    private Integer initialized;
}
