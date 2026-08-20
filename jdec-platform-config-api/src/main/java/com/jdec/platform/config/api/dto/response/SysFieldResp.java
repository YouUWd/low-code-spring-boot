package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 字段配置响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字段配置响应")
public class SysFieldResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "项目编号")
    private String projectNo;

    @Schema(description = "主体ID")
    private Long subjectId;

    @Schema(description = "表名")
    private String tableName;

    @Schema(description = "列名")
    private String columnName;

    @Schema(description = "列类型")
    private String columnType;

    @Schema(description = "前端显示名称")
    private String displayName;

    @Schema(description = "关联关系业务编号")
    private String relationBusinessNo;

    @Schema(description = "关联关系名称")
    private String relationName;

    @Schema(description = "是否加密存储: 0-否, 1-是")
    private Integer encrypted;

    /** 是否生成权限节点.0--否,1--是 */
    @Schema(description = "是否生成权限节点: 0-否, 1-是", example = "0")
    private Integer dataRightFlag;

    @Schema(description = "组合配置信息")
    private SysFieldCombineInfoResp combineInfo;

    @Schema(description = "字段业务配置值列表")
    private List<SysFieldValueResp> fieldValues;

    @Schema(description = "关联的模块信息列表")
    private List<SysFieldModuleResp> modules;

    /** 字段关联模块信息 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "字段关联模块信息")
    public static class SysFieldModuleResp {
        @Schema(description = "模块ID")
        private Long moduleId;

        @Schema(description = "模块名称")
        private String moduleName;
    }
}
