package com.jdec.platform.dataengine.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.dataengine.biz.entity.ModuleDataFieldChangeContent;
import com.jdec.platform.dataengine.biz.mapper.ModuleDataFieldChangeContentMapper;
import com.jdec.platform.dataengine.biz.service.IModuleDataFieldChangeContentService;
import com.jdec.platform.shared.datasource.DataSource;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.BatchResult;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
@DataSource("company")
public class IModuleDataFieldChangeContentServiceImpl
        implements IModuleDataFieldChangeContentService {

    private final ModuleDataFieldChangeContentMapper moduleDataFieldChangeContentMapper;

    @Override
    public int batchInsert(List<ModuleDataFieldChangeContent> list) {
        List<BatchResult> results = moduleDataFieldChangeContentMapper.insert(list);
        return results.size();
    }

    @Override
    public int batchUpdate(List<ModuleDataFieldChangeContent> list) {
        List<BatchResult> results = moduleDataFieldChangeContentMapper.updateById(list);
        return results.size();
    }

    @Override
    public ModuleDataFieldChangeContent queryOne(
            LambdaQueryWrapper<ModuleDataFieldChangeContent> wrapper) {
        return moduleDataFieldChangeContentMapper.selectOne(wrapper);
    }

    @Override
    public List<ModuleDataFieldChangeContent> queryList(
            LambdaQueryWrapper<ModuleDataFieldChangeContent> wrapper) {
        return moduleDataFieldChangeContentMapper.selectList(wrapper);
    }
}
