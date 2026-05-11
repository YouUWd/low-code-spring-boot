package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.DataQueryReq;
import com.jdec.platform.shared.model.PageResult;
import java.util.Map;

/** 数据查询接口 */
public interface DataQueryApi {

    /**
     * 根据模块编码执行动态查询
     *
     * @param moduleCode 模块编码
     * @param projectNo 项目编码
     * @param request 查询请求参数
     * @return 分页查询结果
     */
    PageResult<Map<String, Object>> executeQuery(
            String moduleCode, String projectNo, DataQueryReq request);
}
