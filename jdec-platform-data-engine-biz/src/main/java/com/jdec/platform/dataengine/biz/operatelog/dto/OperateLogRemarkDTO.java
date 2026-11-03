package com.jdec.platform.dataengine.biz.operatelog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 操作日志 remark 结构。
 *
 * <p>无审批链场景下 {@code comment} 为空；{@code change} 为变更结构：主模块字段变更（main，平铺）与 主模块下的平级子模块数组（sub，不含主模块自身字段）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "操作日志备注")
public class OperateLogRemarkDTO implements Serializable {

    @Schema(description = "备注（有审批链时填写，无审批链为空）")
    private String comment;

    @Schema(description = "变更结构：主模块字段变更(main)与含子模块的主模块(sub)")
    private ChangeLogStructure change;
}
