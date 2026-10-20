package com.jdec.platform.shared.validation.dynamic;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;

/** 请求树中的一条记录，是校验期间使用的索引节点，不修改原始请求。 */
public final class RecordNode {

    private final String moduleId;
    private final int recordIndex;
    private final String path;
    private final ObjectNode row;
    private final RecordNode parent;
    private final List<RecordNode> children = new ArrayList<>();

    RecordNode(String moduleId, int recordIndex, String path, ObjectNode row, RecordNode parent) {
        this.moduleId = moduleId;
        this.recordIndex = recordIndex;
        this.path = path;
        this.row = row;
        this.parent = parent;
    }

    public String moduleId() {
        return moduleId;
    }

    public int recordIndex() {
        return recordIndex;
    }

    public String path() {
        return path;
    }

    public ObjectNode row() {
        return row;
    }

    public RecordNode parent() {
        return parent;
    }

    public List<RecordNode> children() {
        return children;
    }
}
