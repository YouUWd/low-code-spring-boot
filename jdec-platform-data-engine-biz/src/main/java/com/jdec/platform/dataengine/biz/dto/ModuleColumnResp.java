package com.jdec.platform.dataengine.biz.dto;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModuleColumnResp implements Serializable {

    private Long id;

    private String fieldCode;

    private String fieldName;
}
