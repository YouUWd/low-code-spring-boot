package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.SysMenuDragReq;
import com.jdec.platform.config.api.dto.request.SysMenuSaveReq;
import com.jdec.platform.config.api.dto.response.SysMenuResp;
import com.jdec.platform.config.api.dto.response.SysMenuTreeResp;
import java.util.List;

public interface SysMenuApi {

    /**
     * 查询菜单树
     *
     * @param category 菜单分类.1:业务菜单 2:系统菜单（可选，为null时返回所有菜单）
     */
    List<SysMenuTreeResp> tree(Integer category);

    /**
     * 查询菜单列表（平铺）
     *
     * @param category 菜单分类.1:业务菜单 2:系统菜单（可选，为null时返回所有菜单）
     */
    List<SysMenuResp> list(Integer category);

    /**
     * 根据Id查询菜单
     *
     * @param Id 菜单ID
     */
    SysMenuResp getMenuById(Long Id);

    /**
     * 根据参数查询菜单详情
     *
     * @param param 菜单参数
     */
    SysMenuResp getMenuByParam(String param);

    /**
     * 新增/编辑菜单
     *
     * @return 保存后的菜单信息
     */
    SysMenuResp saveMenu(SysMenuSaveReq req);

    /**
     * 删除菜单（级联删除子节点及角色关联）
     *
     * @param id 菜单ID
     * @param category 菜单分类（如：业务菜单/系统菜单），用于审计日志的子模块标识
     */
    void deleteMenu(Long id, String category);

    /** 拖拽菜单 */
    void dragMenu(SysMenuDragReq req);
}
