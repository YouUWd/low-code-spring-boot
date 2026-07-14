package com.jdec.platform.common_business.api;

import com.jdec.platform.common_business.api.dto.response.UserTreeResp;
import java.util.List;

public interface UserServiceApi {
    List<UserTreeResp> getUserTreeList(Long subjectId, Integer userType, String userName);
}
