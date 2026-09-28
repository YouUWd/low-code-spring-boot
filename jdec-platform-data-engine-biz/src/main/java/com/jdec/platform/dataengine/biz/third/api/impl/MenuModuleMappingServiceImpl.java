package com.jdec.platform.dataengine.biz.third.api.impl;

import com.jdec.platform.dataengine.biz.dto.MenuListResp;
import com.jdec.platform.dataengine.biz.third.api.module.MenuModuleMappingService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MenuModuleMappingServiceImpl implements MenuModuleMappingService {
    @Override
    public MenuListResp findTargetMenu(Long menuId) {
        return null;
    }

    @Override
    public List<MenuListResp> findTargetAndAllChildren(List<MenuListResp> menus, Long menuId) {
        return List.of();
    }

    @Override
    public List<MenuListResp> findTargetAndAllChildrenByMenuId(Long menuId) {
        return List.of();
    }
}
