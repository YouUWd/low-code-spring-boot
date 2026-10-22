package com.jdec.platform.dataengine.biz.referencequery;

import com.jdec.platform.dataengine.biz.context.VersionDataQueryContext;
import java.util.List;
import java.util.Map;

/** 版本数据来源查询接口类(词云或者数据库) */
public interface VersionDataQueryStrategy {

    List<Map<String, Object>> queryVersionDatas(VersionDataQueryContext versionDataQueryContext);

    Boolean isRefTo(String bizType);
}
