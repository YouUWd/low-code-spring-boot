package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "角色数据权限-业务方使用")
public class DataRightConfigResp {
    @Schema(description = "表名")
    private String tableName;

    @Schema(description = "数据权限列表")
    private List<DataConfig> dataList;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataConfig {
        @Schema(description = "数据权限全选：1=该数据权限节点下全部主体全选，0=非全选")
        private Integer permissionCheckAll;

        @Schema(description = "主体ID")
        private Long subjectId;

        @Schema(description = "主体全选：1=该主体下全部数据，0=按业务名称勾选")
        private Integer subjectCheckAll;

        @Schema(description = "业务名称列表")
        private List<String> bizNames;

        @Schema(description = "字段名")
        private String columnName;
    }
}
