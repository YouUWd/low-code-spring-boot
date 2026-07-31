package com.jdec.platform.common_business.biz.dto.configcenter.response;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimpleField implements Serializable {

    private Integer id;

    private String tableName;

    private String columnName;

    private String transformer;

    private String displayName;

    private Integer bizKeyOrder;
}
