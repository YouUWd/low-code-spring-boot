package com.jdec.platform.data.api;

import com.jdec.platform.data.api.dto.request.DynamicOptionReq;
import com.jdec.platform.data.api.dto.request.DynamicQueryReq;
import com.jdec.platform.data.api.dto.request.DynamicSaveReq;
import com.jdec.platform.data.api.dto.request.EngineHeaderReq;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.DynamicOptionItem;
import com.jdec.platform.data.api.dto.response.EngineDataResult;
import com.jdec.platform.data.api.dto.response.EngineHeaderResp;
import java.util.List;
import java.util.Map;

/** 数据引擎核心接口 统一封装元数据获取与数据读写，业务层无需穿透依赖 config 层 */
public interface DataApi {

    // ==================== 读契约 (彻底归一) ====================

    /** 获取动态列表表头配置 (动静分离，前端初始化时调用) */
    EngineHeaderResp getHeader(EngineHeaderReq req);

    /** 通用动态数据集查询 (纯数据引擎，返回 DataPage 分页列表) */
    DataPage<Map<String, Object>> query(DynamicQueryReq req);

    /**
     * 单据详情精准查询快捷方法
     *
     * @param moduleId 当前模块 ID
     * @param id 单据权威主键 ID
     * @return 包含单条实体 Map 与元数据的详情响应
     */
    default EngineDataResult<Map<String, Object>> getDetail(Long moduleId, Long id) {
        return getDetail(moduleId, id, (List<Long>) null);
    }

    /**
     * 单据详情精准查询 (支持指定显式投影字段列表)
     *
     * @param moduleId 当前模块 ID
     * @param id 单据权威主键 ID
     * @param fields 可选的显式投影字段 ID 列表 (限制为模块本身及子模块的字段)
     * @return 包含单条实体 Map 与元数据的详情响应
     */
    EngineDataResult<Map<String, Object>> getDetail(Long moduleId, Long id, List<Long> fields);

    /** 通用字段搜索下拉候选项查询 (获取当前模块上下文下的去重候选值列表) */
    List<DynamicOptionItem> getOptions(DynamicOptionReq req);

    // ==================== 写契约 ====================

    /** 自相似主子表同构原子保存/更新 */
    Long save(DynamicSaveReq req);
}
