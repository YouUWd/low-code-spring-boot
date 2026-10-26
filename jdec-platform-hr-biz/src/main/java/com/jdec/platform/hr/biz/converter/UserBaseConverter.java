package com.jdec.platform.hr.biz.converter;

import com.jdec.platform.hr.api.dto.response.HelloResp;
import com.jdec.platform.hr.api.dto.response.UserBaseResp;
import com.jdec.platform.hr.biz.entity.UserBase;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** UserBase 转换器工具类 */
public class UserBaseConverter {

    private UserBaseConverter() {
        // 工具类，不允许实例化
    }

    /** Entity 转 DTO */
    public static UserBaseResp toUserBaseResp(UserBase entity) {
        if (entity == null) {
            return null;
        }

        return UserBaseResp.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .userName(entity.getUserName())
                .shortName(entity.getShortName())
                .sex(entity.getSex())
                .jobClass(entity.getJobClass())
                .positionId(entity.getPositionId())
                .jobLevel(entity.getJobLevel())
                .jobGrade(entity.getJobGrade())
                .enterDate(entity.getEnterDate())
                .resignationDate(entity.getResignationDate())
                .userIDCard(entity.getUserIDCard())
                .birthday(entity.getBirthday())
                .marital(entity.getMarital())
                .address(entity.getAddress())
                .helloResp(new HelloResp("Hell!", "It works!"))
                .build();
    }

    /** Entity 列表转 DTO 列表 */
    public static List<UserBaseResp> toUserBaseResps(List<UserBase> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream()
                .map(UserBaseConverter::toUserBaseResp)
                .collect(Collectors.toList());
    }

    /** DTO 转 Entity */
    public static UserBase toUserBase(UserBaseResp dto) {
        if (dto == null) {
            return null;
        }

        return UserBase.builder()
                .id(dto.getId())
                .userId(dto.getUserId())
                .userName(dto.getUserName())
                .shortName(dto.getShortName())
                .sex(dto.getSex())
                .jobClass(dto.getJobClass())
                .positionId(dto.getPositionId())
                .jobLevel(dto.getJobLevel())
                .jobGrade(dto.getJobGrade())
                .enterDate(dto.getEnterDate())
                .resignationDate(dto.getResignationDate())
                .userIDCard(dto.getUserIDCard())
                .birthday(dto.getBirthday())
                .marital(dto.getMarital())
                .address(dto.getAddress())
                .build();
    }

    /** 批量转换 */
    public static List<UserBase> toUserBases(List<UserBaseResp> dtos) {
        if (dtos == null) {
            return Collections.emptyList();
        }
        return dtos.stream().map(UserBaseConverter::toUserBase).collect(Collectors.toList());
    }
}
