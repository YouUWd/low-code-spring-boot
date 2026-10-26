package com.jdec.platform.dataengine.api.dto.model;

import java.io.Serializable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TableRelationDTO implements Serializable {

    private Long id;

    private String mainTable;

    private String mainField;

    private String joinTable;

    private String joinField;

    private String relationType;

    private String description;
}
