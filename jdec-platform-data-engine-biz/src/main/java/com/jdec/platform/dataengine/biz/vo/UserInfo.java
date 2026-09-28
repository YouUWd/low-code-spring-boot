package com.jdec.platform.dataengine.biz.vo;

import lombok.Builder;
import lombok.Data;

/** 用户信息返回模型类 */
@Data
@Builder
public class UserInfo {
    private Long id;

    private String departName;

    private String positionName;

    private String userAvatar;

    private String userName;

    private String workNumber;
}
