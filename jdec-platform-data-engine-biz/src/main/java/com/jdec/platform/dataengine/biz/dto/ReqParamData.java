package com.jdec.platform.dataengine.biz.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
// @AllArgsConstructor
@NoArgsConstructor
public class ReqParamData implements java.io.Serializable {

    private String projectNo;

    private Long subjectId;

    private Long roleId;

    private Long loginUserId;

    private Long mockUserId;

    public Long getUserId() {
        if (mockUserId != null && mockUserId > 0) {
            return mockUserId;
        }
        return loginUserId;
    }
}
