package com.jdec.platform.common_business.api.dto.common;

import java.io.Serializable;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class SubjectRoleDTO implements Serializable {

    private Long subjectId;

    private Long roleId;

    // 当前登录人正在使用的 模拟用户
    private Long mockUserId;

    // 当前登录人正在使用的 模拟用户的角色主题
    private Long mockSubjectId;

    // 当前登录人正在使用的 模拟用户的角色
    private Long mockRoleId;
}
