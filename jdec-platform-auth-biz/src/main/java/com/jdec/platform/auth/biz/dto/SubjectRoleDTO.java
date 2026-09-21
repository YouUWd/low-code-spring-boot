package com.jdec.platform.auth.biz.dto;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前用户角色缓存（迁移自 student-manage 的 SubjectRoleDTO）。
 *
 * <p>存放在 Redis key {@code jdec:student:user_current_role:{realLoginUserId}} 中。 非模拟登录时只有 {@code
 * subjectId}/{@code roleId}；模拟登录时额外记录 {@code mockUserId}/{@code mockSubjectId}/{@code mockRoleId}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectRoleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 登录人的主体ID */
    private Long subjectId;

    /** 登录人的角色ID */
    private Long roleId;

    /** 当前登录人正在使用的模拟用户ID */
    private Long mockUserId;

    /** 当前登录人正在使用的模拟用户的主体ID */
    private Long mockSubjectId;

    /** 当前登录人正在使用的模拟用户的角色ID */
    private Long mockRoleId;
}
