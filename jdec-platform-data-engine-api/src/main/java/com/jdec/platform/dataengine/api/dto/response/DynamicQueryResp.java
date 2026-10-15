package com.jdec.platform.dataengine.api.dto.response;

import java.util.List;
import java.util.Map;

public class DynamicQueryResp {
    private Map<String, Object> meta;
    private Map<String, Object> pagination;
    private List<Map<String, Object>> records;

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

    public Map<String, Object> getPagination() {
        return pagination;
    }

    public void setPagination(Map<String, Object> pagination) {
        this.pagination = pagination;
    }

    public List<Map<String, Object>> getRecords() {
        return records;
    }

    public void setRecords(List<Map<String, Object>> records) {
        this.records = records;
    }
}
