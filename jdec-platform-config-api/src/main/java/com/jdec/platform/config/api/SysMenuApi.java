package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.SysMenuDragReq;
import com.jdec.platform.config.api.dto.request.SysMenuSaveReq;
import com.jdec.platform.config.api.dto.response.SysMenuTreeResp;
import java.util.List;

public interface SysMenuApi {

    /**
     * 查询菜单树
     *
     * @param category 菜单分类.1:业务菜单 2:系统菜单（可选，为null时返回所有菜单）
     */
    List<SysMenuTreeResp> tree(Integer category);

    /** 新增/编辑菜单 */
    void saveMenu(SysMenuSaveReq req);

    /**
     * 删除菜单（级联删除子节点及角色关联）
     *
     * @param id 菜单ID
     * @param force 是否强制删除。false 时若有子菜单或角色关联则抛出提示；true 时级联删除
     */
    void deleteMenu(Long id, boolean force);

    /** 拖拽菜单 */
    void dragMenu(SysMenuDragReq req);
}
