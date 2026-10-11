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

    // ==================== 读契约 (旧版基于 fieldId，计划废弃，由上层转换为物理表查询) ====================

    /**
     * 动态列表分页查询 (PageQueryReq -> DataPage<DataBlock>)
     *
     * @deprecated 将废弃，请上层转换为基于物理表/列的结构化查询 {@link
     *     #queryByTable(com.jdec.platform.data.api.dto.request.TablePageQueryReq)}
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    DataPage<DataBlock> query(PageQueryReq req);

    /**
     * 动态数据集数量统计
     *
     * @deprecated 将废弃，请上层转换为基于物理表/列的结构化查询 {@link
     *     #countByTable(com.jdec.platform.data.api.dto.request.TableQueryReq)}
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    Long count(QueryReq req);

    /**
     * 单据详情精准查询 (DetailQueryReq -> DataBlock 读写同源区块)
     *
     * @deprecated 将废弃，请上层转换为基于物理表/列的结构化查询 {@link
     *     #getDetailByTable(com.jdec.platform.data.api.dto.request.TableDetailQueryReq)}
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    DataBlock getDetail(DetailQueryReq req);

    /**
     * 单据详情精准查询快捷方法
     *
     * @deprecated 将废弃，请使用 {@link #getFullDetail(Long, Long)} 或 {@link #getDetailByTable}
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    default DataBlock getDetail(Long moduleId, Long id) {
        return getDetail(DetailQueryReq.builder().moduleId(moduleId).id(id).build());
    }

    /**
     * 单据详情精准查询快捷方法 (带投影字段)
     *
     * @deprecated 将废弃，请使用 {@link
     *     #getDetailByTable(com.jdec.platform.data.api.dto.request.TableDetailQueryReq)}
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    default DataBlock getDetail(Long moduleId, Long id, List<Long> fields) {
        return getDetail(DetailQueryReq.builder().moduleId(moduleId).id(id).fields(fields).build());
    }

    // ==================== 物理表/列结构化查询契约 (脱离上层 fieldId，推荐目标规范) ====================

    /** 物理表动态分页查询 (TablePageQueryReq -> DataPage<DataBlock>) */
    DataPage<DataBlock> queryByTable(com.jdec.platform.data.api.dto.request.TablePageQueryReq req);

    /** 物理表动态数据集数量统计 (TableQueryReq -> Long) */
    Long countByTable(com.jdec.platform.data.api.dto.request.TableQueryReq req);

    /** 物理表单据详情精准查询 (TableDetailQueryReq -> DataBlock) */
    DataBlock getDetailByTable(com.jdec.platform.data.api.dto.request.TableDetailQueryReq req);

    /** 单据全量物理模块树精准查询快捷方法 (抓取指定模块及其伴生表、所有子孙表全部字段) */
    default DataBlock getFullDetail(Long moduleId, Long id) {
        return getDetailByTable(
                com.jdec.platform.data.api.dto.request.TableDetailQueryReq.builder()
                        .moduleId(moduleId)
                        .id(id)
                        .build());
    }

    /** 物理表字段搜索下拉候选项查询 (TableOptionReq -> List<DataOption>) */
    List<DataOption> getOptionsByTable(com.jdec.platform.data.api.dto.request.TableOptionReq req);

    /**
     * 字段搜索下拉候选项查询
     *
     * @deprecated 将废弃，请上层转换为基于物理表/列的结构化查询 {@link
     *     #getOptionsByTable(com.jdec.platform.data.api.dto.request.TableOptionReq)}
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    List<DataOption> getOptions(OptionReq req);

    // ==================== 写契约 (100% 读写同构强约束) ====================

    /**
     * 自相似主子表同构原子保存/更新
     *
     * @param block 待持久化的业务数据区块 (结构与 getDetail 返回值 100% 同源)
     * @return 包含新生成主键与最新数据树的同构响应
     */
    SaveResp save(DataBlock block);

    /**
     * 基于基准单据派生/融合生成新版本单据
     *
     * @param req 版本派生请求 (包含基准ID与局部增量数据)
     * @return 新生成的单据根物理 ID
     */
    Long forkNewVersion(com.jdec.platform.data.api.dto.request.ForkVersionReq req);
}
