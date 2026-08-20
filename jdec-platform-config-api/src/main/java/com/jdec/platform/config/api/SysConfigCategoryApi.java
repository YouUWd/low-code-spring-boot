package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.SysConfigCategorySaveReq;
import com.jdec.platform.config.api.dto.request.SysConfigItemDragReq;
import com.jdec.platform.config.api.dto.request.SysConfigItemSaveReq;
import com.jdec.platform.config.api.dto.response.*;
import java.util.List;

public interface SysConfigCategoryApi {

    /** 查询配置分类列表 */
    List<SysConfigCategoryResp> list();

    /** 查询混合树（Category + Item） */
    List<SysConfigTreeNodeResp> mixedTree();

    /** 查询所有通用配置（按分类分组） */
    List<AllConfigResp> getAllConfigs();

    /**
     * 根据categoryAlias查询配置分类
     *
     * @param categoryAlias 分类英文标识
     */
    SysConfigCategoryResp getByCategoryAlias(String categoryAlias);

    /** 新增/编辑配置分类，新增时返回新记录ID */
    Long saveCategory(SysConfigCategorySaveReq req);

    /**
     * 删除配置分类
     *
     * @param id 分类ID
     * @param force 是否强制删除。false 时若有配置项则抛出提示；true 时级联删除
     */
    void deleteCategory(Long id, boolean force);

    // ==================== 配置项接口 ====================

    /**
     * 查询配置项（按分类ID）
     *
     * <p>根据分类的 format 字段自动判定返回树形或列表结构
     *
     * @param categoryId 分类ID
     */
    List<SysConfigItemTreeResp> getItemsByCategory(Long categoryId);

    /**
     * 查询配置项（按分类alias）
     *
     * <p>根据分类的 format 字段自动判定返回树形或列表结构
     *
     * @param categoryAlias 分类英文标识
     */
    List<SysConfigItemTreeResp> getItemsByCategoryAlias(String categoryAlias);

    /** 新增/编辑配置项，新增时返回新记录ID */
    Long saveItem(SysConfigItemSaveReq req);

    /**
     * 删除配置项
     *
     * @param id 配置项ID
     * @param force 是否强制删除。false 时若有子节点则抛出提示；true 时级联删除
     */
    void deleteItem(Long id, boolean force);

    /**
     * 拖拽配置项
     *
     * <p>支持将配置项拖拽到其它层级。限制：item 不能拖到 category 层级，只能在 item 之间拖拽
     *
     * @param req 拖拽请求
     */
    void dragItem(SysConfigItemDragReq req);

    /**
     * 查找指定分类下的配置项
     *
     * @param categoryAlias 分类英文标识
     * @param itemValue 配置项value
     */
    SysConfigItemResp getConfigItemsByValue(String categoryAlias, String itemValue);

    /**
     * 查找指定分类下的配置项
     *
     * @param id 配置项ID
     */
    SysConfigItemResp getConfigItemsById(Long id);
}
