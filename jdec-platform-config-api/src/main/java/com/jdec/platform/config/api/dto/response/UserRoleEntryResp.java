package com.jdec.platform.config.api.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户角色关联响应 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRoleEntryResp {
    /** 关联 ID */
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 角色 ID */
    private Long roleId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
