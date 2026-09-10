package com.jdec.platform.dataengine.api.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "表关联关系节点")
public class TabTreeNodeDTO implements Serializable {

    @Schema(description = "父节点")
    private TabNodeDTO node;

    @Schema(description = "子节点列表")
    private List<TabTreeNodeDTO> children;
}
