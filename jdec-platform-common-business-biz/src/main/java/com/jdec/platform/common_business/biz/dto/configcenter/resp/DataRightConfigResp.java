package com.jdec.platform.common_business.biz.dto.configcenter.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 数据权限配置值获取返回对象
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataRightConfigResp {

    @Schema(description = "数据库表名")
    private String tableName;

    private List<DataConfig> dataList;
}
