package com.jdec.platform.common_business.api.dto.response;

import java.io.Serializable;
import lombok.Data;

@Data
public class UserResp implements Serializable {

    private String departName;

    private Long id;

    private String positionName;

    private String userAvatar;

    private String userName;

    private String workNumber;
}
