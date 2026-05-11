package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysMenuApi;
import com.jdec.platform.config.api.dto.request.SysMenuDragReq;
import com.jdec.platform.config.api.dto.request.SysMenuSaveReq;
import com.jdec.platform.config.api.dto.response.SysMenuTreeResp;
import com.jdec.platform.config.biz.entity.SysMenu;
import com.jdec.platform.config.biz.mapper.SysMenuMapper;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;

/** 系统菜单 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysMenuService implements SysMenuApi {

    private final SysMenuMapper sysMenuMapper;

    /** 菜单类型：导航 */
    private static final Integer MENU_TYPE_NAV = 1;

    /** 顶层父级ID */
    private static final Long ROOT_PID = 0L;

    @Override
    public List<SysMenuTreeResp> tree(Integer category) {
        String projectNo = AppContext.getProjectNo();
        List<SysMenu> list =
                sysMenuMapper.selectList(
                        new LambdaQueryWrapper<SysMenu>()
                                .eq(SysMenu::getCategory, category)
                                .eq(SysMenu::getSubjectId, AppContext.getSubjectId())
                                .eq(SysMenu::getProjectNo, projectNo)
                                .orderByAsc(SysMenu::getSortOrder)
                                .orderByAsc(SysMenu::getId));
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        // 构建映射
        Map<Long, SysMenuTreeResp> nodeMap = new LinkedHashMap<>();
        for (SysMenu menu : list) {
            SysMenuTreeResp resp = new SysMenuTreeResp();
            BeanUtils.copyProperties(menu, resp);
            resp.setChildren(new ArrayList<>());
            nodeMap.put(menu.getId(), resp);
        }
        // 组装树
        List<SysMenuTreeResp> tree = new ArrayList<>();
        for (SysMenu menu : list) {
            SysMenuTreeResp node = nodeMap.get(menu.getId());
            Long pid = menu.getPid();
            if (pid == null || pid.equals(ROOT_PID)) {
                tree.add(node);
            } else {
                SysMenuTreeResp parent = nodeMap.get(pid);
                if (parent != null && parent.getChildren() != null) {
                    parent.getChildren().add(node);
                } else {
                    tree.add(node);
                }
            }
        }
        return tree;
    }

    @Override
    public void saveMenu(SysMenuSaveReq req) {
        Long pid = req.getPid() == null ? ROOT_PID : req.getPid();
        req.setPid(pid);

        // 校验：一级菜单类型必须为导航
        if (pid.equals(ROOT_PID) && !MENU_TYPE_NAV.equals(req.getMenuType())) {
            throw new BusinessException("一级菜单类型必须为导航");
        }

        if (req.getId() == null) {
            // 新增
            SysMenu entity = new SysMenu();
            BeanUtils.copyProperties(req, entity);
            entity.setProjectNo(AppContext.getProjectNo());
            // 校验同级名称是否重复（按父级、分类、主体、应用隔离）
            checkDuplicateTitle(
                    pid, req.getCategory(), req.getTitle(), AppContext.getSubjectId(), null);
            sysMenuMapper.insert(entity);
        } else {
            // 编辑
            boolean exists =
                    sysMenuMapper.exists(
                            Wrappers.<SysMenu>lambdaQuery()
                                    .eq(SysMenu::getId, req.getId())
                                    .eq(SysMenu::getSubjectId, AppContext.getSubjectId())
                                    .eq(SysMenu::getProjectNo, AppContext.getProjectNo()));
            if (!exists) {
                throw new BusinessException("菜单不存在");
            }
            // 不能把自己设为自己的父节点
            if (req.getId().equals(pid)) {
                throw new BusinessException("不能将自己设为父菜单");
            }
            // 校验是否形成了环（不能把自己子节点设为父节点）
            if (!pid.equals(ROOT_PID)) {
                checkCycle(req.getId(), pid, AppContext.getSubjectId());
            }
            // 校验同级名称是否重复
            checkDuplicateTitle(
                    pid, req.getCategory(), req.getTitle(), AppContext.getSubjectId(), req.getId());

            SysMenu entity = new SysMenu();
            BeanUtils.copyProperties(req, entity);
            sysMenuMapper.updateById(entity);
        }
    }

    @Transactional
    @Override
    public void deleteMenu(Long id) {
        String projectNo = AppContext.getProjectNo();
        boolean exists =
                sysMenuMapper.exists(
                        Wrappers.<SysMenu>lambdaQuery()
                                .eq(SysMenu::getId, id)
                                .eq(SysMenu::getSubjectId, AppContext.getSubjectId())
                                .eq(SysMenu::getProjectNo, projectNo));
        if (!exists) {
            throw new BusinessException("菜单不存在");
        }
        // 级联删除所有子节点（同主体、同应用下）
        Set<Long> ids = collectChildrenIds(id, AppContext.getSubjectId(), projectNo);
        ids.add(id);
        sysMenuMapper.deleteByIds(ids);
    }

    @Override
    public void dragMenu(SysMenuDragReq req) {
        Long id = req.getId();
        Long targetPid = req.getTargetPid() == null ? ROOT_PID : req.getTargetPid();
        String projectNo = AppContext.getProjectNo();
        if (id == null) {
            throw new BusinessException("节点ID不能为空");
        }
        SysMenu node =
                sysMenuMapper.selectOne(
                        Wrappers.<SysMenu>lambdaQuery()
                                .eq(SysMenu::getId, id)
                                .eq(SysMenu::getSubjectId, AppContext.getSubjectId())
                                .eq(SysMenu::getProjectNo, projectNo));
        if (node == null) {
            throw new BusinessException("菜单不存在");
        }
        // 不能拖到自己下面
        if (id.equals(targetPid)) {
            throw new BusinessException("不能将自己设为自己的父节点");
        }
        // 不能拖到自己的子节点下面（形成环）
        if (!targetPid.equals(ROOT_PID)) {
            checkCycle(id, targetPid, AppContext.getSubjectId());
            boolean exists =
                    sysMenuMapper.exists(
                            Wrappers.<SysMenu>lambdaQuery()
                                    .eq(SysMenu::getId, targetPid)
                                    .eq(SysMenu::getSubjectId, AppContext.getSubjectId())
                                    .eq(SysMenu::getProjectNo, projectNo));
            if (!exists) {
                throw new BusinessException("目标父菜单不存在");
            }
        }
        // 校验：拖到顶层时必须是导航类型
        if (targetPid.equals(ROOT_PID) && !MENU_TYPE_NAV.equals(node.getMenuType())) {
            throw new BusinessException("一级菜单类型必须为导航");
        }
        // 校验同级名称是否重复
        checkDuplicateTitle(
                targetPid, node.getCategory(), node.getTitle(), AppContext.getSubjectId(), id);

        node.setPid(targetPid);
        sysMenuMapper.updateById(node);
    }

    /** 校验同级菜单名称是否重复 */
    private void checkDuplicateTitle(
            Long pid, Integer category, String title, Long subjectId, Long excludeId) {
        String projectNo = AppContext.getProjectNo();
        LambdaQueryWrapper<SysMenu> wrapper =
                new LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getPid, pid)
                        .eq(SysMenu::getCategory, category)
                        .eq(SysMenu::getTitle, title)
                        .eq(SysMenu::getSubjectId, subjectId)
                        .eq(SysMenu::getProjectNo, projectNo);
        if (excludeId != null) {
            wrapper.ne(SysMenu::getId, excludeId);
        }
        long count = sysMenuMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException("同级菜单名称已存在");
        }
    }

    /** 收集所有子节点ID（限定同主体、同应用） */
    private Set<Long> collectChildrenIds(Long parentId, Long subjectId, String projectNo) {
        Set<Long> result = new HashSet<>();
        List<SysMenu> children =
                sysMenuMapper.selectList(
                        Wrappers.<SysMenu>lambdaQuery()
                                .eq(SysMenu::getPid, parentId)
                                .eq(SysMenu::getSubjectId, subjectId)
                                .eq(SysMenu::getProjectNo, projectNo));
        for (SysMenu child : children) {
            result.add(child.getId());
            result.addAll(collectChildrenIds(child.getId(), subjectId, projectNo));
        }
        return result;
    }

    /** 检查是否形成环：targetPid 不能是 id 的子节点（包括自身） */
    private void checkCycle(Long id, Long targetPid, Long subjectId) {
        if (id.equals(targetPid)) {
            throw new BusinessException("不能将节点移动到自身或其子菜单下");
        }
        List<SysMenu> children =
                sysMenuMapper.selectList(
                        Wrappers.<SysMenu>lambdaQuery()
                                .eq(SysMenu::getPid, id)
                                .eq(SysMenu::getSubjectId, subjectId)
                                .eq(SysMenu::getProjectNo, AppContext.getProjectNo()));
        for (SysMenu child : children) {
            checkCycle(child.getId(), targetPid, subjectId);
        }
    }
}
