package com.jdec.platform.config.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 树形表头节点契约 (ModuleHeaderNodeDTO)
 *
 * <p>遵循第一性原理与 KISS 原则： 1. 彻底删除生造的 key； 2. 字段统一使用 dataIndex = tableName.columnName； 3. 模块节点包含
 * moduleId 与 children，字段叶子节点包含 fieldId 与 dataIndex。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "树形表头节点契约 (100% 递归同构)")
public class ModuleHeaderNodeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "模块ID (模块容器节点特有)", example = "101")
    private Long moduleId;

    @Schema(description = "显示标签 (模块名称或字段显示名称)", example = "学生信息")
    private String label;

    @Schema(description = "字段ID (字段叶子节点特有)", example = "1011")
    private Long fieldId;

    @Schema(description = "数据索引键 (统一强制为 tableName.columnName)", example = "student.student_no")
    private String dataIndex;

    @Schema(description = "子节点列表 (子列或子模块)")
    private List<ModuleHeaderNodeDTO> children;
}
