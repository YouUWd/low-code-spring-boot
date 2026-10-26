package com.jdec.platform.shared.validation.dynamic;

import java.util.List;
import java.util.Objects;

/** 请求树索引。 */
public final class RequestTree {

    private final List<RecordNode> roots;
    private final List<RecordNode> allRecords;

    RequestTree(List<RecordNode> roots, List<RecordNode> allRecords) {
        this.roots = List.copyOf(roots);
        this.allRecords = List.copyOf(allRecords);
    }

    /** 顶层模块的记录。 */
    public List<RecordNode> roots() {
        return roots;
    }

    /** 全部记录（前序遍历顺序）。 */
    public List<RecordNode> allRecords() {
        return allRecords;
    }

    public List<RecordNode> recordsByModule(String moduleId) {
        return allRecords.stream().filter(n -> Objects.equals(n.moduleId(), moduleId)).toList();
    }
}
