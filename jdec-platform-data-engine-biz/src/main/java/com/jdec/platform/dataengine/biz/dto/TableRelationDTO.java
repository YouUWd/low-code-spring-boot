package com.jdec.platform.dataengine.biz.dto;

import java.io.Serializable;
import lombok.Data;

@Data
public class TableRelationDTO implements Serializable {

    private String mainTable;

    private String mainField;

    private String joinTable;

    private String joinField;

    private String relationType;
}
