package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 多模块同构原子批量保存请求模型 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "多模块同构原子批量保存请求模型")
public class BatchDynamicSaveReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主模块保存请求 (作为主实体优先执行，生成/确认外键 ID)")
    private DynamicSaveReq master;

    @Schema(description = "关联子模块保存请求映射字典，Key 为子模块别名 (如 courses, awards)，Value 为该模块的 DynamicSaveReq")
    private Map<String, DynamicSaveReq> children;
}
