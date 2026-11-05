package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 数据权限查询项 */
@Data
@Schema(description = "数据权限查询项")
public class DataPermissionQueryItem {

    @Schema(description = "表名", example = "student")
    private String tableName;

    @Schema(description = "字段名", example = "id")
    private String column;

    @Schema(description = "主体id列表", example = "1,2,3")
    private List<Long> subjectIds;

    @Schema(description = "是否来自词云：0-否，1-是（决定业务数据是否需剥离词云内部字段再去重）")
    private Integer cloud;
}
