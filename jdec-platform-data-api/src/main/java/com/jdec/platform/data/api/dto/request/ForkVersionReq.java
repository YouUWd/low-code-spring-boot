package com.jdec.platform.data.api.dto.request;

import com.jdec.platform.data.api.dto.common.DataBlock;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 单据版本派生请求 DTO 基于基准单据 (baseId) 深度融合局部增量修改 (patchData)，生成全新独立的单据版本 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForkVersionReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 根模块 ID */
    private Long moduleId;

    /** 基准单据物理 ID (如 156) */
    private Long baseId;

    /** 局部增量编辑物理数据块 (仅需携带变动的列与增删改的明细) */
    private DataBlock patchData;
}
