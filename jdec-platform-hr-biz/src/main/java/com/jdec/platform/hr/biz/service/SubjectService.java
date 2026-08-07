package com.jdec.platform.hr.biz.service;

import cn.hutool.core.bean.BeanUtil;
import com.jdec.platform.hr.api.SubjectApi;
import com.jdec.platform.hr.api.bo.SubjectBO;
import com.jdec.platform.hr.biz.entity.Subject;
import com.jdec.platform.hr.biz.mapper.SubjectMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DataSource(DataSourceConstants.HR_MANAGE)
public class SubjectService implements SubjectApi {
    private final SubjectMapper subjectMapper;

    @Override
    public List<SubjectBO> getSubjectList() {
        List<Subject> subjects = subjectMapper.selectList(null);
        return BeanUtil.copyToList(subjects, SubjectBO.class);
    }
}
