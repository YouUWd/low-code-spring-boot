package com.jdec.platform.common.biz.postprocess.enhance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.common.biz.postprocess.enhance.context.DataRowEnhanceContext;
import com.jdec.platform.common.biz.postprocess.enhance.context.OptionEnhanceContext;
import com.jdec.platform.common.biz.postprocess.enhance.provider.DataRowEnhanceProvider;
import com.jdec.platform.common.biz.postprocess.enhance.provider.OptionEnhanceProvider;
import com.jdec.platform.common.biz.postprocess.enhance.provider.option.IndustryOptionEnhanceProvider;
import com.jdec.platform.common.biz.postprocess.enhance.provider.option.StatusOptionEnhanceProvider;
import com.jdec.platform.common.biz.postprocess.enhance.provider.option.UserOptionEnhanceProvider;
import com.jdec.platform.common.biz.postprocess.enhance.provider.option.WordCloudOptionEnhanceProvider;
import com.jdec.platform.common.biz.postprocess.enhance.provider.row.StatusRowEnhanceProvider;
import com.jdec.platform.common.biz.postprocess.enhance.provider.row.UserRowEnhanceProvider;
import com.jdec.platform.common.biz.support.loader.StatusInfoLoader;
import com.jdec.platform.common.biz.support.loader.UserInfoLoader;
import com.jdec.platform.config.api.SysStatusApi;
import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 菜单查询结果与候选项数据增强服务实现类 (装配调度器)
 *
 * <p>基于面向对象装配模式 (Assemble Provider Pattern)，自动收集并调度所有注册的 {@link DataRowEnhanceProvider} 与 {@link
 * OptionEnhanceProvider}。
 */
@Slf4j
@Service
public class QueryResultEnhanceServiceImpl implements QueryResultEnhanceService {

    private final List<DataRowEnhanceProvider> rowEnhanceProviders;
    private final List<OptionEnhanceProvider> optionEnhanceProviders;

    @Autowired
    public QueryResultEnhanceServiceImpl(
            @Autowired(required = false) List<DataRowEnhanceProvider> rowEnhanceProviders,
            @Autowired(required = false) List<OptionEnhanceProvider> optionEnhanceProviders) {
        this.rowEnhanceProviders = sortRowProviders(rowEnhanceProviders);
        this.optionEnhanceProviders = sortOptionProviders(optionEnhanceProviders);
    }

    /** 兼容历史测试用例构造器 (手动实例化默认 Providers) */
    public QueryResultEnhanceServiceImpl(SysUserApi sysUserApi, SysStatusApi sysStatusApi) {
        this(sysUserApi, sysStatusApi, new ObjectMapper());
    }

    public QueryResultEnhanceServiceImpl(
            SysUserApi sysUserApi, SysStatusApi sysStatusApi, ObjectMapper objectMapper) {
        ObjectMapper mapper = objectMapper != null ? objectMapper : new ObjectMapper();
        UserInfoLoader userLoader = new UserInfoLoader(sysUserApi);
        StatusInfoLoader statusLoader = new StatusInfoLoader(sysStatusApi);

        this.rowEnhanceProviders =
                List.of(
                        new UserRowEnhanceProvider(userLoader),
                        new StatusRowEnhanceProvider(statusLoader));
        this.optionEnhanceProviders =
                List.of(
                        new UserOptionEnhanceProvider(userLoader),
                        new StatusOptionEnhanceProvider(statusLoader),
                        new IndustryOptionEnhanceProvider(mapper),
                        new WordCloudOptionEnhanceProvider(mapper));
    }

    @Override
    public void enhanceDataRows(List<DataRow> records, Long rootModuleId, String primaryTable) {
        enhanceDataRows(records, rootModuleId, primaryTable, null);
    }

    @Override
    public void enhanceDataRows(
            List<DataRow> records,
            Long rootModuleId,
            String primaryTable,
            MenuHeaderColumnResp headerRoot) {
        if (CollectionUtils.isEmpty(records)) {
            return;
        }

        DataRowEnhanceContext ctx =
                DataRowEnhanceContext.builder()
                        .rootModuleId(rootModuleId)
                        .primaryTable(primaryTable)
                        .headerRoot(headerRoot)
                        .build();

        // 仅处理第一层数据行增强 (无需递归处理子区块)
        for (DataRowEnhanceProvider provider : rowEnhanceProviders) {
            if (provider.supports(ctx)) {
                provider.enhance(records, ctx);
            }
        }
    }

    @Override
    public List<DataOptionResp> enhanceOptions(
            List<DataOptionResp> rawOptions, String columnName, Long rootModuleId) {
        return enhanceOptions(rawOptions, columnName, rootModuleId, (String) null);
    }

    @Override
    public List<DataOptionResp> enhanceOptions(
            List<DataOptionResp> rawOptions,
            String columnName,
            Long rootModuleId,
            String businessTypeCode) {
        return enhanceOptions(rawOptions, columnName, rootModuleId, null, null, businessTypeCode);
    }

    @Override
    public List<DataOptionResp> enhanceOptions(
            List<DataOptionResp> rawOptions,
            String columnName,
            Long rootModuleId,
            MenuHeaderColumnResp headerRoot,
            Long fieldId) {
        return enhanceOptions(rawOptions, columnName, rootModuleId, headerRoot, fieldId, null);
    }

    private List<DataOptionResp> enhanceOptions(
            List<DataOptionResp> rawOptions,
            String columnName,
            Long rootModuleId,
            MenuHeaderColumnResp headerRoot,
            Long fieldId,
            String explicitBusinessTypeCode) {
        if (CollectionUtils.isEmpty(rawOptions)) {
            return rawOptions;
        }

        MenuHeaderColumnResp targetHeaderNode = findHeaderNode(headerRoot, fieldId, columnName);
        String finalBusinessTypeCode =
                explicitBusinessTypeCode != null
                        ? explicitBusinessTypeCode
                        : (targetHeaderNode != null
                                ? targetHeaderNode.getBusinessTypeCode()
                                : null);

        String normalizedCol =
                columnName != null && columnName.contains(".")
                        ? columnName.substring(columnName.lastIndexOf('.') + 1).trim()
                        : (columnName != null ? columnName.trim() : "");

        OptionEnhanceContext ctx =
                OptionEnhanceContext.builder()
                        .columnName(columnName)
                        .normalizedColumn(normalizedCol)
                        .rootModuleId(rootModuleId)
                        .businessTypeCode(finalBusinessTypeCode)
                        .headerRoot(headerRoot)
                        .fieldId(fieldId)
                        .targetHeaderNode(targetHeaderNode)
                        .build();

        for (OptionEnhanceProvider provider : optionEnhanceProviders) {
            if (provider.supports(ctx)) {
                return provider.enhance(rawOptions, ctx);
            }
        }

        return rawOptions;
    }

    private MenuHeaderColumnResp findHeaderNode(
            MenuHeaderColumnResp node, Long fieldId, String columnName) {
        if (node == null) {
            return null;
        }
        if (fieldId != null && fieldId.equals(node.getFieldId())) {
            return node;
        }
        if (StringUtils.hasText(columnName) && StringUtils.hasText(node.getDataIndex())) {
            String di = node.getDataIndex().trim().toLowerCase(Locale.ROOT);
            String col = columnName.trim().toLowerCase(Locale.ROOT);
            if (di.equals(col) || di.endsWith("." + col)) {
                return node;
            }
        }
        if (!CollectionUtils.isEmpty(node.getChildren())) {
            for (MenuHeaderColumnResp child : node.getChildren()) {
                MenuHeaderColumnResp target = findHeaderNode(child, fieldId, columnName);
                if (target != null) {
                    return target;
                }
            }
        }
        return null;
    }

    private static List<DataRowEnhanceProvider> sortRowProviders(
            List<DataRowEnhanceProvider> providers) {
        if (CollectionUtils.isEmpty(providers)) {
            return Collections.emptyList();
        }
        List<DataRowEnhanceProvider> sorted = new ArrayList<>(providers);
        sorted.sort(Comparator.comparingInt(DataRowEnhanceProvider::order));
        return Collections.unmodifiableList(sorted);
    }

    private static List<OptionEnhanceProvider> sortOptionProviders(
            List<OptionEnhanceProvider> providers) {
        if (CollectionUtils.isEmpty(providers)) {
            return Collections.emptyList();
        }
        List<OptionEnhanceProvider> sorted = new ArrayList<>(providers);
        sorted.sort(Comparator.comparingInt(OptionEnhanceProvider::order));
        return Collections.unmodifiableList(sorted);
    }
}
