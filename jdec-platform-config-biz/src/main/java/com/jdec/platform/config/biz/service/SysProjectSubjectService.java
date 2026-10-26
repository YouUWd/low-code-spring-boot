package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysProjectSubjectApi;
import com.jdec.platform.config.api.dto.response.SysProjectSubjectResp;
import com.jdec.platform.config.biz.entity.SysProjectSubject;
import com.jdec.platform.config.biz.mapper.SysProjectSubjectMapper;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/** 项目主体关联 Service 实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysProjectSubjectService implements SysProjectSubjectApi {

    private final SysProjectSubjectMapper sysProjectSubjectMapper;

    @Override
    public List<SysProjectSubjectResp> list() {
        List<SysProjectSubject> list =
                sysProjectSubjectMapper.selectList(
                        Wrappers.<SysProjectSubject>lambdaQuery()
                                .eq(SysProjectSubject::getProjectNo, AppContext.getProjectNo())
                                .orderByAsc(SysProjectSubject::getId));
        return list.stream()
                .map(
                        entity -> {
                            SysProjectSubjectResp resp = new SysProjectSubjectResp();
                            BeanUtils.copyProperties(entity, resp);
                            return resp;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public SysProjectSubjectResp getById(Long id) {
        SysProjectSubject entity = sysProjectSubjectMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        SysProjectSubjectResp resp = new SysProjectSubjectResp();
        BeanUtils.copyProperties(entity, resp);
        return resp;
    }

    @Override
    public SysProjectSubjectResp getBySubjectIdAndProjectNo(Long subjectId, String projectNo) {
        SysProjectSubject entity =
                sysProjectSubjectMapper.selectOne(
                        Wrappers.<SysProjectSubject>lambdaQuery()
                                .eq(SysProjectSubject::getSubjectId, subjectId)
                                .eq(SysProjectSubject::getProjectNo, projectNo));
        if (entity == null) {
            return null;
        }
        SysProjectSubjectResp resp = new SysProjectSubjectResp();
        BeanUtils.copyProperties(entity, resp);
        return resp;
    }

    @Override
    public void deleteProjectSubject(Long id) {
        sysProjectSubjectMapper.deleteById(id);
    }
}
