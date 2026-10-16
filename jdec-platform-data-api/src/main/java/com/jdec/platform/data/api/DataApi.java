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

    /** 动态列表全量查询 (不分页，跳过 COUNT 统计，返回聚合根模块自相似数据区块) */
    DataBlock queryList(QueryReq req);

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

    // ==================== 单表轻量操作契约 (Single Table APIs) ====================

    /**
     * 单表列表轻量投影查询 (不依赖模块树，直接在目标单表上执行查询，跳过多表编译与复杂上下文)
     *
     * @param req 单表查询请求
     * @return 记录列表，每条记录为 Map<列名, 字段值>
     */
    List<java.util.Map<String, Object>> querySingleTableList(
            com.jdec.platform.data.api.dto.request.SingleTableQueryReq req);

    /**
     * 单表单列值投影查询 (直接提取满足条件的指定列数据，例如 List<Long> ids 或 List<String> businessNos)
     *
     * @param req 单表查询请求 (需在 req 中指定单一列名)
     * @param targetType 目标类型 Class
     * @return 单列值列表
     */
    <T> List<T> querySingleTableColumn(
            com.jdec.platform.data.api.dto.request.SingleTableQueryReq req, Class<T> targetType);

    /**
     * 单表主键 ID 列表轻量查询快捷方法 (相当于 querySingleTableColumn(req, Long.class))
     *
     * @param req 单表查询请求
     * @return 满足条件的 ID 列表
     */
    default List<Long> querySingleTableIds(
            com.jdec.platform.data.api.dto.request.SingleTableQueryReq req) {
        return querySingleTableColumn(req, Long.class);
    }

    /**
     * 单表条件计数 (SELECT COUNT(1) FROM tableName WHERE ...)
     *
     * @param req 单表查询请求 (仅使用 tableName 与 filters)
     * @return 满足条件的记录数
     */
    Long countSingleTable(com.jdec.platform.data.api.dto.request.SingleTableQueryReq req);

    /**
     * 批量查询单据最新启用的最大版本主键 ID 集合
     *
     * <p>底层在数据库端通过分组聚合与连接过滤，直接获取每个 business_no 在指定 business_status 下的最大版本记录主键 ID 列表。
     *
     * @param tableName 物理主表名
     * @param businessNos 业务单号集合
     * @param businessStatus 业务状态 (如 99-已启用)
     * @return 各单号最新启用的记录 ID 列表
     */
    List<Long> getLatestVersionIds(
            String tableName, java.util.Collection<String> businessNos, Integer businessStatus);

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
