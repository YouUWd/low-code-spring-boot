package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.SysMenuDragReq;
import com.jdec.platform.config.api.dto.request.SysMenuSaveReq;
import com.jdec.platform.config.api.dto.response.SysMenuTreeResp;

import java.util.List;

public interface SysMenuApi {

    /**
     * 查询菜单树
     *
     * @param category  菜单分类.1:业务菜单 2:系统菜单
     */
    List<SysMenuTreeResp> tree(Integer category);

    /**
     * 新增/编辑菜单
     */
    void saveMenu(SysMenuSaveReq req);

    /**
     * 删除菜单（级联删除子节点）
     *
     * @param id        菜单ID
     */
    void deleteMenu(Long id);

    /**
     * 拖拽菜单
     */
    void dragMenu(SysMenuDragReq req);
}
