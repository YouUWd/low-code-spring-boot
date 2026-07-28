package com.jdec.platform.monitorlog.biz.entity.enumtype;

import com.baomidou.mybatisplus.annotation.EnumValue;

/** 日志类型枚举 */
public enum LogTypeEnum {
    LOGIN("login", "登录"),
    LOGOUT("logout", "登出"),
    CHANGEROLE("changerole", "切换角色"),
    MOCKUSER("mockuser", "模拟用户"),
    MOCKROLE("mockrole", "模拟角色"),
    MOCKCHANGEROLE("mockchangerole", "模拟切换角色"),
    MOCKRESET("mockreset", "重置模拟"),
    CHANGESUBJECT("changesubject", "切换主体");

    @EnumValue private final String code;
    private final String description;

    LogTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static LogTypeEnum fromCode(String code) {
        for (LogTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
