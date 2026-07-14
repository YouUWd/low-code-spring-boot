package com.jdec.platform.common_business.biz.dto.configcenter.response;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SourceMapping implements Serializable {

    private String entity;

    private String field;

    private Integer sortOrder;
}
