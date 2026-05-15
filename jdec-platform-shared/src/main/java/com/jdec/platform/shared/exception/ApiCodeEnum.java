package com.jdec.platform.shared.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * API 状态码枚举
 *
 * <p>统一的业务状态码定义
 */
@Getter
@AllArgsConstructor
public enum ApiCodeEnum {

    /** 成功 - 200 */
    SUCCESS(200, "操作成功"),

    /** 登录过期 - 401 */
    OVERDUE(401, "登录已过期"),

    /** 权限变动 - 600 */
    RIGHT_CHANGE(600, "权限已变动"),

    /** 异常 - 700 (Toast 提示) */
    WARNING(700, "操作失败"),

    /** 异常 - 701 (Modal 弹窗) */
    WARNING_MODAL(701, "操作失败"),

    /** 存在重复值 - 702 */
    WARNING_EXISTS(702, "数据已存在"),

    /** 用户重置 - 801 */
    USER_RESET(801, "用户信息已重置"),

    /** 用户无角色重置角色获取 - 802 */
    USER_RESET_ROLE(802, "用户角色已重置");

    private final int code;
    private final String message;
}
