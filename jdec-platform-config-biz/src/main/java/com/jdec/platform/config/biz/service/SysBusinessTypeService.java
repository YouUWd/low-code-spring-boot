package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.SysBusinessTypeApi;
import com.jdec.platform.config.api.dto.response.BusinessTypeOptionResp;
import com.jdec.platform.config.api.dto.response.SearchConfigItemResp;
import com.jdec.platform.config.biz.entity.SysBusinessType;
import com.jdec.platform.config.biz.mapper.SysBusinessTypeMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 业务类型 Service 实现 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysBusinessTypeService implements SysBusinessTypeApi {

    private static final TypeReference<List<SearchConfigItemResp>> SEARCH_TYPE_REFERENCE =
            new TypeReference<>() {};

    private final SysBusinessTypeMapper sysBusinessTypeMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<BusinessTypeOptionResp> listBusinessTypeOptions() {
        List<SysBusinessType> list =
                sysBusinessTypeMapper.selectList(
                        Wrappers.<SysBusinessType>lambdaQuery()
                                .eq(SysBusinessType::getDeleted, 0)
                                .orderByAsc(SysBusinessType::getId));
        return list.stream()
                .map(
                        entity ->
                                new BusinessTypeOptionResp(
                                        entity.getId(),
                                        entity.getBusinessTypeCode(),
                                        entity.getBusinessTypeName()))
                .toList();
    }

    @Override
    public List<SearchConfigItemResp> listSearchConfigByBusinessType(String businessTypeCode) {
        if (!StringUtils.hasText(businessTypeCode)) {
            return Collections.emptyList();
        }
        SysBusinessType entity =
                sysBusinessTypeMapper.selectOne(
                        Wrappers.<SysBusinessType>lambdaQuery()
                                .eq(SysBusinessType::getBusinessTypeCode, businessTypeCode.trim())
                                .eq(SysBusinessType::getDeleted, 0));
        if (entity == null) {
            return Collections.emptyList();
        }
        return parseSearchType(entity.getBusinessTypeCode(), entity.getSearchType());
    }

    /** 解析 search_type JSON 数组 */
    private List<SearchConfigItemResp> parseSearchType(String businessTypeCode, String searchType) {
        if (!StringUtils.hasText(searchType)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(searchType, SEARCH_TYPE_REFERENCE);
        } catch (Exception e) {
            log.error("解析业务类型 {} 的搜索配置失败", businessTypeCode, e);
            throw new BusinessException("业务类型搜索配置格式错误: " + businessTypeCode);
        }
    }
}
