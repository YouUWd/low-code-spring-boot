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
public interface DataEngineApi {

    // ==================== 读契约 (彻底归一) ====================

    /** 获取动态列表表头配置 (动静分离，前端初始化时调用) */
    EngineHeaderResp getHeader(EngineHeaderReq req);

    /** 通用动态数据集查询 (纯数据引擎，返回 DataPage 分页列表) */
    DataPage<Map<String, Object>> query(DynamicQueryReq req);

    /**
     * 单据详情精准查询 (第一层级为单条记录 Map，支持携带子模块展开树)
     *
     * @param moduleId 当前模块 ID
     * @param id 单据权威主键 ID
     * @param children 可选的下级子模块展开请求树 (支持多条明细 records 树形级联)
     * @return 包含单条实体 Map 与元数据的详情响应
     */
    EngineDataResult<Map<String, Object>> getDetail(
            Long moduleId, Long id, List<DynamicQueryReq> children);

    /**
     * 单据详情精准查询快捷方法 (无下级展开)
     *
     * @param moduleId 当前模块 ID
     * @param id 单据权威主键 ID
     */
    default EngineDataResult<Map<String, Object>> getDetail(Long moduleId, Long id) {
        return getDetail(moduleId, id, null);
    }

    /** 通用字段搜索下拉候选项查询 (获取当前模块上下文下的去重候选值列表) */
    List<DynamicOptionItem> getOptions(DynamicOptionReq req);

    // ==================== 写契约 ====================

    /** 自相似主子表同构原子保存/更新 */
    Long save(DynamicSaveReq req);
}
