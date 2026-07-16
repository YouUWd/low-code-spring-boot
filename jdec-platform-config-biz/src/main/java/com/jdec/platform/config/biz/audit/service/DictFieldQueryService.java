package com.jdec.platform.config.biz.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.biz.entity.SysConfigItem;
import com.jdec.platform.config.biz.mapper.SysConfigItemMapper;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 字典字段查询服务
 *
 * <p>用于审计日志中字典值转中文描述
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class DictFieldQueryService {

    private final SysConfigItemMapper configItemMapper;

    /**
     * 根据字典分类别名和值列表获取中文描述
     *
     * @param categoryAlias 字典分类别名（对应 sys_config_category.category_alias）
     * @param values 字典值列表（对应 sys_config_item.value）
     * @return 字典值 -> 中文描述（label）的映射
     */
    public Map<String, String> queryLabelsByValues(String categoryAlias, List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            String projectNo = AppContext.getProjectNo();
            Long subjectId = AppContext.getSubjectId();

            // 查询字典配置项
            List<SysConfigItem> items =
                    configItemMapper.selectList(
                            new LambdaQueryWrapper<SysConfigItem>()
                                    .eq(SysConfigItem::getCategoryAlias, categoryAlias)
                                    .eq(SysConfigItem::getProjectNo, projectNo)
                                    .eq(SysConfigItem::getSubjectId, subjectId)
                                    .in(SysConfigItem::getValue, values)
                                    .eq(SysConfigItem::getStatus, 1));

            if (items.isEmpty()) {
                log.warn(
                        "字典配置项不存在: categoryAlias={}, projectNo={}, subjectId={}, values={}",
                        categoryAlias,
                        projectNo,
                        subjectId,
                        values);
                return Collections.emptyMap();
            }

            // 构建 value -> label 映射
            return items.stream()
                    .filter(item -> item.getValue() != null && item.getLabel() != null)
                    .collect(
                            Collectors.toMap(
                                    SysConfigItem::getValue,
                                    SysConfigItem::getLabel,
                                    (v1, v2) -> v1));

        } catch (Exception e) {
            log.error("查询字典描述失败: categoryAlias={}, values={}", categoryAlias, values, e);
            return Collections.emptyMap();
        }
    }
}
