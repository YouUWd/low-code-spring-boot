package com.jdec.platform.data.api.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 抽象动态数据聚合基类 (读写同构的核心数据与递归树载体)
 *
 * @param <T> 子模块自身的递归类型 (如 DynamicDetailResp 或 DynamicSaveReq)
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "动态数据聚合抽象基类")
public abstract class AbstractDynamicData<T extends AbstractDynamicData<T>>
        implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(
            description = "当前操作的模块 ID",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "134")
    private Long moduleId;

    @Schema(
            description =
                    "当前模块物理表数据集 (Key 为表名; 1:1/N:1 为 Map<String, Object>, 1:N 为 List<Map<String, Object>>)")
    private Map<String, Object> tables;

    @Schema(description = "挂载在当前模块下的子模块列表 (标准递归树形结构)")
    private List<T> subModules;
}
