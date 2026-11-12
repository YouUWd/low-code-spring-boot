package com.jdec.platform.dataengine.api.dto.model;

import com.jdec.platform.shared.security.model.LoginUser;
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

    private LoginUser loginUser;
}
