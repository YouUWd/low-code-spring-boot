package com.jdec.platform.common_business.biz.dto.configcenter.response;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModuleTableResp implements Serializable {

    private Integer id;

    private String tableName;

    private String tableDesc;

    private String joinLeftField;

    private String joinRightField;

    private String relationType;

    // 关联表是否只读（0=否，1=是）
    private Integer readOnly;

    private Integer sortOrder;
}
