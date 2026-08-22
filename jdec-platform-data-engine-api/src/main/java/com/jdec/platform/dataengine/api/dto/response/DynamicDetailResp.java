package com.jdec.platform.dataengine.api.dto.response;

import java.util.List;
import java.util.Map;

public class DynamicDetailResp {
    private Long moduleId;
    private Map<String, Object> mainData;
    private Map<String, List<Map<String, Object>>> subData;

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

    public Map<String, List<Map<String, Object>>> getSubData() {
        return subData;
    }

    public void setSubData(Map<String, List<Map<String, Object>>> subData) {
        this.subData = subData;
    }
}
