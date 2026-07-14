package com.jdec.platform.common_business.biz.dto;

import java.io.Serializable;
import lombok.Data;

@Data
public class OperatorDTO implements Serializable {

    private Long userId;

    private String userName;
}
