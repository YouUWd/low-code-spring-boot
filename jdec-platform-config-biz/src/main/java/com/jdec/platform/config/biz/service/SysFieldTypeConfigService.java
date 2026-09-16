package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.biz.entity.SysFieldTypeConfig;
import com.jdec.platform.config.biz.mapper.SysFieldTypeConfigMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 字段类型业务配置 Service */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysFieldTypeConfigService {

    private final SysFieldTypeConfigMapper sysFieldTypeConfigMapper;

    /** 查询字段类型业务配置列表 */
    public List<SysFieldTypeConfig> list() {
        return sysFieldTypeConfigMapper.selectList(
                Wrappers.<SysFieldTypeConfig>lambdaQuery().orderByAsc(SysFieldTypeConfig::getId));
    }

    /** 根据ID查询字段类型业务配置 */
    public SysFieldTypeConfig getById(Long id) {
        return sysFieldTypeConfigMapper.selectById(id);
    }

    /** 新增/编辑字段类型业务配置，id 为空时新增，返回主键ID */
    public Long save(SysFieldTypeConfig entity) {
        if (entity.getId() == null) {
            // TODO 业务逻辑：校验 dataType 唯一性
            sysFieldTypeConfigMapper.insert(entity);
        } else {
            // TODO 业务逻辑：校验记录存在、dataType 唯一性
            sysFieldTypeConfigMapper.updateById(entity);
        }
        return entity.getId();
    }

    /** 删除字段类型业务配置 */
    public void delete(Long id) {
        // TODO 业务逻辑：校验是否已被 sys_field_search_config 引用
        sysFieldTypeConfigMapper.deleteById(id);
    }
}
