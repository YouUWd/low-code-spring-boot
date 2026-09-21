package com.jdec.platform.common.biz.service;

import com.jdec.platform.config.api.dto.response.UserSearchTreeNodeResp;
import java.util.List;

/** 用户相关公共能力 */
public interface UserService {

    /**
     * 获取人员架构树。
     *
     * <p>对应源项目的 {@code UserServiceImpl#getUserTreeList}。
     *
     * @param userType 用户类型 0=全部 1=内部 2=外部
     * @param userName 人员名称（模糊查询，可为空）
     * @return 人员架构树
     */
    List<UserSearchTreeNodeResp> getUserTreeList(Integer userType, String userName);
}
