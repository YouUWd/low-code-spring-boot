package com.jdec.platform.common_business.api.dto.common;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataRightValue {
    private String dataPermissionCode;

    private String subject;

    private List<String> bizNames;
}
