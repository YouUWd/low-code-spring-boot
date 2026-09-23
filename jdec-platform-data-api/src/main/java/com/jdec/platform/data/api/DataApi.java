package com.jdec.platform.data.api;

import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.request.SaveReq;
import com.jdec.platform.data.api.dto.response.DataDetail;
import com.jdec.platform.data.api.dto.response.DataOption;
import com.jdec.platform.data.api.dto.response.DataPage;
import java.util.List;
import java.util.Map;

/** 数据引擎核心接口 统一封装元数据获取与数据读写，业务层无需穿透依赖 config 层 */
public interface DataApi {

    // ==================== 读契约 (彻底归一) ====================

    /** 通用动态数据集查询 (纯数据引擎，返回 DataPage 分页列表) */
    DataPage<Map<String, Object>> query(QueryReq req);

    /** 通用动态数据集数量统计 (纯数据引擎，返回满足条件的记录总数) */
    Long count(QueryReq req);

    /**
     * 单据详情精准查询快捷方法
     *
     * @param moduleId 当前模块 ID
     * @param id 单据权威主键 ID
     * @return 包含单条实体 Map 与元数据的详情响应
     */
    default DataDetail<Map<String, Object>> getDetail(Long moduleId, Long id) {
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
    DataDetail<Map<String, Object>> getDetail(Long moduleId, Long id, List<Long> fields);

    /** 通用字段搜索下拉候选项查询 (获取当前模块上下文下的去重候选值列表) */
    List<DataOption> getOptions(OptionReq req);

    // ==================== 写契约 ====================

    /** 自相似主子表同构原子保存/更新 (返回根记录 ID) */
    Long save(SaveReq req);

    /** 自相似主子表同构原子保存/更新 (返回各个模块的主键映射) */
    Map<String, Object> saveAndCollectIds(SaveReq req);
}
