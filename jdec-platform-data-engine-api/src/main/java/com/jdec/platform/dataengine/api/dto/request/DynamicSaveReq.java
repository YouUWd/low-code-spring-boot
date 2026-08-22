package com.jdec.platform.dataengine.api.dto.request;

import java.util.List;
import java.util.Map;

public class DynamicSaveReq {
    private Long moduleId;
    private Map<String, Object> mainData;
    private Map<String, List<DynamicSaveReq>> subModules;

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

    public Map<String, List<DynamicSaveReq>> getSubModules() {
        return subModules;
    }

    public void setSubModules(Map<String, List<DynamicSaveReq>> subModules) {
        this.subModules = subModules;
    }
}
