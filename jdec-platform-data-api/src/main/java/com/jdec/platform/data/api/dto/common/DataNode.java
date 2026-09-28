package com.jdec.platform.data.api.dto.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模块数据节点模型 (自相似模块数据树的模块单元)
 *
 * <p>表示一个业务模块及其下的数据记录行集合。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "自相似模块数据节点")
public class DataNode implements Serializable {

    @Schema(description = "模块 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    private Long moduleId;

    @Schema(description = "当前模块下的数据记录行列表")
    private List<DataRow> records;
}
