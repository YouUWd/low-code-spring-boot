package com.jdec.platform.dataengine.biz.enums;

import lombok.Getter;

@Getter
public enum RelationType {
    ONE_TO_ONE("1:1"),
    ONE_TO_MANY("1:N"),
    MANY_TO_ONE("N:1");

    private String type;

    private RelationType(String type) {
        this.type = type;
    }
    ;

    public static RelationType getRelationType(String type) {
        for (RelationType relationType : RelationType.values())
            if (relationType.type.equals(type)) return relationType;
        return null;
    }
}
