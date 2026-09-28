package com.jdec.platform.dataengine.biz.dto;

import com.jdec.platform.dataengine.api.dto.enums.QueryType;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
// @AllArgsConstructor
@NoArgsConstructor
public class BasicModel implements Serializable {

    private Long subjectId;

    private Long roleId;

    private QueryType queryType;
}
