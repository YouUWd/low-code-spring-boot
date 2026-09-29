package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * 单据详情精准查询请求模型 (DetailQueryReq)
 *
 * <p>继承自通用 QueryReq，扩充单据权威主键 ID，天然支持针对子模块的字段投影 (fields)、过滤 (filters) 与排序 (sorts)，且绝无任何分页参数污染。
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Schema(description = "单据详情精准查询请求模型")
public class DetailQueryReq extends QueryReq {

    @NotNull(message = "单据主键 ID 不能为空")
    @Schema(
            description = "单据权威主键 ID",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "1001")
    private Long id;
}
