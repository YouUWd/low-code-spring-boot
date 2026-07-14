package com.jdec.platform.common_business.biz.service.impl;

import com.jdec.platform.common_business.api.UserServiceApi;
import com.jdec.platform.common_business.api.dto.response.UserTreeResp;
import com.jdec.platform.common_business.biz.service.ConfigCenterApiService;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserServiceApi {

    private final ConfigCenterApiService configCenterApiService;

    /**
     * 获取用户树 - 对应 PHP UserUtils::getUserTreeList
     *
     * <p>PHP 返回格式: - 部门节点: {id: "E1", label, pid: "E0", type: 1, children: [...]} - 用户节点: {id:
     * "U1", sys_id: 1, label, depart_id: "E1", sex, avatar, work_number, type: 3, depart_name}
     *
     * <p>关键逻辑： 1. withExistsRole=true时，只返回有用户的部门及其父路径（枝叶修剪） 2. PHP的get_tree_children会复制数据，不修改原数组 3.
     * Java需要创建新Map来模拟这个行为，避免Map被buildNestedTree修改
     */
    @Override
    public List<UserTreeResp> getUserTreeList(Long subjectId, Integer userType, String userName) {
        // userType: 0=全部, 1=内部, 2=外部
        boolean withExistsRole = (userType == 0);

        // 权限检查：验证是否有模拟登录权限
        if (withExistsRole && !hasSimulateLoginPermission()) {
            return Collections.emptyList();
        }

        return configCenterApiService.getUserTreeByUserType(subjectId, userType, userName);
    }

    private boolean hasSimulateLoginPermission() {
        return true;
    }
}
