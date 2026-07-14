package com.jdec.platform.common_business.biz.dto.configcenter.resp;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModuleColumnResp implements Serializable {

    private Integer id;

    private String fieldCode;

    private String fieldName;
}
