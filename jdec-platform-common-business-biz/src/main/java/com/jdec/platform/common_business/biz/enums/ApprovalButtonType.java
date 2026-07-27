package com.jdec.platform.common_business.biz.enums;

public enum ApprovalButtonType {
    VIEW("view", "查阅"),
    SUBMIT("submit", "提交"),
    EDIT("edit", "编辑"),
    CANCEL("cancel", "取消"),
    PASS("pass", "通过"),
    REJECT("reject", "驳回"),
    HANDLE("handle", "办理"),

    CREATE("create", "创建"),
    CHANGE("change", "变更");

    final String key;
    final String title;

    ApprovalButtonType(String key, String title) {
        this.key = key;
        this.title = title;
    }

    public static ApprovalButtonType getApprovalButtonTypeByKey(String keyStr) {
        for (ApprovalButtonType buttonType : ApprovalButtonType.values()) {
            if (buttonType.key.equals(keyStr)) {
                return buttonType;
            }
        }
        return null;
    }
}
