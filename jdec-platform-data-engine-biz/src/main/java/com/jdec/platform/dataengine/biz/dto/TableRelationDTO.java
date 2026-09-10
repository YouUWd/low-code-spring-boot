package com.jdec.platform.dataengine.biz.dto;

import java.io.Serializable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TableRelationDTO implements Serializable {

    private String mainTable;

    private String mainField;

    private String joinTable;

    private String joinField;

    private String relationType;
}
