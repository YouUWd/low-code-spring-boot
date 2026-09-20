package com.jdec.platform.data.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自相似模块数据节点模型 (负责从根节点到任意子模块层级的自相似递归装配)
 *
 * <p>每个节点包含 moduleId、menuId 以及对应的记录列表 record。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "自相似模块数据节点")
public class ModuleDataNode implements Serializable {

    @Schema(description = "模块 ID", example = "1001")
    private Long moduleId;

    @Schema(description = "菜单 ID", example = "1001")
    private Long menuId;

    @Schema(description = "当前模块下的记录列表 (包含数据行及级联子模块)")
    private List<DataRecordItem> record;
}
