package com.jdec.platform.dataengine.biz.dto;

import java.io.Serializable;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChangeItemDTO implements Serializable {

    private String tableName;

    private String nodeId;

    private String parentNodeId;

    private String action;

    private Map<String, Object> changeContent;
}
