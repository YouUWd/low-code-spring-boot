package com.jdec.platform.approval.api.resp;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ActionLogResp {
    /** 主键id */
    private Long id;

    /** 图标类型 */
    private String actionIcon;

    /** 图标url */
    private String actionPath;

    /** 操作记录 */
    private String actionName;

    /** 操作类型标题 */
    private String actionTypeTitle;

    /** 日志内容 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createdDate;

    /** 操作人信息 */
    private UserVO createdUser;

    /** 真实登录用户信息 */
    private UserVO realUser;

    @Data
    public static class UserVO {
        /** 主键id */
        private Long id;

        /** 部门名称 */
        private String departName;

        /** 职位名称 */
        private String positionName;

        /** 头像 */
        private String userAvatar;

        /** 名称 */
        private String userName;

        /** 工号 */
        private String workNumber;
    }
}
