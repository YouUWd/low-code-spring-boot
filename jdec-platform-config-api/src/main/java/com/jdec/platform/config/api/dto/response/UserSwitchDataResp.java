package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户切换数据响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户切换数据响应")
public class UserSwitchDataResp {
    @Schema(description = "系统用户列表")
    private List<SysUserInfo> sysUsers;

    @Schema(description = "主体ID列表")
    private List<Long> subjectIds;

    /** 系统用户信息 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "系统用户信息")
    public static class SysUserInfo {
        @Schema(description = "自增ID")
        private Long id;

        @Schema(description = "员工id")
        private Long userId;

        @Schema(description = "用户类型 1内部 2外部")
        private Integer userType;

        @Schema(description = "名称")
        private String userName;

        @Schema(description = "工号")
        private String workNumber;

        @Schema(description = "头像")
        private String userAvatar;

        @Schema(description = "性别")
        private Integer sex;

        @Schema(description = "主体id")
        private Long subjectId;

        @Schema(description = "部门ids")
        private String departIds;

        @Schema(description = "部门名称")
        private String departName;
    }
}
