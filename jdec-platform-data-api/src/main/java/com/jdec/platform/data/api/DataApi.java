package com.jdec.platform.data.api;

import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.request.DetailQueryReq;
import com.jdec.platform.data.api.dto.request.ForkVersionReq;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.PageQueryReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.DataSaveResp;
import java.util.List;

/**
 * 通用低代码数据引擎客户端接口 (DataApi)
 *
 * <p>统一提供基于业务聚合树 (Module Tree) 与物理表投影规约的自相似数据区块 (DataBlock) 读写与查询能力。
 */
public interface DataApi {

    // ==================== 标准化查询契约 (通用清晰无 Table 前缀) ====================

    /** 动态分页查询 (PageQueryReq -> DataPage<DataBlock>) */
    DataPage<DataBlock> queryPage(PageQueryReq req);

    /** 动态数据集数量统计 (QueryReq -> Long) */
    Long count(QueryReq req);

    /** 单据详情精准查询 (DetailQueryReq -> DataBlock) */
    DataBlock getDetail(DetailQueryReq req);

    /** 字段搜索下拉候选项查询 (OptionReq -> List<DataOptionResp>) */
    List<DataOptionResp> getOptions(OptionReq req);

    /** 单据全量物理模块树精准查询快捷方法 (抓取指定模块及其伴生表、所有子孙表全部字段) */
    default DataBlock getFullDetail(Long moduleId, Long id) {
        return getDetail(DetailQueryReq.builder().moduleId(moduleId).id(id).build());
    }

    // ==================== 写契约 (100% 读写同构强约束) ====================

    /**
     * 自相似主子表同构原子保存/更新
     *
     * @param block 待持久化的业务数据区块 (结构与 getDetail 返回值 100% 同源)
     * @return 包含新生成主键与最新数据树的同构响应
     */
    DataSaveResp save(DataBlock block);

    /**
     * 基于基准单据派生/融合生成新版本单据
     *
     * @param req 版本派生请求 (包含基准ID与局部增量数据)
     * @return 新生成的单据根物理 ID
     */
    Long forkNewVersion(ForkVersionReq req);
}
