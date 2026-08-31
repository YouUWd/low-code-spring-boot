package com.jdec.platform.data.api.dto.model;

import com.jdec.platform.config.api.dto.common.ModuleSimpleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleStatusDTO;
import com.jdec.platform.config.api.dto.common.ModuleTableDTO;
import com.jdec.platform.config.api.dto.common.ModuleTableHeaderDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 引擎模块元数据视图模型 供业务层与调用方一站式获取模块结构，避免直接穿透依赖 config 层 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "引擎模块元数据视图")
public class EngineModuleMeta {

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "模块编码")
    private String moduleCode;

    @Schema(description = "模块名称")
    private String moduleName;

    @Schema(description = "模块描述")
    private String moduleDesc;

    @Schema(description = "主表名称")
    private String primaryTable;

    @Schema(description = "模块关联表列表")
    private List<ModuleTableDTO> tables;

    @Schema(description = "物理字段字典列表")
    private List<ModuleSimpleFieldDTO> fields;

    @Schema(description = "列表表头与检索配置（已结合当前用户角色权限裁剪）")
    private List<ModuleTableHeaderDTO> headers;

    @Schema(description = "状态机配置列表")
    private List<ModuleStatusDTO> statuses;

    @Schema(description = "当前角色在当前模块下的字段权限配置列表 (apply, view, edit)")
    private List<Map<String, Object>> permissions;
}
