package com.jdec.platform.config.biz.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jdec.platform.config.api.SysButtonApi;
import com.jdec.platform.config.api.SysConfigCategoryApi;
import com.jdec.platform.config.api.dto.request.SysButtonMenuQueryReq;
import com.jdec.platform.config.api.dto.request.SysButtonPageReq;
import com.jdec.platform.config.api.dto.request.SysButtonSaveReq;
import com.jdec.platform.config.api.dto.response.SysButtonMenuGroupResp;
import com.jdec.platform.config.api.dto.response.SysButtonResp;
import com.jdec.platform.config.api.dto.response.SysButtonSimpleResp;
import com.jdec.platform.config.api.dto.response.SysConfigItemResp;
import com.jdec.platform.config.biz.entity.SysButton;
import com.jdec.platform.config.biz.entity.SysMenu;
import com.jdec.platform.config.biz.mapper.SysButtonMapper;
import com.jdec.platform.config.biz.mapper.SysMenuMapper;
import com.jdec.platform.config.biz.reference.ReferenceCheckService;
import com.jdec.platform.config.biz.reference.ReferenceViolation;
import com.jdec.platform.config.open.api.OpenButtonApi;
import com.jdec.platform.shared.audit.annotation.DataAudit;
import com.jdec.platform.shared.audit.enums.OperationType;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.exception.PopException;
import com.jdec.platform.shared.model.PageResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 按钮配置 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysButtonService implements SysButtonApi, OpenButtonApi {

    private final SysButtonMapper sysButtonMapper;
    private final ReferenceCheckService referenceCheckService;
    private final SysConfigCategoryApi sysConfigCategoryApi;
    private final SysMenuMapper sysMenuMapper;

    @Override
    public PageResult<SysButtonResp> page(SysButtonPageReq req) {
        LambdaQueryWrapper<SysButton> wrapper =
                Wrappers.<SysButton>lambdaQuery()
                        .eq(SysButton::getProjectNo, AppContext.getProjectNo())
                        .eq(SysButton::getSubjectId, AppContext.getSubjectId())
                        .like(
                                StringUtils.hasText(req.getTitle()),
                                SysButton::getTitle,
                                req.getTitle())
                        .eq(StringUtils.hasText(req.getType()), SysButton::getType, req.getType())
                        .eq(req.getEnabled() != null, SysButton::getEnabled, req.getEnabled())
                        .orderByAsc(SysButton::getId);
        Page<SysButton> pageParam = new Page<>(req.getPageNum(), req.getPageSize());
        IPage<SysButton> pageResult = sysButtonMapper.selectPage(pageParam, wrapper);
        List<SysButton> records = pageResult.getRecords();
        Map<Long, String> menuTitleMap =
                loadMenuTitleMap(
                        records.stream()
                                .flatMap(e -> toMenuIds(e.getMenuIds()).stream())
                                .distinct()
                                .collect(Collectors.toList()));
        Map<String, String> typeLabelMap = loadButtonTypeLabelMap();
        List<SysButtonResp> respList =
                records.stream()
                        .map(e -> toResp(e, menuTitleMap, typeLabelMap))
                        .collect(Collectors.toList());
        return PageResult.of(req.getPageNum(), req.getPageSize(), pageResult.getTotal(), respList);
    }

    @Override
    public List<SysButtonResp> list() {
        List<SysButton> list =
                sysButtonMapper.selectList(
                        Wrappers.<SysButton>lambdaQuery()
                                .eq(SysButton::getProjectNo, AppContext.getProjectNo())
                                .eq(SysButton::getSubjectId, AppContext.getSubjectId())
                                .orderByAsc(SysButton::getId));
        Map<Long, String> menuTitleMap =
                loadMenuTitleMap(
                        list.stream()
                                .flatMap(e -> toMenuIds(e.getMenuIds()).stream())
                                .distinct()
                                .collect(Collectors.toList()));
        Map<String, String> typeLabelMap = loadButtonTypeLabelMap();
        return list.stream()
                .map(e -> toResp(e, menuTitleMap, typeLabelMap))
                .collect(Collectors.toList());
    }

    @DataAudit(
            module = "系统设置", // 业务模块
            subModule = "按钮配置", // 业务子模块
            operation = OperationType.UPDATE, // 操作类型
            tableName = "sys_button", // 表名
            dataIdField = "#req.id" // 数据ID的SpEL表达式，从request.id获取
            )
    @Override
    public void saveButton(SysButtonSaveReq req) {
        // 保存前先查询旧数据（用于审计）
        SysButton oldEntity = null;
        if (req.getId() != null) {
            oldEntity =
                    sysButtonMapper.selectOne(
                            Wrappers.<SysButton>lambdaQuery()
                                    .eq(SysButton::getId, req.getId())
                                    .eq(SysButton::getSubjectId, AppContext.getSubjectId())
                                    .eq(SysButton::getProjectNo, AppContext.getProjectNo()));
            if (oldEntity == null) {
                throw new BusinessException("按钮不存在");
            }
        }

        if (req.getId() == null) {
            SysButton entity = new SysButton();
            BeanUtils.copyProperties(req, entity);
            entity.setMenuIds(toJson(req.getMenuIds()));
            entity.setSubjectId(AppContext.getSubjectId());
            entity.setProjectNo(AppContext.getProjectNo());
            sysButtonMapper.insert(entity);
            // 设置id到请求对象，用于审计切面重新解析dataId
            req.setId(entity.getId());
        } else {
            SysButton entity = new SysButton();
            BeanUtils.copyProperties(req, entity);
            entity.setMenuIds(toJson(req.getMenuIds()));
            entity.setId(req.getId());
            sysButtonMapper.updateById(entity);
        }
    }

    @DataAudit(
            module = "系统设置",
            subModule = "按钮配置",
            operation = OperationType.DELETE,
            deleteDisplayField = "title",
            entityClass = SysButton.class,
            tableName = "sys_button",
            dataIdField = "#id")
    @Transactional
    @Override
    public void deleteButton(Long id) {
        SysButton button =
                sysButtonMapper.selectOne(
                        Wrappers.<SysButton>lambdaQuery()
                                .eq(SysButton::getId, id)
                                .eq(SysButton::getSubjectId, AppContext.getSubjectId())
                                .eq(SysButton::getProjectNo, AppContext.getProjectNo()));
        if (button == null) {
            throw new BusinessException("按钮不存在");
        }
        // 引用检查
        List<ReferenceViolation> violations =
                referenceCheckService.check(SysButton.class, List.of(button.getId()));
        if (!violations.isEmpty()) {
            throw new PopException(
                    referenceCheckService.buildMessage(button.getTitle(), violations));
        }
        sysButtonMapper.deleteById(id);
    }

    @Override
    public List<SysButtonResp> listButtons() {
        return list();
    }

    @Override
    public List<SysButtonMenuGroupResp> queryButtonsByMenu(List<SysButtonMenuQueryReq> req) {
        if (req == null || req.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysButton> all =
                sysButtonMapper.selectList(
                        Wrappers.<SysButton>lambdaQuery()
                                .eq(SysButton::getProjectNo, AppContext.getProjectNo())
                                .eq(SysButton::getSubjectId, AppContext.getSubjectId())
                                .eq(SysButton::getEnabled, 1)
                                .orderByAsc(SysButton::getId));

        // 菜单ID -> (该菜单下的按钮列表 + 已加入的按钮ID集合，用于去重)
        Map<Long, List<SysButtonSimpleResp>> groupMap = new LinkedHashMap<>();
        Map<Long, Set<Long>> groupSeen = new HashMap<>();

        for (SysButtonMenuQueryReq item : req) {
            Long menuId = parseMenuId(item.getMenuId());
            List<String> types = item.getType();
            boolean typeEmpty = (types == null || types.isEmpty());
            for (SysButton button : all) {
                List<Long> btnMenuIds = toMenuIds(button.getMenuIds());
                if (menuId != null) {
                    // 已绑定该菜单的按钮收录；未传类型时，未绑定任何菜单的全局按钮也归入该菜单
                    if (!btnMenuIds.contains(menuId) && !(typeEmpty && btnMenuIds.isEmpty())) {
                        continue;
                    }
                }
                if (!typeEmpty && !types.contains(button.getType())) {
                    continue;
                }
                // 指定了菜单则归入该菜单；未指定菜单则归入按钮自身关联的全部菜单
                List<Long> targetMenus = (menuId != null) ? List.of(menuId) : btnMenuIds;
                if (targetMenus.isEmpty()) {
                    continue;
                }
                SysButtonSimpleResp resp = toSimpleResp(button);
                for (Long tm : targetMenus) {
                    Set<Long> seen = groupSeen.computeIfAbsent(tm, k -> new HashSet<>());
                    if (!seen.add(button.getId())) {
                        continue;
                    }
                    groupMap.computeIfAbsent(tm, k -> new ArrayList<>()).add(resp);
                }
            }
        }

        List<SysButtonMenuGroupResp> result = new ArrayList<>();
        for (Map.Entry<Long, List<SysButtonSimpleResp>> entry : groupMap.entrySet()) {
            SysButtonMenuGroupResp group = new SysButtonMenuGroupResp();
            group.setMenuId(entry.getKey());
            group.setButtons(entry.getValue());
            result.add(group);
        }
        return result;
    }

    /** 单条转换：仅保留前端需要的字段（名称、类型、颜色相关），并排除未启用的按钮 */
    private SysButtonSimpleResp toSimpleResp(SysButton entity) {
        SysButtonSimpleResp resp = new SysButtonSimpleResp();
        resp.setTitle(entity.getTitle());
        resp.setType(entity.getType());
        resp.setDefaultBgColor(entity.getDefaultBgColor());
        resp.setDefaultFontColor(entity.getDefaultFontColor());
        resp.setDefaultBorder(entity.getDefaultBorder());
        resp.setLevitateBgColor(entity.getLevitateBgColor());
        resp.setLevitateFontColor(entity.getLevitateFontColor());
        resp.setLevitateBorderColor(entity.getLevitateBorderColor());
        resp.setSelectedBgColor(entity.getSelectedBgColor());
        resp.setSelectedFontColor(entity.getSelectedFontColor());
        resp.setSelectedBorderColor(entity.getSelectedBorderColor());
        return resp;
    }

    /** 解析菜单ID：空字符串或非数字返回 null（表示不限定菜单） */
    private Long parseMenuId(String menuId) {
        if (!StringUtils.hasText(menuId)) {
            return null;
        }
        try {
            return Long.valueOf(menuId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public SysButtonResp getButtonById(Long id) {
        SysButton entity = sysButtonMapper.selectById(id);
        if (Objects.isNull(entity)) {
            return null;
        }
        return toResp(entity);
    }

    @Override
    public SysButtonResp getButtonByType(String btnType) {
        LambdaQueryWrapper<SysButton> wrapper =
                Wrappers.<SysButton>lambdaQuery()
                        .eq(SysButton::getProjectNo, AppContext.getProjectNo())
                        .eq(SysButton::getSubjectId, AppContext.getSubjectId())
                        .eq(SysButton::getType, btnType)
                        .last("LIMIT 1");
        SysButton entity = sysButtonMapper.selectOne(wrapper);
        if (Objects.isNull(entity)) {
            return null;
        }
        return toResp(entity);
    }

    /** 单条转换：按需加载菜单名称与类型中文标签映射 */
    private SysButtonResp toResp(SysButton entity) {
        Map<Long, String> menuTitleMap = loadMenuTitleMap(toMenuIds(entity.getMenuIds()));
        Map<String, String> typeLabelMap = loadButtonTypeLabelMap();
        return toResp(entity, menuTitleMap, typeLabelMap);
    }

    /** 批量转换：复用已加载的菜单名称映射与类型中文标签映射 */
    private SysButtonResp toResp(
            SysButton entity, Map<Long, String> menuTitleMap, Map<String, String> typeLabelMap) {
        SysButtonResp resp = new SysButtonResp();
        BeanUtils.copyProperties(entity, resp);
        List<Long> menuIds = toMenuIds(entity.getMenuIds());
        resp.setMenuIds(menuIds);
        if (!menuIds.isEmpty()) {
            String menuNames =
                    menuIds.stream()
                            .map(menuTitleMap::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining(","));
            resp.setMenuNames(menuNames);
        }
        String typeLabel = typeLabelMap.get(entity.getType());
        resp.setTypeLabel(typeLabel != null ? typeLabel : entity.getType());
        return resp;
    }

    /** 批量加载菜单ID对应的菜单名称映射 */
    private Map<Long, String> loadMenuTitleMap(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysMenu> menus = sysMenuMapper.selectBatchIds(ids);
        if (menus == null || menus.isEmpty()) {
            return Collections.emptyMap();
        }
        return menus.stream()
                .collect(Collectors.toMap(SysMenu::getId, SysMenu::getTitle, (a, b) -> a));
    }

    /** 加载 ButtonStyle 通用配置，构建 按钮type值 -> 中文标签 的映射 */
    private Map<String, String> loadButtonTypeLabelMap() {
        List<SysConfigItemResp> items =
                sysConfigCategoryApi.getConfigItemsByCategoryAlias("ButtonStyle");
        if (items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }
        return items.stream()
                .filter(i -> i.getValue() != null)
                .collect(
                        Collectors.toMap(
                                SysConfigItemResp::getValue,
                                SysConfigItemResp::getLabel,
                                (a, b) -> a));
    }

    /** 将数据库中 JSON 字符串形式的 menuIds 解析为菜单ID列表 */
    private List<Long> toMenuIds(String menuIds) {
        if (!StringUtils.hasText(menuIds)) {
            return new ArrayList<>();
        }
        return JSON.parseArray(menuIds, Long.class);
    }

    /** 将前端传入的菜单ID列表转为 JSON 字符串存入数据库 */
    private String toJson(List<Long> menuIds) {
        if (menuIds == null) {
            return null;
        }
        return JSON.toJSONString(menuIds);
    }
}
