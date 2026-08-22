package com.jdec.platform.dataengine.api.dto.request;

import java.util.List;
import java.util.Map;

public class DynamicSaveReq {
    private Long moduleId;
    private Map<String, Object> data;
    private Map<String, List<DynamicSaveReq>> subModules;

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public Map<String, List<DynamicSaveReq>> getSubModules() {
        return subModules;
    }

    public void setSubModules(Map<String, List<DynamicSaveReq>> subModules) {
        this.subModules = subModules;
    }
}
