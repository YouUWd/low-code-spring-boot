package com.jdec.platform.dataengine.biz.dto;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class DataSnapshotModuleDTO {

    private Long mId;

    private String mType;

    private Long pid;

    private Map<String, List<Map<String, Object>>> modData;
}
