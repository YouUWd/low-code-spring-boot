package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.SysWechatTemplateApi;
import com.jdec.platform.config.api.dto.request.CreateWechatTemplateParamReq;
import com.jdec.platform.config.api.dto.request.CreateWechatTemplateReq;
import com.jdec.platform.config.api.dto.request.UpdateWechatTemplateParamReq;
import com.jdec.platform.config.api.dto.request.UpdateWechatTemplateReq;
import com.jdec.platform.config.api.dto.request.WechatTemplateParamQueryReq;
import com.jdec.platform.config.api.dto.request.WechatTemplateQueryReq;
import com.jdec.platform.config.api.dto.response.WechatTemplateDetailResp;
import com.jdec.platform.config.api.dto.response.WechatTemplateEntryResp;
import com.jdec.platform.config.api.dto.response.WechatTemplateParamEntryResp;
import com.jdec.platform.config.biz.entity.SysWechatTemplate;
import com.jdec.platform.config.biz.entity.SysWechatTemplateParam;
import com.jdec.platform.config.biz.mapper.SysWechatTemplateMapper;
import com.jdec.platform.config.biz.mapper.SysWechatTemplateParamMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.PageResult;
import com.jdec.platform.shared.utils.PageResultUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 企业微信消息通知模板 Service 实现 (含模板参数管理) */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysWechatTemplateService implements SysWechatTemplateApi {

    private final SysWechatTemplateMapper sysWechatTemplateMapper;
    private final SysWechatTemplateParamMapper sysWechatTemplateParamMapper;
    private final ObjectMapper objectMapper;

    // --- 模板主表实现 ---

    @Override
    public PageResult<WechatTemplateEntryResp> listTemplates(WechatTemplateQueryReq query) {
        Page<SysWechatTemplate> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysWechatTemplate> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SysWechatTemplate::getProjectNo, query.getProjectNo())
                .eq(
                        query.getSubjectId() != null,
                        SysWechatTemplate::getSubjectId,
                        query.getSubjectId())
                .eq(
                        query.getModuleId() != null,
                        SysWechatTemplate::getModuleId,
                        query.getModuleId())
                .like(
                        StringUtils.hasText(query.getTemplateTitle()),
                        SysWechatTemplate::getTemplateTitle,
                        query.getTemplateTitle())
                .eq(SysWechatTemplate::getDeleted, 0)
                .orderByDesc(SysWechatTemplate::getCreatedDate);

        Page<SysWechatTemplate> result = sysWechatTemplateMapper.selectPage(page, wrapper);
        return PageResultUtils.of(result.convert(this::toTemplateEntryResp));
    }

    @Override
    public List<WechatTemplateEntryResp> listTemplatesByModuleId(
            String projectNo, Long subjectId, Long moduleId) {
        LambdaQueryWrapper<SysWechatTemplate> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SysWechatTemplate::getProjectNo, projectNo)
                .eq(subjectId != null, SysWechatTemplate::getSubjectId, subjectId)
                .eq(SysWechatTemplate::getModuleId, moduleId)
                .eq(SysWechatTemplate::getDeleted, 0)
                .orderByDesc(SysWechatTemplate::getCreatedDate);

        List<SysWechatTemplate> list = sysWechatTemplateMapper.selectList(wrapper);
        return list.stream().map(this::toTemplateEntryResp).toList();
    }

    @Override
    public WechatTemplateDetailResp getTemplateById(Long id) {
        SysWechatTemplate template = sysWechatTemplateMapper.selectById(id);
        if (template == null || template.getDeleted() == 1) {
            throw new BusinessException(404, "模板不存在");
        }
        return toTemplateDetailResp(template);
    }

    @Override
    @Transactional
    public WechatTemplateDetailResp createTemplate(CreateWechatTemplateReq req) {
        SysWechatTemplate template = new SysWechatTemplate();
        copyTemplateProperties(req, template);
        template.setDeleted(0);
        sysWechatTemplateMapper.insert(template);
        return toTemplateDetailResp(template);
    }

    @Override
    @Transactional
    public WechatTemplateDetailResp updateTemplate(UpdateWechatTemplateReq req) {
        SysWechatTemplate template = sysWechatTemplateMapper.selectById(req.getId());
        if (template == null || template.getDeleted() == 1) {
            throw new BusinessException(404, "模板不存在");
        }
        copyTemplateProperties(req, template);
        sysWechatTemplateMapper.updateById(template);
        return toTemplateDetailResp(template);
    }

    @Override
    @Transactional
    public void deleteTemplate(Long id) {
        SysWechatTemplate template = sysWechatTemplateMapper.selectById(id);
        if (template != null) {
            template.setDeleted(1);
            sysWechatTemplateMapper.updateById(template);
        }
    }

    // --- 模板参数实现 ---

    @Override
    public PageResult<WechatTemplateParamEntryResp> listParams(WechatTemplateParamQueryReq query) {
        Page<SysWechatTemplateParam> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysWechatTemplateParam> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SysWechatTemplateParam::getProjectNo, query.getProjectNo())
                .eq(
                        query.getSubjectId() != null,
                        SysWechatTemplateParam::getSubjectId,
                        query.getSubjectId())
                .eq(
                        query.getModuleId() != null,
                        SysWechatTemplateParam::getModuleId,
                        query.getModuleId())
                .like(
                        StringUtils.hasText(query.getTemplateParamName()),
                        SysWechatTemplateParam::getTemplateParamName,
                        query.getTemplateParamName())
                .eq(SysWechatTemplateParam::getDeleted, 0)
                .orderByDesc(SysWechatTemplateParam::getCreatedDate);

        Page<SysWechatTemplateParam> result =
                sysWechatTemplateParamMapper.selectPage(page, wrapper);
        return PageResultUtils.of(result.convert(this::toParamEntryResp));
    }

    @Override
    public WechatTemplateParamEntryResp getParamById(Long id) {
        SysWechatTemplateParam param = sysWechatTemplateParamMapper.selectById(id);
        if (param == null || param.getDeleted() == 1) {
            throw new BusinessException(404, "参数不存在");
        }
        return toParamEntryResp(param);
    }

    @Override
    @Transactional
    public WechatTemplateParamEntryResp createParam(CreateWechatTemplateParamReq req) {
        SysWechatTemplateParam param = new SysWechatTemplateParam();
        copyParamProperties(req, param);
        param.setDeleted(0);
        sysWechatTemplateParamMapper.insert(param);
        return toParamEntryResp(param);
    }

    @Override
    @Transactional
    public WechatTemplateParamEntryResp updateParam(UpdateWechatTemplateParamReq req) {
        SysWechatTemplateParam param = sysWechatTemplateParamMapper.selectById(req.getId());
        if (param == null || param.getDeleted() == 1) {
            throw new BusinessException(404, "参数不存在");
        }
        copyParamProperties(req, param);
        sysWechatTemplateParamMapper.updateById(param);
        return toParamEntryResp(param);
    }

    @Override
    @Transactional
    public void deleteParam(Long id) {
        SysWechatTemplateParam param = sysWechatTemplateParamMapper.selectById(id);
        if (param != null) {
            param.setDeleted(1);
            sysWechatTemplateParamMapper.updateById(param);
        }
    }

    // --- 内部辅助方法 ---

    private WechatTemplateEntryResp toTemplateEntryResp(SysWechatTemplate entity) {
        WechatTemplateEntryResp resp = new WechatTemplateEntryResp();
        resp.setId(entity.getId());
        resp.setProjectNo(entity.getProjectNo());
        resp.setSubjectId(entity.getSubjectId());
        resp.setTemplateTitle(entity.getTemplateTitle());
        resp.setTemplateSendType(entity.getTemplateSendType());
        resp.setTemplateAcceptType(parseJsonList(entity.getTemplateAcceptType()));
        resp.setTemplateTriggerType(entity.getTemplateTriggerType());
        resp.setModuleId(entity.getModuleId());
        resp.setModuleName(entity.getModuleName());
        resp.setApprovalFlag(entity.getApprovalFlag());
        resp.setCreatedName(entity.getCreatedName());
        resp.setCreatedDate(entity.getCreatedDate());
        resp.setUpdatedDate(entity.getUpdatedDate());
        return resp;
    }

    private WechatTemplateDetailResp toTemplateDetailResp(SysWechatTemplate entity) {
        WechatTemplateDetailResp resp = new WechatTemplateDetailResp();
        resp.setId(entity.getId());
        resp.setProjectNo(entity.getProjectNo());
        resp.setSubjectId(entity.getSubjectId());
        resp.setTemplateTitle(entity.getTemplateTitle());
        resp.setTemplateSendType(entity.getTemplateSendType());
        resp.setTemplateAcceptType(parseJsonList(entity.getTemplateAcceptType()));
        resp.setTemplateTriggerType(entity.getTemplateTriggerType());
        resp.setModuleId(entity.getModuleId());
        resp.setModuleName(entity.getModuleName());
        resp.setApprovalFlag(entity.getApprovalFlag());
        resp.setCreatedName(entity.getCreatedName());
        resp.setCreatedDate(entity.getCreatedDate());
        resp.setUpdatedDate(entity.getUpdatedDate());
        resp.setTemplateContent(entity.getTemplateContent());
        resp.setExtraReceiverIds(parseJsonList(entity.getExtraReceiverIds()));
        resp.setExtraReceiverNames(entity.getExtraReceiverNames());
        resp.setConfigParamIds(parseJsonList(entity.getConfigParamIds()));
        return resp;
    }

    private void copyTemplateProperties(CreateWechatTemplateReq req, SysWechatTemplate entity) {
        entity.setProjectNo(req.getProjectNo());
        entity.setSubjectId(req.getSubjectId());
        entity.setTemplateTitle(req.getTemplateTitle());
        entity.setTemplateSendType(req.getTemplateSendType());
        entity.setTemplateAcceptType(toJsonString(req.getTemplateAcceptType()));
        entity.setTemplateTriggerType(req.getTemplateTriggerType());
        entity.setTemplateContent(req.getTemplateContent());
        entity.setExtraReceiverIds(toJsonString(req.getExtraReceiverIds()));
        entity.setExtraReceiverNames(req.getExtraReceiverNames());
        entity.setConfigParamIds(toJsonString(req.getConfigParamIds()));
        entity.setModuleId(req.getModuleId());
        entity.setModuleName(req.getModuleName());
        entity.setApprovalFlag(req.getApprovalFlag());
    }

    private WechatTemplateParamEntryResp toParamEntryResp(SysWechatTemplateParam entity) {
        WechatTemplateParamEntryResp resp = new WechatTemplateParamEntryResp();
        resp.setId(entity.getId());
        resp.setProjectNo(entity.getProjectNo());
        resp.setSubjectId(entity.getSubjectId());
        resp.setTemplateParamName(entity.getTemplateParamName());
        resp.setTemplateParamSlug(entity.getTemplateParamSlug());
        resp.setTemplateParamDesc(entity.getTemplateParamDesc());
        resp.setModuleId(entity.getModuleId());
        resp.setFieldId(entity.getFieldId());
        resp.setFieldType(entity.getFieldType());
        resp.setConvertFlag(entity.getConvertFlag());
        resp.setTemplateParamConvertMethod(entity.getTemplateParamConvertMethod());
        resp.setCreatedName(entity.getCreatedName());
        resp.setCreatedDate(entity.getCreatedDate());
        resp.setUpdatedDate(entity.getUpdatedDate());
        return resp;
    }

    private void copyParamProperties(
            CreateWechatTemplateParamReq req, SysWechatTemplateParam entity) {
        entity.setProjectNo(req.getProjectNo());
        entity.setSubjectId(req.getSubjectId());
        entity.setTemplateParamName(req.getTemplateParamName());
        entity.setTemplateParamSlug(req.getTemplateParamSlug());
        entity.setTemplateParamDesc(req.getTemplateParamDesc());
        entity.setModuleId(req.getModuleId());
        entity.setFieldId(req.getFieldId());
        entity.setFieldType(req.getFieldType());
        entity.setConvertFlag(req.getConvertFlag());
        entity.setTemplateParamConvertMethod(req.getTemplateParamConvertMethod());
    }

    private String toJsonString(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON 序列化失败", e);
            return null;
        }
    }

    private List<Long> parseJsonList(String json) {
        if (!StringUtils.hasText(json)) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.error("JSON 反序列化失败", e);
            return null;
        }
    }
}
