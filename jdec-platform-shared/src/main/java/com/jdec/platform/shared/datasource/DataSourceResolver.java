package com.jdec.platform.shared.datasource;

import com.jdec.platform.shared.exception.BusinessException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 数据源解析器 负责根据 projectNo 解析对应的物理数据源 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceResolver {

    private final ProjectProperties projectProperties;
    private final DynamicRoutingDataSource dynamicRoutingDataSource;

    /**
     * 根据 projectNo 解析物理 DataSource。
     *
     * <p>解析链路：projectNo -> mapping -> 数据源名称 -> 物理 DataSource
     *
     * <p>降级策略：mapping 中未找到时，直接用 projectNo 作为数据源名称尝试
     *
     * @param projectNo 项目编号
     * @return 物理数据源
     * @throws BusinessException 如果数据源不存在
     */
    public DataSource resolve(String projectNo) {
        // 1. 通过映射获取数据源名称
        String dsName = projectProperties.getDatasourceMapping().getOrDefault(projectNo, projectNo);

        log.debug("解析数据源: projectNo={}, mappedDsName={}", projectNo, dsName);

        // 2. 获取物理 DataSource
        DataSource ds = dynamicRoutingDataSource.getDataSource(dsName);
        if (ds == null) {
            log.error("数据源不存在: {}, projectNo={}", dsName, projectNo);
            throw new BusinessException("系统配置错误：找不到对应的数据源 [" + projectNo + "]");
        }

        return ds;
    }
}
