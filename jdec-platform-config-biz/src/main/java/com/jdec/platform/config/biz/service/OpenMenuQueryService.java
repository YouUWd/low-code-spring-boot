package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.biz.entity.SysMenu;
import com.jdec.platform.config.biz.mapper.SysMenuMapper;
import com.jdec.platform.config.open.api.OpenMenuApi;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 菜单开放接口实现：菜单维度的只读查询。
 *
 * <p>本类只依赖 {@link SysMenuMapper}，与 {@link SysMenuService} 相比不涉及引用校验、审计日志等链路。 <b>单独成类的原因</b>：{@code
 * SysMenuService} 一侧的链路（引用校验 → 审批链 → 审计日志 → 监控日志）会反向依赖 {@code OpenMenuApi} 的消费方， 若由 {@code
 * SysMenuService} 实现本接口，会与其消费方（监控日志）构成构造循环；拆出后该环被打断。
 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class OpenMenuQueryService implements OpenMenuApi {

    /** 菜单类型：页面（仅该类型且关联详情菜单时，取详情菜单绑定的模块） */
    private static final Integer MENU_TYPE_PAGE = 2;

    private final SysMenuMapper sysMenuMapper;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Long getModuleIdByMenuId(Long menuId) {
        SysMenu menu = selectMenuById(menuId);
        if (menu == null) {
            return null;
        }
        // 页面类型且关联了详情菜单 → 取详情菜单绑定的模块；否则取当前菜单绑定的模块
        if (MENU_TYPE_PAGE.equals(menu.getMenuType()) && menu.getDetailId() != null) {
            SysMenu detailMenu = selectMenuById(menu.getDetailId());
            return detailMenu == null ? null : detailMenu.getModuleId();
        }
        return menu.getModuleId();
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Long getMenuIdByModuleId(Long moduleId) {
        if (moduleId == null) {
            return null;
        }
        // 优先在当前主体与项目范围内查找匹配该模块的有效菜单
        SysMenu menu =
                sysMenuMapper.selectOne(
                        Wrappers.<SysMenu>lambdaQuery()
                                .eq(SysMenu::getModuleId, moduleId)
                                .eq(SysMenu::getSubjectId, AppContext.getSubjectId())
                                .eq(SysMenu::getProjectNo, AppContext.getProjectNo())
                                .last("LIMIT 1"));
        return menu != null ? menu.getId() : null;
    }

    /** 取库中菜单记录（数据隔离：subject_id + project_no），id 为空或查不到返回 {@code null} */
    private SysMenu selectMenuById(Long id) {
        if (id == null) {
            return null;
        }
        return sysMenuMapper.selectOne(
                Wrappers.<SysMenu>lambdaQuery()
                        .eq(SysMenu::getId, id)
                        .eq(SysMenu::getSubjectId, AppContext.getSubjectId())
                        .eq(SysMenu::getProjectNo, AppContext.getProjectNo()));
    }
}
