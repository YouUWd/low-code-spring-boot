package com.jdec.platform.common_business.biz.dto.configcenter.resp;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 数据权限单个配置项
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataConfig {
    private List<String> bizNames;

    private String columnName;
}
