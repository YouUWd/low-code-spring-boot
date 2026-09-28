package com.jdec.platform.dataengine.biz.dto;

import java.io.Serializable;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode
public class FormButton implements Serializable {

    private String buttonName;

    private String buttonKey;
}
