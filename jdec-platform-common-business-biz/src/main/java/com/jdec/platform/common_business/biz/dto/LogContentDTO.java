package com.jdec.platform.common_business.biz.dto;

import java.io.Serializable;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString
public class LogContentDTO implements Serializable {

    private String module;

    private String orderId;

    private String hrefUrl;

    private String controlName;
}
