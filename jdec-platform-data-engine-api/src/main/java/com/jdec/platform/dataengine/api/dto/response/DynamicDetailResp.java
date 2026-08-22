package com.jdec.platform.dataengine.api.dto.response;

import java.util.List;
import java.util.Map;

public class DynamicDetailResp {
    private Long moduleId;
    private Map<String, Object> mainData;
    private Map<String, List<DynamicDetailResp>> subModules;

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Map<String, Object> getMainData() {
        return mainData;
    }

    public void setMainData(Map<String, Object> mainData) {
        this.mainData = mainData;
    }

    public Map<String, List<DynamicDetailResp>> getSubModules() {
        return subModules;
    }

    public void setSubModules(Map<String, List<DynamicDetailResp>> subModules) {
        this.subModules = subModules;
    }
}
