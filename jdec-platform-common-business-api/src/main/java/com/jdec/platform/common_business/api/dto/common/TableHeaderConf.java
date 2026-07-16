package com.jdec.platform.common_business.api.dto.common;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableHeaderConf implements Serializable {

    private String name;

    private String table;

    private String field;

    private Integer width;

    private Integer sortOrder;

    private String searchType;

    private String fixed;

    private Boolean ellipsis;

    private Boolean sortable;
}
