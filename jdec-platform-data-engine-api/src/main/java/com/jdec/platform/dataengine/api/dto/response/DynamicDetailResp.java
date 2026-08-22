package com.jdec.platform.dataengine.api.dto.response;

import java.util.List;
import java.util.Map;

public class DynamicDetailResp {
    private Long moduleId;
    private Map<String, Object> data;
    private Map<String, List<DynamicDetailResp>> subModules;

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

    public Map<String, List<DynamicDetailResp>> getSubModules() {
        return subModules;
    }

    public void setSubModules(Map<String, List<DynamicDetailResp>> subModules) {
        this.subModules = subModules;
    }
}
