package com.jdec.platform.dataengine.biz.operatelog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 操作日志 - 一个模块的变更 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "操作日志模块变更")
public class ModuleChangeLogDTO implements Serializable {

    @Schema(description = "变更项名称（取菜单名称，取不到回退模块名）")
    private String name;

    @Schema(description = "模块ID")
    @JsonProperty("mId")
    private Long mId;

    @Schema(description = "模块下的变更操作")
    private ActionChangeLogDTO actions;
}
