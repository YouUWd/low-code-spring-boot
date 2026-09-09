package com.jdec.platform.data.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 行内自相似模块数据节点
 *
 * <p>挂载在各数据行内部 (如 records[i].children)，严密保留子模块 ID、父外键关联与子数据列表， 解决多记录时主从数据关联错位问题，同时实现自相似递归。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "行内自相似模块数据节点")
public class DynamicDataNode implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "当前子模块 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "103")
    private Long moduleId;

    @Schema(description = "父外键关联字段名 (例如 student_id)", example = "student_id")
    private String parentForeignKey;

    @Schema(description = "当前子模块的数据记录列表 (每条记录内部可自相似继续挂载 children)")
    private List<Map<String, Object>> records;
}
