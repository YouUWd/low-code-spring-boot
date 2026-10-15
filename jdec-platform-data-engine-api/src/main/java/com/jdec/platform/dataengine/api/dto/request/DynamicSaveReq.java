package com.jdec.platform.dataengine.api.dto.request;

import java.util.Map;

public class DynamicSaveReq {
    private Long moduleId;
    private Map<String, Object> tables;

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Map<String, Object> getTables() {
        return tables;
    }

    public void setTables(Map<String, Object> tables) {
        this.tables = tables;
    }
}
