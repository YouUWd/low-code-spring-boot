package com.jdec.platform.dataengine.biz.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class DataSnapshotTableDTO implements Serializable {

    private String tabName;

    private List<Map<Object, Object>> rows;
}
