package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.api.SysModuleFieldApi;
import com.jdec.platform.config.api.dto.request.CreateSysModuleFieldReq;
import com.jdec.platform.config.api.dto.request.QuerySysModuleFieldReq;
import com.jdec.platform.config.api.dto.request.UpdateSysModuleFieldReq;
import com.jdec.platform.config.api.dto.response.SysModuleFieldDetailResp;
import com.jdec.platform.config.api.dto.response.SysModuleFieldEntryResp;
import com.jdec.platform.config.biz.entity.SysModuleField;
import com.jdec.platform.config.biz.mapper.SysModuleFieldMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模块字段管理 Service 实现
 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysModuleFieldService implements SysModuleFieldApi {

    private final SysModuleFieldMapper sysModuleFieldMapper;

    @Override
    public SysModuleFieldEntryResp createModuleField(CreateSysModuleFieldReq request) {
        SysModuleField field = new SysModuleField();
        field.setModuleId(request.getModuleId());
        field.setFieldCode(request.getFieldCode());
        field.setDisplayName(request.getDisplayName());
        field.setLogicalField(request.getLogicalField());
        field.setSourceMapping(request.getSourceMapping());
        field.setTransformer(request.getTransformer());
        field.setTransformerEnv(request.getTransformerEnv() != null ? request.getTransformerEnv() : "none");
        field.setRenderIcon(request.getRenderIcon());
        field.setRenderType(request.getRenderType());
        field.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        field.setVisible(request.getVisible() != null ? request.getVisible() : 1);
        field.setRequired(request.getRequired() != null ? request.getRequired() : 0);
        field.setSearchable(request.getSearchable() != null ? request.getSearchable() : 1);
        field.setSortable(request.getSortable() != null ? request.getSortable() : 1);
        field.setDefaultValue(request.getDefaultValue());
        field.setValidationRule(request.getValidationRule());
        field.setCreatedBy(request.getCreatedBy());
        field.setCreatedDate(LocalDateTime.now());
        field.setCreatedName(request.getCreatedName());
        field.setDeleted(0);

        sysModuleFieldMapper.insert(field);
        return toModuleFieldEntry(field);
    }

    @Override
    public SysModuleFieldDetailResp getModuleFieldById(Long fieldId) {
        SysModuleField field = sysModuleFieldMapper.selectById(fieldId);
        return toModuleFieldDetail(field);
    }

    @Override
    public List<SysModuleFieldEntryResp> getFieldsByModule(Long moduleId) {
        LambdaQueryWrapper<SysModuleField> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModuleField::getModuleId, moduleId)
                .eq(SysModuleField::getDeleted, 0)
                .orderByAsc(SysModuleField::getSortOrder);

        List<SysModuleField> fields = sysModuleFieldMapper.selectList(wrapper);
        return fields.stream()
                .map(this::toModuleFieldEntry)
                .collect(Collectors.toList());
    }

    @Override
    public List<SysModuleFieldEntryResp> queryModuleFields(QuerySysModuleFieldReq request) {
        LambdaQueryWrapper<SysModuleField> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysModuleField::getModuleId, request.getModuleId())
                .eq(SysModuleField::getDeleted, 0);

        if (request.getFieldCode() != null) {
            wrapper.like(SysModuleField::getFieldCode, request.getFieldCode());
        }
        if (request.getDisplayName() != null) {
            wrapper.like(SysModuleField::getDisplayName, request.getDisplayName());
        }
        if (request.getLogicalField() != null) {
            wrapper.eq(SysModuleField::getLogicalField, request.getLogicalField());
        }
        if (request.getTransformerEnv() != null) {
            wrapper.eq(SysModuleField::getTransformerEnv, request.getTransformerEnv());
        }
        if (request.getVisible() != null) {
            wrapper.eq(SysModuleField::getVisible, request.getVisible());
        }
        if (request.getSearchable() != null) {
            wrapper.eq(SysModuleField::getSearchable, request.getSearchable());
        }
        if (request.getSortable() != null) {
            wrapper.eq(SysModuleField::getSortable, request.getSortable());
        }

        wrapper.orderByAsc(SysModuleField::getSortOrder);
        List<SysModuleField> fields = sysModuleFieldMapper.selectList(wrapper);
        return fields.stream()
                .map(this::toModuleFieldEntry)
                .collect(Collectors.toList());
    }

    @Override
    public SysModuleFieldEntryResp updateModuleField(UpdateSysModuleFieldReq request) {
        SysModuleField field = sysModuleFieldMapper.selectById(request.getId());
        if (field == null) {
            throw new RuntimeException("字段不存在");
        }

        field.setDisplayName(request.getDisplayName());
        field.setSourceMapping(request.getSourceMapping());
        field.setTransformer(request.getTransformer());
        field.setTransformerEnv(request.getTransformerEnv());
        field.setRenderIcon(request.getRenderIcon());
        field.setRenderType(request.getRenderType());
        field.setSortOrder(request.getSortOrder());
        field.setVisible(request.getVisible());
        field.setRequired(request.getRequired());
        field.setSearchable(request.getSearchable());
        field.setSortable(request.getSortable());
        field.setDefaultValue(request.getDefaultValue());
        field.setValidationRule(request.getValidationRule());
        field.setUpdatedBy(request.getUpdatedBy());
        field.setUpdatedDate(LocalDateTime.now());
        field.setUpdatedName(request.getUpdatedName());

        sysModuleFieldMapper.updateById(field);
        return toModuleFieldEntry(field);
    }

    @Override
    public void deleteModuleField(Long fieldId) {
        SysModuleField field = sysModuleFieldMapper.selectById(fieldId);
        if (field != null) {
            field.setDeleted(1);
            sysModuleFieldMapper.updateById(field);
        }
    }

    @Override
    public void batchDeleteModuleFields(List<Long> fieldIds) {
        if (CollectionUtils.isEmpty(fieldIds)) {
            return;
        }
        LambdaQueryWrapper<SysModuleField> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysModuleField::getId, fieldIds);
        List<SysModuleField> fields = sysModuleFieldMapper.selectList(wrapper);
        fields.forEach(field -> {
            field.setDeleted(1);
            sysModuleFieldMapper.updateById(field);
        });
    }

    private SysModuleFieldEntryResp toModuleFieldEntry(SysModuleField field) {
        SysModuleFieldEntryResp resp = new SysModuleFieldEntryResp();
        resp.setId(field.getId());
        resp.setModuleId(field.getModuleId());
        resp.setFieldCode(field.getFieldCode());
        resp.setDisplayName(field.getDisplayName());
        resp.setLogicalField(field.getLogicalField());
        resp.setRenderType(field.getRenderType());
        resp.setSortOrder(field.getSortOrder());
        resp.setVisible(field.getVisible());
        resp.setRequired(field.getRequired());
        resp.setSearchable(field.getSearchable());
        resp.setSortable(field.getSortable());
        resp.setCreatedDate(field.getCreatedDate());
        resp.setUpdatedDate(field.getUpdatedDate());
        return resp;
    }

    private SysModuleFieldDetailResp toModuleFieldDetail(SysModuleField field) {
        if (field == null) {
            return null;
        }
        SysModuleFieldDetailResp resp = new SysModuleFieldDetailResp();
        resp.setId(field.getId());
        resp.setModuleId(field.getModuleId());
        resp.setFieldCode(field.getFieldCode());
        resp.setDisplayName(field.getDisplayName());
        resp.setLogicalField(field.getLogicalField());
        resp.setSourceMapping(field.getSourceMapping());
        resp.setTransformer(field.getTransformer());
        resp.setTransformerEnv(field.getTransformerEnv());
        resp.setRenderIcon(field.getRenderIcon());
        resp.setRenderType(field.getRenderType());
        resp.setSortOrder(field.getSortOrder());
        resp.setVisible(field.getVisible());
        resp.setRequired(field.getRequired());
        resp.setSearchable(field.getSearchable());
        resp.setSortable(field.getSortable());
        resp.setDefaultValue(field.getDefaultValue());
        resp.setValidationRule(field.getValidationRule());
        resp.setCreatedBy(field.getCreatedBy());
        resp.setCreatedDate(field.getCreatedDate());
        resp.setCreatedName(field.getCreatedName());
        resp.setUpdatedBy(field.getUpdatedBy());
        resp.setUpdatedDate(field.getUpdatedDate());
        resp.setUpdatedName(field.getUpdatedName());
        return resp;
    }
}
