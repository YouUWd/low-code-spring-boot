package com.jdec.platform.hr.api;

import com.jdec.platform.hr.api.bo.UserBO;
import com.jdec.platform.hr.api.dto.request.CreateUserReq;
import com.jdec.platform.hr.api.dto.request.UpdateUserReq;
import com.jdec.platform.hr.api.dto.response.UserBasicInfoResp;
import com.jdec.platform.hr.api.dto.response.UserTreeNodeResp;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 用户模块暴露的 API */
public interface UserApi {

    /**
     * 根据手机号获取用户的主体ID
     *
     * @param phone 手机号
     * @return 主体ID
     */
    Optional<Integer> getSubjectIdByPhone(String phone);

    /**
     * 根据用户ID获取用户的主体ID
     *
     * @param userId 用户ID
     * @return 主体ID
     */
    Optional<Integer> getSubjectIdByUserId(Long userId);

    /**
     * 根据手机号获取用户的企业微信用户ID（short_name）
     *
     * @param phone 手机号
     * @return 企业微信用户ID
     */
    Optional<String> getWeChatUserIdByPhone(String phone);

    /**
     * 根据手机号获取用户基本信息
     *
     * @param phone 手机号
     * @return 用户基本信息
     */
    Optional<UserBasicInfoResp> getUserBasicInfoByPhone(String phone);

    /**
     * 创建用户
     *
     * @param request 创建用户请求
     * @return 用户基本信息
     */
    UserBasicInfoResp createUser(CreateUserReq request);

    /**
     * 更新用户
     *
     * @param request 更新用户请求
     * @return 用户基本信息
     */
    UserBasicInfoResp updateUser(UpdateUserReq request);

    List<UserBO> listByIds(Set<Long> allUserIds);

    UserBO getUserById(Long userId);

    List<UserBO> listBySubjectId(Long subjectId);

    /**
     * 获取人员架构树
     *
     * @param userType 用户类型 0=全部 1=内部 2=外部
     * @return 人员架构树
     */
    List<UserTreeNodeResp> getUserTreeList(Integer userType);
}
