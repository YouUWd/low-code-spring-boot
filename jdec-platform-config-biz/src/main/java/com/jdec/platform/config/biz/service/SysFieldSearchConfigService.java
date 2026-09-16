package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.biz.entity.SysFieldSearchConfig;
import com.jdec.platform.config.biz.mapper.SysFieldSearchConfigMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 字段搜索条件配置 Service */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysFieldSearchConfigService {

    private final SysFieldSearchConfigMapper sysFieldSearchConfigMapper;

    /** 查询字段搜索条件配置列表 */
    public List<SysFieldSearchConfig> list() {
        return sysFieldSearchConfigMapper.selectList(
                Wrappers.<SysFieldSearchConfig>lambdaQuery()
                        .orderByAsc(SysFieldSearchConfig::getFieldTypeId)
                        .orderByAsc(SysFieldSearchConfig::getId));
    }

    /** 按字段类型配置ID查询搜索条件列表 */
    public List<SysFieldSearchConfig> listByFieldTypeId(Long fieldTypeId) {
        return sysFieldSearchConfigMapper.selectList(
                Wrappers.<SysFieldSearchConfig>lambdaQuery()
                        .eq(SysFieldSearchConfig::getFieldTypeId, fieldTypeId)
                        .orderByAsc(SysFieldSearchConfig::getId));
    }

    /** 根据ID查询字段搜索条件配置 */
    public SysFieldSearchConfig getById(Long id) {
        return sysFieldSearchConfigMapper.selectById(id);
    }

    /** 新增/编辑字段搜索条件配置，id 为空时新增，返回主键ID */
    public Long save(SysFieldSearchConfig entity) {
        if (entity.getId() == null) {
            // TODO 业务逻辑：校验 fieldTypeId 存在、同类型下 code 唯一
            sysFieldSearchConfigMapper.insert(entity);
        } else {
            // TODO 业务逻辑：校验记录存在、同类型下 code 唯一
            sysFieldSearchConfigMapper.updateById(entity);
        }
        return entity.getId();
    }

    /** 删除字段搜索条件配置 */
    public void delete(Long id) {
        sysFieldSearchConfigMapper.deleteById(id);
    }
}
