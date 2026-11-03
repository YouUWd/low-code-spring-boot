package com.jdec.platform.common.biz.support.loader;

import com.jdec.platform.common.api.dto.response.UserSimpleResp;
import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.dto.request.QueryUsersByIdsReq;
import com.jdec.platform.config.api.dto.response.SysUserPageResp;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/** 用户简要信息批量加载器 (供数据行/候选项增强等后置加工复用) */
@Slf4j
@Component
public class UserInfoLoader {

    private final SysUserApi sysUserApi;

    @Autowired
    public UserInfoLoader(@Autowired(required = false) SysUserApi sysUserApi) {
        this.sysUserApi = sysUserApi;
    }

    /**
     * 批量加载用户信息
     *
     * @param userIds 用户 ID 集合（兼容 userId 与主键 id）
     * @return userId -> 用户简要信息
     */
    public Map<Long, UserSimpleResp> loadUserMap(Set<Long> userIds) {
        Map<Long, UserSimpleResp> userMap = new HashMap<>();
        if (CollectionUtils.isEmpty(userIds) || sysUserApi == null) {
            return userMap;
        }
        try {
            QueryUsersByIdsReq queryReq = new QueryUsersByIdsReq();
            queryReq.setUserIds(new ArrayList<>(userIds));
            List<SysUserPageResp> userList = sysUserApi.getUsersByUserIds(queryReq);
            if (userList != null) {
                for (SysUserPageResp u : userList) {
                    if (u == null) {
                        continue;
                    }
                    UserSimpleResp resp =
                            UserSimpleResp.builder()
                                    .userId(u.getUserId())
                                    .userName(u.getUserName())
                                    .userAvatar(u.getUserAvatar())
                                    .workNumber(u.getWorkNumber())
                                    .build();
                    if (u.getUserId() != null) {
                        userMap.put(u.getUserId(), resp);
                    }
                    if (u.getId() != null) {
                        userMap.putIfAbsent(u.getId(), resp);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("批量查询用户信息失败: userIds={}, err={}", userIds, ex.getMessage());
        }
        return userMap;
    }

    /** 获取用户信息，未命中时返回仅含 userId 的兜底对象 */
    public static UserSimpleResp orFallback(Map<Long, UserSimpleResp> userMap, Long uid) {
        UserSimpleResp resp = userMap.get(uid);
        return resp != null ? resp : UserSimpleResp.builder().userId(uid).build();
    }
}
