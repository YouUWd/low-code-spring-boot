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
        @Schema(description = "业务名称列表")
        private List<String> bizNames;

        @Schema(description = "业务名称")
        private String columnName;
    }
}
