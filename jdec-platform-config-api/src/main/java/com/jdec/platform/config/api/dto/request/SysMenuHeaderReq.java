package com.jdec.platform.config.api.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 菜单表头节点排序/父级调整请求项 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "菜单表头节点排序/父级调整请求项")
public class SysMenuHeaderReq {

    @Schema(description = "所属菜单ID", example = "1")
    private Long menuId;

    @Schema(description = "节点虚拟id（对应 sys_menu_header.vir_id），如 MENU_47 / Field_10")
    private String id;

    @Schema(description = "父节点虚拟id（对应 sys_menu_header.parent_id，顶层为 0）")
    private String parentId;

    @Schema(description = "排序号（对应 sys_menu_header.sort_value）")
    private Integer sortOrder;

    @Schema(description = "固定（left-左固定，right-右固定，空-不固定）")
    private String fixed;

    @Schema(description = "是否选中：0-否，1-是")
    private Integer checked;
}
