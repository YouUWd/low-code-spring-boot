package com.jdec.platform.hr.biz.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.hr.api.DepartmentApi;
import com.jdec.platform.hr.api.bo.DepartmentBO;
import com.jdec.platform.hr.biz.entity.Department;
import com.jdec.platform.hr.biz.mapper.DepartmentMapper;
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
public class DepartmentService implements DepartmentApi {
    private final DepartmentMapper departmentMapper;

    @Override
    public List<DepartmentBO> getDepartmentList() {
        List<Department> allDepts =
                departmentMapper.selectList(
                        new LambdaQueryWrapper<Department>()
                                .select(
                                        Department::getId,
                                        Department::getPid,
                                        Department::getName,
                                        Department::getManagerId,
                                        Department::getManagerName));
        return BeanUtil.copyToList(allDepts, DepartmentBO.class);
    }
}
