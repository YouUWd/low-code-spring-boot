package com.jdec.platform.config.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目维度物理表关联拓扑聚合 DTO (含双向索引 Map)
 *
 * <p>支持基于 project 粒度进行全局缓存，并提供 O(1) 复杂度的双向关系极速匹配。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "项目维度物理表关联拓扑聚合信息")
public class ProjectTableRelationsDTO {

    @Schema(description = "项目编码", example = "company")
    private String projectNo;

    @Schema(description = "全量表关联列表")
    @Builder.Default
    private List<TableRelationDTO> allRelations = new ArrayList<>();

    @Schema(description = "正向索引：主表名 -> 关联列表 (main_table -> relations)")
    @Builder.Default
    private Map<String, List<TableRelationDTO>> forwardRelations = Collections.emptyMap();

    @Schema(description = "反向索引：从表名 -> 关联列表 (join_table -> relations)")
    @Builder.Default
    private Map<String, List<TableRelationDTO>> reverseRelations = Collections.emptyMap();

    /**
     * 快速精确查询 mainTable -> joinTable 的单向关系 (忽略表名大小写)
     *
     * @param mainTable 主表名
     * @param joinTable 从表名
     * @return 匹配的 TableRelationDTO，未找到返回 null
     */
    public TableRelationDTO findDirectRelation(String mainTable, String joinTable) {
        if (mainTable == null || joinTable == null || forwardRelations == null) {
            return null;
        }
        List<TableRelationDTO> list = forwardRelations.get(mainTable.trim().toLowerCase());
        if (list == null || list.isEmpty()) {
            return null;
        }
        for (TableRelationDTO rel : list) {
            if (rel != null
                    && rel.getJoinTable() != null
                    && joinTable.equalsIgnoreCase(rel.getJoinTable().trim())) {
                return rel;
            }
        }
        return null;
    }

    /**
     * 快速精确查询涉及指定表的所有关联列表 (正向作为 main_table 或 反向作为 join_table)
     *
     * @param tableName 表名
     * @return 关联列表
     */
    public List<TableRelationDTO> findRelationsByTable(String tableName) {
        if (tableName == null) {
            return Collections.emptyList();
        }
        String lowerT = tableName.trim().toLowerCase();
        List<TableRelationDTO> result = new ArrayList<>();
        if (forwardRelations != null && forwardRelations.containsKey(lowerT)) {
            result.addAll(forwardRelations.get(lowerT));
        }
        if (reverseRelations != null && reverseRelations.containsKey(lowerT)) {
            for (TableRelationDTO r : reverseRelations.get(lowerT)) {
                if (!result.contains(r)) {
                    result.add(r);
                }
            }
        }
        return result;
    }
}
