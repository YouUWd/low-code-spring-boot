package com.jdec.platform.dataengine.biz.context;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VersionDataQueryContext {

    private String tableName;

    private List<String> businessNos;

    private List<String> fields;

    // 词云关系ID
    private Map<String, Set<String>> relationNoWordUUIDMap;

    private String bizType;
}
