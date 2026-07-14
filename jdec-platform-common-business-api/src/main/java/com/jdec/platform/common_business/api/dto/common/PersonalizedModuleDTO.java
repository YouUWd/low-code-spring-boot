package com.jdec.platform.common_business.api.dto.common;

import java.io.Serializable;
import lombok.Data;

@Data
public class PersonalizedModuleDTO implements Serializable {

    private String name; // 列名称

    private String table; // 表名称

    private String tableName; // 表中文名

    private String field; // 关联字段

    private Integer width; // 列宽度（像素）

    private Integer sortOrder; // 排序顺序

    private String searchType; // 搜索类型

    private String fixed; // 固定列位置,可用值:none,left,right

    private Boolean ellipsis; // 	文本超出是否省略

    private Boolean sortable; // 是否可排序

    private Boolean checked;
}
