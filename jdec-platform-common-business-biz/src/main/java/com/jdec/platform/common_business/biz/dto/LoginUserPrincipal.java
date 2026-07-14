package com.jdec.platform.common_business.biz.dto;

import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户登录信息上下文对象 * */
@Data
@EqualsAndHashCode
public class LoginUserPrincipal implements Serializable {

    private Long userId;

    private String userName;

    private Long subjectId;
}
