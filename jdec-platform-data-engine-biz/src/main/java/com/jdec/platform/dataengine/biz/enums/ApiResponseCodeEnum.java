package com.jdec.platform.dataengine.biz.enums;

import lombok.Getter;

@Getter
public enum ApiResponseCodeEnum {
    OK(200, "ok"),
    LONGIN_FORBIDDEN(401, "未登录或令牌已过期"),
    BAD_REQUEST(400, "请求参数不对"),
    SERVER_ERROR(500, "服务器内部错误"),

    // 审批链相关
    APPROVAL_PROCESS_ERROR(600, "审批链已发生变化,页面将刷新!"),
    APPROVAL_PROCEDURE_CHANGE(601, "审批流程异常"),
    APPROVAL_FIELD_VALUE_CHAIN_TYPE_CONFIG_ERROR(602, "配置错误，多个字段值关联了审批链分类"),
    APPROVAL_CREATE_AUTO_APPROVAL_TASK_ERROR(603, "创建自动审批任务异常"),

    WARNING(700, "页面无任何可查看的字段。"),
    BAD_JSON_ENCODE_ERROR(701, "JSON序列化异常"),

    // 角色相关
    CHECK_USER_ROLE_ERROR(801, "当前用户使用的角色发生变化！"),
    USER_NO_RIGHT(803, "用户无任何角色"),
    USER_ROLE_NO_PERMISSION(804, "当前角色无权限"),
    MOCK_USER_NO_RIGHT(805, "模拟用户无任何角色"),
    USER_NO_MOCK_RIGHT(806, "用户没有模拟权限"),

    // 模块权限节点相关
    BAD_MODULE_RIGHT_NODE_ERROR(901, "模块没有可供查看的字段权限"),
    BAD_MODULE_DATA_ERROR(902, "模块配置数据错误"),
    BAD_MODULE_FIELD_WRITE_RIGHT_ERROR(903, "模块字段没有编辑权限"),
    BAD_MODULE_FIELD_APPLY_RIGHT_ERROR(904, "模块字段没有申请权限"),
    BAD_MODULE_NO_TABLEHEADER_ERROR(905, "模块没有可供查看的表头配置"),
    BAD_MODULE_FIELD_APPLY_DATA_ERROR(906, "模块保存数据错误"),
    BAD_MENU_RIGHT_NODE_ERROR(907, "页面没有可供查看的字段权限"),
    BAD_MENU_FIELD_APPLY_RIGHT_ERROR(908, "页面模块没有申请权限"),
    BAD_MODULE_SAVE_DATA_ERROR(909, "模块保存数据错误");

    private int code;

    private String message;

    ApiResponseCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public static ApiResponseCodeEnum getEnumByCode(int code) {
        for (ApiResponseCodeEnum codeEnum : ApiResponseCodeEnum.values()) {
            if (codeEnum.code == code) {
                return codeEnum;
            }
        }
        return null;
    }
}
