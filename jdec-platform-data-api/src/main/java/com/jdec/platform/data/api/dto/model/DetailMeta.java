package com.jdec.platform.data.api.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "详情元数据与字段权限模型")
public class DetailMeta implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "字段显示名称字典: Key为表名, Value为(字段名->中文显示名)")
    private Map<String, Map<String, String>> fieldNames;

    @Schema(description = "各物理表的 view/apply/edit 权限白名单: Key为表名")
    private Map<String, TablePermission> permissions;
}
