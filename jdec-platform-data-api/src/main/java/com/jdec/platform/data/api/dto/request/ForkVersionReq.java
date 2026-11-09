package com.jdec.platform.data.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jdec.platform.data.api.dto.common.DataBlock;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单据版本派生请求 DTO
 *
 * <p>基于基准单据 (id) 深度融合局部增量修改 (patchData)，生成全新独立的单据版本。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "单据版本派生请求")
public class ForkVersionReq implements Serializable {

    @Schema(description = "业务视图编码", example = "COMPANY")
    @JsonProperty("view")
    private String view;

    /** 基准单据物理 ID (如 156) */
    @Schema(description = "基准单据物理 ID", example = "100")
    private Long id;

    /** 局部增量编辑物理数据块 (仅需携带变动的列与增删改的明细) */
    @Schema(description = "局部增量编辑物理数据块")
    private DataBlock patchData;
}
