package com.jdec.platform.common.api.dto.response;

import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块元数据响应结构 (字段以 table.column 格式映射为字典) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模块元数据配置")
public class ModuleMetaDTO implements Serializable {

    @Schema(description = "模块 ID", example = "1001")
    private Long moduleId;

    @Schema(description = "菜单 ID", example = "19")
    private Long menuId;

    @Schema(description = "字段字典 (键为 tableName.columnName 格式)")
    private Map<String, FieldMetaDTO> fields;

    private MenuHeaderColumnResp header;

    private ModuleSimplePermissionDTO permission;
}
