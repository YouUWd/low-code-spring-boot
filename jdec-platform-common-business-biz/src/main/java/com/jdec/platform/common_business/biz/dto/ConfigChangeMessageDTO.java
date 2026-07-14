package com.jdec.platform.common_business.biz.dto;

import java.io.Serializable;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class ConfigChangeMessageDTO<T> implements Serializable {

    private String messageId;

    private String publishTime;

    private Long subjectId;

    private String projectNo;

    private String changeType;

    private T payload;

    private String token;

    //    private OperatorDTO Operator;
}
