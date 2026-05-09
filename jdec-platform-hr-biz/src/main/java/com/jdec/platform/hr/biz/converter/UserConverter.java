package com.jdec.platform.hr.biz.converter;

import com.jdec.platform.hr.api.dto.response.UserBasicInfoResp;
import com.jdec.platform.hr.biz.entity.User;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** User 转换器工具类 */
public class UserConverter {

    private UserConverter() {
        // 工具类，不允许实例化
    }

    /** Entity 转 DTO */
    public static UserBasicInfoResp toUserBasicInfo(User entity) {
        if (entity == null) {
            return null;
        }

        return UserBasicInfoResp.builder()
                .id(entity.getId())
                .userName(entity.getUserName())
                .phone(entity.getPhone())
                .subjectId(entity.getSubjectId())
                .shortName(entity.getShortName())
                .workNumber(entity.getWorkNumber())
                .departmentName(entity.getDepartmentName())
                .userStatus(entity.getUserStatus())
                .build();
    }

    /** Entity 列表转 DTO 列表 */
    public static List<UserBasicInfoResp> toUserBasicInfos(List<User> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream().map(UserConverter::toUserBasicInfo).collect(Collectors.toList());
    }
}
