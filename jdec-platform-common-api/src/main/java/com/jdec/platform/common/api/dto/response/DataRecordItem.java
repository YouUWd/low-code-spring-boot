package com.jdec.platform.common.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 树形记录项包装容器
 *
 * <p>包含单行扁平物理数据（row，以 table.column 为键）与所属的下级子模块集合（children）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "数据记录项包装")
public class DataRecordItem implements Serializable {

    @Schema(description = "当前模块数据行 (键为 table.column 格式)")
    private Map<String, Object> row;

    @Schema(description = "从属于当前行的子模块数据集合 (自相似递归嵌套)")
    @Builder.Default
    private List<ModuleDataNode> children = new java.util.ArrayList<>();
}
