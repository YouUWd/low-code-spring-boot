package com.jdec.platform.common_business.biz.dto.configcenter.response;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModuleStatus implements Serializable {

    private Integer id;

    private Integer statusPid;

    private Integer statusId;
}
