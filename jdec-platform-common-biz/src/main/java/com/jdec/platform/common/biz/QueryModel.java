package com.jdec.platform.common.biz;

public enum QueryModel {
    CREATE("create"),
    EDIT("edit"),
    VIEW("view"),
    CHANGE_REQUEST("change_request");

    private final String type;

    QueryModel(String type) {
        this.type = type;
    }
}
