package com.jdec.platform.dataengine.biz.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FieldPermissionItem {
    private Boolean apply = false;

    private Boolean view = false;

    private Boolean edit = false;
}
