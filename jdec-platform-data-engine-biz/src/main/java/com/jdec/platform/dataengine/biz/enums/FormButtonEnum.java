package com.jdec.platform.dataengine.biz.enums;

import lombok.Getter;

@Getter
public enum FormButtonEnum {
    VIEW("view", "查阅"),
    EDITE("edit", "编辑"),
    CREATE("create", "申请"),
    DELETE("delete", "删除"),
    ;

    private String key;
    private String name;

    private FormButtonEnum(String key, String name) {
        this.key = key;
        this.name = name;
    }

    public static FormButtonEnum getFormButtonEnumByKey(String key) {
        for (FormButtonEnum formButtonEnum : FormButtonEnum.values()) {
            if (formButtonEnum.getKey().equals(key)) {
                return formButtonEnum;
            }
        }
        return null;
    }
}
