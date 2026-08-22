package com.jdec.platform.dataengine.api.dto.response;

import java.util.Map;

public class DynamicDetailResp {
    private Long moduleId;
    private String moduleCode;
    private String primaryTable;
    private Map<String, Object> tables;

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public String getModuleCode() {
        return moduleCode;
    }

    public void setModuleCode(String moduleCode) {
        this.moduleCode = moduleCode;
    }

    public String getPrimaryTable() {
        return primaryTable;
    }

    public void setPrimaryTable(String primaryTable) {
        this.primaryTable = primaryTable;
    }

    public Map<String, Object> getTables() {
        return tables;
    }

    public void setTables(Map<String, Object> tables) {
        this.tables = tables;
    }
}
