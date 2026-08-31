package com.jdec.platform.data.api;

import com.jdec.platform.data.api.dto.request.BatchDynamicQueryReq;
import com.jdec.platform.data.api.dto.request.BatchDynamicSaveReq;
import com.jdec.platform.data.api.dto.request.DynamicDetailReq;
import com.jdec.platform.data.api.dto.request.DynamicQueryReq;
import com.jdec.platform.data.api.dto.request.DynamicSaveReq;
import com.jdec.platform.data.api.dto.response.BatchEngineDataResult;
import com.jdec.platform.data.api.dto.response.BatchSaveResp;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.EngineDataResult;
import java.util.Map;

/** 数据引擎核心接口 统一封装元数据获取与数据读写，业务层无需穿透依赖 config 层 */
public interface DataEngineApi {

    // ==================== 读契约 (单模块通用 + 多模块批量) ====================

    /** 通用动态数据集查询 (支持 viewMode: LIST/DETAIL/ALL 自适应决定结构与元数据) */
    EngineDataResult<DataPage<Map<String, Object>>> query(DynamicQueryReq req);

    /** 多模块批量并发查询 (用于多 Tab 详情页、仪表盘等场景一次性加载) */
    BatchEngineDataResult batchQuery(BatchDynamicQueryReq req);

    /** 动态数据集/单条详情查询门面 (内部转为 query 且 pageSize=1) */
    EngineDataResult<Map<String, Object>> getDetail(DynamicDetailReq req);

    // ==================== 写契约 (单模块同构 + 多模块原子) ====================

    /** 主子表同构原子保存/更新 */
    Long save(DynamicSaveReq req);

    /** 多模块同构原子批量保存 (主子模块跨表原子事务落库，自动外键传播) */
    BatchSaveResp batchSave(BatchDynamicSaveReq req);
}
