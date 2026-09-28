package com.jdec.platform.data.api;

import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.request.DetailQueryReq;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.PageQueryReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.response.DataOption;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.SaveResp;
import java.util.List;

/**
 * 通用低代码数据引擎客户端接口 (DataApi)
 *
 * <p>统一提供读写同构的自相似数据区块 (DataBlock) 读写与查询能力。
 */
public interface DataApi {

    // ==================== 读契约 ====================

    /** 动态列表分页查询 (PageQueryReq -> DataPage<DataBlock>) */
    DataPage<DataBlock> query(PageQueryReq req);

    /** 动态数据集数量统计 */
    Long count(QueryReq req);

    /** 单据详情精准查询 (DetailQueryReq -> DataBlock 读写同源区块) */
    DataBlock getDetail(DetailQueryReq req);

    /** 单据详情精准查询快捷方法 */
    default DataBlock getDetail(Long moduleId, Long id) {
        return getDetail(DetailQueryReq.builder().moduleId(moduleId).id(id).build());
    }

    /** 单据详情精准查询快捷方法 (带投影字段) */
    default DataBlock getDetail(Long moduleId, Long id, List<Long> fields) {
        return getDetail(DetailQueryReq.builder().moduleId(moduleId).id(id).fields(fields).build());
    }

    /** 字段搜索下拉候选项查询 */
    List<DataOption> getOptions(OptionReq req);

    // ==================== 写契约 (100% 读写同构强约束) ====================

    /**
     * 自相似主子表同构原子保存/更新
     *
     * @param block 待持久化的业务数据区块 (结构与 getDetail 返回值 100% 同源)
     * @return 包含新生成主键与最新数据树的同构响应
     */
    SaveResp save(DataBlock block);
}
