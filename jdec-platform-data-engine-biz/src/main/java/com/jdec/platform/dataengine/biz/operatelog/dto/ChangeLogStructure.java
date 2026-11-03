package com.jdec.platform.dataengine.biz.operatelog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 操作日志 remark.change 结构。
 *
 * <p>顶层模块称为主模块，其下所有模块为子模块（子模块内部仍可通过 {@code children} 继续嵌套更深层级模块）：
 *
 * <ul>
 *   <li>{@code main}：主模块自身的字段变更明细，平铺为列对象数组；主模块不展示唯一值字段（行名）。
 *   <li>{@code sub}：主模块下所有<b>平级子模块</b>的变更数组，每个元素为独立子模块（结构与子模块处理逻辑一致）， 不再包含主模块自身的字段对比（主模块字段对比已平铺到
 *       {@code main}）。
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "操作日志变更结构")
public class ChangeLogStructure implements Serializable {

    @Schema(description = "主模块字段变更明细（平铺，无唯一值字段展示）")
    private List<ColumnChangeLogDTO> main;

    @Schema(description = "主模块下的平级子模块变更数组（不含主模块自身字段，主模块字段见 main）")
    private List<ModuleChangeLogDTO> sub;
}
