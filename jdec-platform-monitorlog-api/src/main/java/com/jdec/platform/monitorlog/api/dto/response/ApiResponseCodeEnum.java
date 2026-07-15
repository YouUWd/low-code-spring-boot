package com.jdec.platform.monitorlog.api.dto.response;

import lombok.Getter;

@Getter
public enum ApiResponseCodeEnum {
    OK(200, "ok"),
    BAD_REQUEST(400, "请求参数不对"),
    SERVER_ERROR(500, "服务器内部错误"),
    CHECK_RESIGNATION_ERROR(600, "该员工担任公司或者部门负责人，请联系部门管理人员变更后离职！"),
    CHECK_USER_ROLE_ERROR(601, "当前登录用户没有对应的角色！"),
    CHECK_USER_CURRENT_ROLE_ERROR(602, "当前用户角色已被切换，请重试!"),
    BAD_JSON_ENCODE_ERROR(700, "JSON序列化异常"),
    BAD_MODULE_ERROR(800, "MODULE信息不存在"),
    BAD_MODULE_FILED_ERROR(801, "MODULE字段信息不存在"),
    BAD_MODULE_RIGHT_NODE_ERROR(901, "模块没有可供查看的字段权限"),
    BAD_MODULE_DATA_ERROR(902, "模块数据错误"),
    BAD_MODULE_FIELD_WRITE_RIGHT_ERROR(903, "模块字段没有编辑权限"),
    BAD_MODULE_FIELD_APPLY_RIGHT_ERROR(904, "模块字段没有申请权限"),
    BAD_MODULE_NO_TABLEHEADER_ERROR(905, "模块没有可供查看的表头配置"),
    BAD_MODULE_FIELD_APPLY_DATA_ERROR(906, "模块数据错误");

    private int code;

    private String message;

    ApiResponseCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
