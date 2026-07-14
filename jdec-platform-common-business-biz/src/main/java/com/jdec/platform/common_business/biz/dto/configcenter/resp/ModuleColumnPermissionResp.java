package com.jdec.platform.common_business.biz.dto.configcenter.resp;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModuleColumnPermissionResp implements Serializable {

    private String tableName;

    private String tableDesc;

    private String tableType;

    private List<ModuleColumnResp> readableFields;

    private List<ModuleColumnResp> writableFields;

    private List<ModuleColumnResp> updatableFields;
}
