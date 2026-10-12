package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 物理表投影规格 (精准绑定物理表与字段列表) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "物理表投影规格")
public class TableSpec implements Serializable {

    @NotNull(message = "所属模块 ID 不能为空")
    @Schema(description = "所属模块 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    private Long moduleId;

    @NotBlank(message = "物理表名不能为空")
    @Schema(description = "物理表名 (如 info, basic_info, member)", example = "info")
    private String tableName;

    @ArraySchema(
            schema =
                    @Schema(
                            description = "列名 (如 company_name, parent_id)",
                            example = "company_name"),
            arraySchema = @Schema(description = "该表需要投影的列名列表。为 null、空列表或包含通配符 * 时，默认投影该物理表的全部字段"))
    private List<String> columns;

    /** 便捷工厂：投影指定模块及指定表的全部字段 */
    public static TableSpec all(Long moduleId, String tableName) {
        return TableSpec.builder()
                .moduleId(moduleId)
                .tableName(tableName)
                .columns(List.of("*"))
                .build();
    }

    /** 便捷工厂：投影指定模块及指定表的指定字段 */
    public static TableSpec of(Long moduleId, String tableName, String... columns) {
        return TableSpec.builder()
                .moduleId(moduleId)
                .tableName(tableName)
                .columns(List.of(columns))
                .build();
    }

    /** 便捷工厂：投影指定模块及指定表的字段列表 */
    public static TableSpec of(Long moduleId, String tableName, List<String> columns) {
        return TableSpec.builder().moduleId(moduleId).tableName(tableName).columns(columns).build();
    }
}
