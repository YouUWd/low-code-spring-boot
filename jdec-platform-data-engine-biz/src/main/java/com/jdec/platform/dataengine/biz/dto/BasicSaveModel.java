package com.jdec.platform.dataengine.biz.dto;

import com.jdec.platform.dataengine.biz.enums.SaveType;
import com.jdec.platform.shared.security.model.LoginUser;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
// @AllArgsConstructor
@NoArgsConstructor
public class BasicSaveModel implements Serializable {

    private Long subjectId;

    private Long roleId;

    private Long userId;

    private String projectNo;

    private SaveType saveType;

    private LoginUser loginUser;
}
