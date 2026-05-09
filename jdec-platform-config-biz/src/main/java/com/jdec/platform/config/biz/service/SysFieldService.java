package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.biz.entity.SysField;
import com.jdec.platform.config.biz.mapper.SysFieldMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 系统字段配置服务
 *
 * <p>提供字段配置的查询和管理功能。所有方法都使用 CONFIG_CENTER 数据源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysFieldService {

    private final SysFieldMapper sysFieldMapper;

    /**
     * 查询字段配置列表
     *
     * <p>使用 CONFIG_CENTER 数据源查询字段配置。
     *
     * @param tenantId 租户ID
     * @param tableName 表名
     * @return 字段配置列表
     */
    @DataSource(DataSourceConstants.CONFIG_CENTER)
    public List<SysField> querySysFields(String tenantId, String tableName) {
        log.debug("查询字段配置: tenantId={}, tableName={}", tenantId, tableName);

        return sysFieldMapper.selectList(
                new LambdaQueryWrapper<SysField>()
                        .eq(SysField::getTenantId, tenantId)
                        .eq(SysField::getTableName, tableName)
                        .eq(SysField::getDeleted, 0));
    }
}
