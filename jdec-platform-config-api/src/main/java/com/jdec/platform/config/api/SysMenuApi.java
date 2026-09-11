package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.SaveMenuReq;
import com.jdec.platform.config.api.dto.request.SysMenuDragReq;
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
     * 新增/编辑菜单，返回保存后的菜单信息。
     *
     * <p>支持前端将整棵菜单树一并提交：当 request.treeNodes 非空时，保存当前菜单节点后按 treeNodes 差量同步整棵树的父子关系与同级排序； treeNodes
     * 中缺失的节点不做删除。排序由前端整棵树决定，后端不再搬移其它同级节点。
     */
    SysMenuResp saveMenu(SaveMenuReq request);

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
