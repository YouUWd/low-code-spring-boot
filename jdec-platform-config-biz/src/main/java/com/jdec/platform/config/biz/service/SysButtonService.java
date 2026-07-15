package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jdec.platform.config.api.SysButtonApi;
import com.jdec.platform.config.api.dto.request.SysButtonPageReq;
import com.jdec.platform.config.api.dto.request.SysButtonSaveReq;
import com.jdec.platform.config.api.dto.response.SysButtonResp;
import com.jdec.platform.config.biz.check.CheckConstant;
import com.jdec.platform.config.biz.check.ReferenceCheckManager;
import com.jdec.platform.config.biz.check.ReferenceContext;
import com.jdec.platform.config.biz.entity.SysButton;
import com.jdec.platform.config.biz.mapper.SysButtonMapper;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.exception.PopException;
import com.jdec.platform.shared.model.PageResult;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 按钮配置 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysButtonService implements SysButtonApi {

    private final SysButtonMapper sysButtonMapper;
    private final ReferenceCheckManager referenceCheckManager;

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
                        .like(
                                StringUtils.hasText(req.getAlias()),
                                SysButton::getAlias,
                                req.getAlias())
                        .eq(req.getEnabled() != null, SysButton::getEnabled, req.getEnabled())
                        .eq(req.getShowed() != null, SysButton::getShowed, req.getShowed())
                        .orderByAsc(SysButton::getSortOrder)
                        .orderByAsc(SysButton::getId);
        Page<SysButton> pageParam = new Page<>(req.getPageNum(), req.getPageSize());
        IPage<SysButton> pageResult = sysButtonMapper.selectPage(pageParam, wrapper);
        List<SysButtonResp> respList =
                pageResult.getRecords().stream()
                        .map(
                                entity -> {
                                    SysButtonResp resp = new SysButtonResp();
                                    BeanUtils.copyProperties(entity, resp);
                                    return resp;
                                })
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
                                .orderByAsc(SysButton::getSortOrder)
                                .orderByAsc(SysButton::getId));
        return list.stream()
                .map(
                        entity -> {
                            SysButtonResp resp = new SysButtonResp();
                            BeanUtils.copyProperties(entity, resp);
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public void saveButton(SysButtonSaveReq req) {
        if (req.getId() == null) {
            SysButton entity = new SysButton();
            BeanUtils.copyProperties(req, entity);
            entity.setSubjectId(AppContext.getSubjectId());
            entity.setProjectNo(AppContext.getProjectNo());
            long count =
                    sysButtonMapper.selectCount(
                            Wrappers.<SysButton>lambdaQuery()
                                    .eq(SysButton::getProjectNo, AppContext.getProjectNo())
                                    .eq(SysButton::getSubjectId, AppContext.getSubjectId())
                                    .eq(SysButton::getTitle, entity.getTitle()));
            if (count > 0) {
                throw new BusinessException("按钮名称已存在");
            }
            sysButtonMapper.insert(entity);
        } else {
            boolean exists =
                    sysButtonMapper.exists(
                            Wrappers.<SysButton>lambdaQuery()
                                    .eq(SysButton::getId, req.getId())
                                    .eq(SysButton::getSubjectId, AppContext.getSubjectId())
                                    .eq(SysButton::getProjectNo, AppContext.getProjectNo()));
            if (!exists) {
                throw new BusinessException("按钮不存在");
            }
            long count =
                    sysButtonMapper.selectCount(
                            Wrappers.<SysButton>lambdaQuery()
                                    .eq(SysButton::getProjectNo, AppContext.getProjectNo())
                                    .eq(SysButton::getSubjectId, AppContext.getSubjectId())
                                    .eq(SysButton::getTitle, req.getTitle())
                                    .ne(SysButton::getId, req.getId()));
            if (count > 0) {
                throw new BusinessException("按钮名称已存在");
            }
            SysButton entity = new SysButton();
            BeanUtils.copyProperties(req, entity);
            entity.setId(req.getId());
            sysButtonMapper.updateById(entity);
        }
    }

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
        ReferenceContext context =
                ReferenceContext.builder()
                        .targetType(CheckConstant.SYS_BUTTON)
                        .targetId(id)
                        .targetName(button.getTitle())
                        .build();
        List<String> check = referenceCheckManager.check(context);
        if (!check.isEmpty()) {
            throw new PopException(String.join("\n", check));
        }
        sysButtonMapper.deleteById(id);
    }

    @Override
    public SysButtonResp getButtonById(Long id) {
        SysButton entity = sysButtonMapper.selectById(id);
        if (Objects.isNull(entity)) {
            return null;
        }
        SysButtonResp resp = new SysButtonResp();
        BeanUtils.copyProperties(entity, resp);
        return resp;
    }
}
