package com.jdec.platform.dataengine.biz.enums;

import lombok.Getter;

// 引用类型美剧
@Getter
public enum ReferenceBizType {
    // 词云
    WORD_CLOUD("WORD_REF"),
    // 内部引用
    INTERNAL_REF("INTERNAL_REF");

    private final String type;

    ReferenceBizType(String type) {
        this.type = type;
    }
}
