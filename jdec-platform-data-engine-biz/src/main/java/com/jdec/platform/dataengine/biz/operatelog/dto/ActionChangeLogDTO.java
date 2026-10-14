package com.jdec.platform.dataengine.biz.operatelog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 操作日志 - 一个模块下的变更操作（新增 i / 更新 u / 删除 d） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "操作日志模块变更操作")
public class ActionChangeLogDTO implements Serializable {

    @Schema(description = "新增的行")
    private List<RowChangeLogDTO> i;

    @Schema(description = "更新的行")
    private List<RowChangeLogDTO> u;

    @Schema(description = "删除的行")
    private List<RowChangeLogDTO> d;
}
