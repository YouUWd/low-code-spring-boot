package com.jdec.platform.data.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜单动态数据响应模型 (与 MenuQueryReq 呼应，面向前端 UI 隔离层)
 *
 * <p>顶层包含全局菜单多级列头展示配置 (header) 与自相似模块数据树载荷 (data)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "菜单动态数据响应模型")
public class MenuDataResp implements Serializable {

    @Schema(description = "菜单多级列头展示配置 (来源自 UserMenuHeaderApi)")
    private MenuHeaderColumnResp header;

    @Schema(description = "模块数据树载荷")
    private ModuleDataNode data;
}
