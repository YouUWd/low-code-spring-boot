package com.jdec.platform.common_business.biz.dto.configcenter.resp;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CombineField implements Serializable {

    private Integer id;

    private String logicalField;

    private String displayName;

    private List<SourceMapping> sourceMapping;

    private String transformer;

    private Integer enabled;
}
