package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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

    /** 便捷工厂：投影指定表的全部字段 */
    public static TableSpec all(String tableName) {
        return TableSpec.builder().tableName(tableName).columns(List.of("*")).build();
    }

    /** 便捷工厂：投影指定表的指定字段 */
    public static TableSpec of(String tableName, String... columns) {
        return TableSpec.builder().tableName(tableName).columns(List.of(columns)).build();
    }

    /** 便捷工厂：投影指定表的字段列表 */
    public static TableSpec of(String tableName, List<String> columns) {
        return TableSpec.builder().tableName(tableName).columns(columns).build();
    }
}
