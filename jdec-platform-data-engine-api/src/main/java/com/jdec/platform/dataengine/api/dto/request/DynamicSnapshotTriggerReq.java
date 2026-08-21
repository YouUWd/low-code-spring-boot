package com.jdec.platform.dataengine.api.dto.request;

public class DynamicSnapshotTriggerReq {
    private Long moduleId;
    private Long dataId;
    private Integer finalStatusValue;
    private String remark;

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getDataId() {
        return dataId;
    }

    public void setDataId(Long dataId) {
        this.dataId = dataId;
    }

    public Integer getFinalStatusValue() {
        return finalStatusValue;
    }

    public void setFinalStatusValue(Integer finalStatusValue) {
        this.finalStatusValue = finalStatusValue;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
