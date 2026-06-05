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
import com.jdec.platform.config.biz.entity.SysMessageRecord;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModuleField;
import com.jdec.platform.config.biz.entity.SysWechatTemplate;
import com.jdec.platform.config.biz.entity.SysWechatTemplateParam;
import com.jdec.platform.config.biz.mapper.SysMessageRecordMapper;
import com.jdec.platform.config.biz.mapper.SysModuleFieldMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.mapper.SysWechatTemplateMapper;
import com.jdec.platform.config.biz.mapper.SysWechatTemplateParamMapper;
import com.jdec.platform.hr.api.UserApi;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.PageResult;
import com.jdec.platform.shared.utils.PageResultUtils;
import com.jdec.platform.shared.wechat.WeChatWorkService;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
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
    private final SysModuleMapper sysModuleMapper;
    private final SysModuleFieldMapper sysModuleFieldMapper;
    private final ObjectMapper objectMapper;
    private final UserApi userApi;
    private final WeChatWorkService weChatWorkService;
    private final SysMessageRecordMapper sysMessageRecordMapper;

    // --- 模板主表实现 ---

    @Override
    public PageResult<WechatTemplateEntryResp> listTemplates(
            String projectNo, Long subjectId, WechatTemplateQueryReq query) {
        Page<SysWechatTemplate> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysWechatTemplate> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SysWechatTemplate::getProjectNo, projectNo)
                .eq(subjectId != null, SysWechatTemplate::getSubjectId, subjectId)
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
    public WechatTemplateDetailResp createTemplate(
            String projectNo, Long subjectId, CreateWechatTemplateReq req) {
        SysWechatTemplate template = new SysWechatTemplate();
        copyTemplateProperties(projectNo, subjectId, req, template);
        template.setDeleted(0);
        sysWechatTemplateMapper.insert(template);
        return toTemplateDetailResp(template);
    }

    @Override
    @Transactional
    public WechatTemplateDetailResp updateTemplate(
            String projectNo, Long subjectId, UpdateWechatTemplateReq req) {
        SysWechatTemplate template = sysWechatTemplateMapper.selectById(req.getId());
        if (template == null || template.getDeleted() == 1) {
            throw new BusinessException(404, "模板不存在");
        }
        copyTemplateProperties(projectNo, subjectId, req, template);
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

    @Override
    public boolean testSendMessage(String projectNo, Long subjectId, Long id) {
        SysWechatTemplate template = sysWechatTemplateMapper.selectById(id);
        if (template == null || template.getDeleted() == 1) {
            throw new BusinessException(404, "模板不存在");
        }
        if (!Objects.equals(template.getProjectNo(), projectNo)
                || (subjectId != null && !Objects.equals(template.getSubjectId(), subjectId))) {
            throw new BusinessException(403, "无权访问此模板");
        }
        //        LoginUser loginUser = SecurityUtils.getLoginUserOrThrow();
        String phone = "19008612557";
        if (!StringUtils.hasText(phone)) {
            throw new BusinessException(400, "当前用户未绑定手机号");
        }
        String wechatUserId =
                userApi.getWeChatUserIdByPhone(phone)
                        .orElseThrow(() -> new BusinessException(400, "当前用户未绑定企业微信账号"));

        // 获取参数集
        LambdaQueryWrapper<SysWechatTemplateParam> paramWrapper = Wrappers.lambdaQuery();
        paramWrapper
                .eq(SysWechatTemplateParam::getProjectNo, projectNo)
                .eq(subjectId != null, SysWechatTemplateParam::getSubjectId, subjectId)
                .eq(SysWechatTemplateParam::getModuleId, template.getModuleId())
                .eq(SysWechatTemplateParam::getDeleted, 0);
        List<SysWechatTemplateParam> params = sysWechatTemplateParamMapper.selectList(paramWrapper);

        String content = formatContent(template.getTemplateContent(), params);

        // 创建发送日志记录并初始化写入
        SysMessageRecord record =
                SysMessageRecord.builder()
                        .projectNo(projectNo)
                        .subjectId(template.getSubjectId())
                        .receiver(wechatUserId)
                        .messageType("markdown")
                        .messageTitle(template.getTemplateTitle())
                        .messageContent(content)
                        .sendStatus(998) // 默认未发送成功/等待发送
                        .responseData("sending...")
                        .createdDate(LocalDateTime.now())
                        .updatedDate(LocalDateTime.now())
                        .deleted(0)
                        .build();
        sysMessageRecordMapper.insert(record);

        try {
            weChatWorkService.sendMarkdownMessage(template.getSubjectId(), wechatUserId, content);
            record.setSendStatus(0);
            record.setResponseData("{\"errcode\":0,\"errmsg\":\"ok\"}");
        } catch (Exception e) {
            log.error("测试发送微信模板消息失败", e);
            record.setSendStatus(997);
            record.setResponseData(e.getMessage());
        } finally {
            record.setUpdatedDate(LocalDateTime.now());
            sysMessageRecordMapper.updateById(record);
        }

        return record.getSendStatus() == 0;
    }

    private String formatContent(String content, List<SysWechatTemplateParam> params) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        if (params == null || params.isEmpty()) {
            return content;
        }
        String formatted = content;
        for (SysWechatTemplateParam param : params) {
            String slug = param.getTemplateParamSlug();
            String name = param.getTemplateParamName();
            if (StringUtils.hasText(slug) && StringUtils.hasText(name)) {
                formatted = formatted.replace(slug, name);
            }
        }
        return formatted;
    }

    // --- 模板参数实现 ---

    @Override
    public PageResult<WechatTemplateParamEntryResp> listParams(
            String projectNo, Long subjectId, WechatTemplateParamQueryReq query) {
        Page<SysWechatTemplateParam> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysWechatTemplateParam> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SysWechatTemplateParam::getProjectNo, projectNo)
                .eq(subjectId != null, SysWechatTemplateParam::getSubjectId, subjectId)
                .eq(
                        query.getModuleId() != null,
                        SysWechatTemplateParam::getModuleId,
                        query.getModuleId())
                .and(
                        StringUtils.hasText(query.getKeyword()),
                        q ->
                                q.like(
                                                SysWechatTemplateParam::getTemplateParamName,
                                                query.getKeyword())
                                        .or()
                                        .like(
                                                SysWechatTemplateParam::getTemplateParamSlug,
                                                query.getKeyword())
                                        .or()
                                        .like(
                                                SysWechatTemplateParam::getTemplateParamDesc,
                                                query.getKeyword()))
                .eq(SysWechatTemplateParam::getDeleted, 0)
                .orderByDesc(SysWechatTemplateParam::getCreatedDate);

        Page<SysWechatTemplateParam> result =
                sysWechatTemplateParamMapper.selectPage(page, wrapper);

        List<SysWechatTemplateParam> records = result.getRecords();
        List<WechatTemplateParamEntryResp> dtoList = convertToParamEntryRespList(records);

        Page<WechatTemplateParamEntryResp> convertedPage =
                new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        convertedPage.setRecords(dtoList);
        return PageResultUtils.of(convertedPage);
    }

    @Override
    public List<WechatTemplateParamEntryResp> listParamsByModuleId(
            String projectNo, Long subjectId, Long moduleId) {
        LambdaQueryWrapper<SysWechatTemplateParam> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SysWechatTemplateParam::getProjectNo, projectNo)
                .eq(subjectId != null, SysWechatTemplateParam::getSubjectId, subjectId)
                .eq(moduleId != null, SysWechatTemplateParam::getModuleId, moduleId)
                .eq(SysWechatTemplateParam::getDeleted, 0)
                .orderByDesc(SysWechatTemplateParam::getCreatedDate);

        List<SysWechatTemplateParam> list = sysWechatTemplateParamMapper.selectList(wrapper);
        return convertToParamEntryRespList(list);
    }

    @Override
    public WechatTemplateParamEntryResp getParamById(Long id) {
        SysWechatTemplateParam param = sysWechatTemplateParamMapper.selectById(id);
        if (param == null || param.getDeleted() == 1) {
            throw new BusinessException(404, "参数不存在");
        }

        WechatTemplateParamEntryResp resp = toParamEntryResp(param);

        // 装配单个详情的模块名与字段名
        if (param.getModuleId() != null) {
            SysModule module = sysModuleMapper.selectById(param.getModuleId());
            if (module != null) {
                resp.setModuleName(module.getModuleName());
            }
        }
        if (param.getFieldId() != null) {
            SysModuleField field = sysModuleFieldMapper.selectById(param.getFieldId());
            if (field != null) {
                resp.setFieldName(
                        field.getDisplayName() != null
                                ? field.getDisplayName()
                                : field.getFieldCode());
            }
        }

        return resp;
    }

    @Override
    @Transactional
    public WechatTemplateParamEntryResp createParam(
            String projectNo, Long subjectId, CreateWechatTemplateParamReq req) {
        SysWechatTemplateParam param = new SysWechatTemplateParam();
        copyParamProperties(projectNo, subjectId, req, param);
        param.setDeleted(0);
        sysWechatTemplateParamMapper.insert(param);
        return getParamById(param.getId());
    }

    @Override
    @Transactional
    public WechatTemplateParamEntryResp updateParam(
            String projectNo, Long subjectId, UpdateWechatTemplateParamReq req) {
        SysWechatTemplateParam param = sysWechatTemplateParamMapper.selectById(req.getId());
        if (param == null || param.getDeleted() == 1) {
            throw new BusinessException(404, "参数不存在");
        }
        copyParamProperties(projectNo, subjectId, req, param);
        sysWechatTemplateParamMapper.updateById(param);
        return getParamById(param.getId());
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
        populateTemplateResp(entity, resp);
        return resp;
    }

    private WechatTemplateDetailResp toTemplateDetailResp(SysWechatTemplate entity) {
        WechatTemplateDetailResp resp = new WechatTemplateDetailResp();
        populateTemplateResp(entity, resp);
        return resp;
    }

    private void populateTemplateResp(SysWechatTemplate entity, WechatTemplateEntryResp resp) {
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
        resp.setTemplateContent(entity.getTemplateContent());
        resp.setExtraReceiverIds(parseJsonList(entity.getExtraReceiverIds()));
        resp.setExtraReceiverNames(entity.getExtraReceiverNames());
        resp.setConfigParamIds(parseJsonList(entity.getConfigParamIds()));
        resp.setCreatedBy(entity.getCreatedBy());
        resp.setCreatedName(entity.getCreatedName());
        resp.setCreatedDate(entity.getCreatedDate());
        resp.setUpdatedBy(entity.getUpdatedBy());
        resp.setUpdatedDate(entity.getUpdatedDate());
        resp.setUpdatedName(entity.getUpdatedName());
    }

    private void copyTemplateProperties(
            String projectNo,
            Long subjectId,
            CreateWechatTemplateReq req,
            SysWechatTemplate entity) {
        entity.setProjectNo(projectNo);
        entity.setSubjectId(subjectId);
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
        resp.setConvertFlag(entity.getConvertFlag());
        resp.setTemplateParamConvertMethod(entity.getTemplateParamConvertMethod());
        resp.setCreatedName(entity.getCreatedName());
        resp.setCreatedDate(entity.getCreatedDate());
        resp.setUpdatedDate(entity.getUpdatedDate());
        return resp;
    }

    private void copyParamProperties(
            String projectNo,
            Long subjectId,
            CreateWechatTemplateParamReq req,
            SysWechatTemplateParam entity) {
        entity.setProjectNo(projectNo);
        entity.setSubjectId(subjectId);
        entity.setTemplateParamName(req.getTemplateParamName());
        entity.setTemplateParamSlug(req.getTemplateParamSlug());
        entity.setTemplateParamDesc(req.getTemplateParamDesc());
        entity.setModuleId(req.getModuleId());
        entity.setFieldId(req.getFieldId());
        entity.setConvertFlag(req.getConvertFlag());
        entity.setTemplateParamConvertMethod(req.getTemplateParamConvertMethod());
    }

    private List<WechatTemplateParamEntryResp> convertToParamEntryRespList(
            List<SysWechatTemplateParam> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 批量收集 IDs
        Set<Long> moduleIds =
                records.stream()
                        .map(SysWechatTemplateParam::getModuleId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        Set<Long> fieldIds =
                records.stream()
                        .map(SysWechatTemplateParam::getFieldId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        // 2. 批量查询 DB
        Map<Long, String> moduleNameMap = new HashMap<>();
        if (!moduleIds.isEmpty()) {
            List<SysModule> modules = sysModuleMapper.selectBatchIds(moduleIds);
            if (modules != null) {
                modules.forEach(m -> moduleNameMap.put(m.getId(), m.getModuleName()));
            }
        }

        Map<Long, String> fieldNameMap = new HashMap<>();
        if (!fieldIds.isEmpty()) {
            List<SysModuleField> fields = sysModuleFieldMapper.selectBatchIds(fieldIds);
            if (fields != null) {
                fields.forEach(
                        f ->
                                fieldNameMap.put(
                                        f.getId(),
                                        f.getDisplayName() != null
                                                ? f.getDisplayName()
                                                : f.getFieldCode()));
            }
        }

        // 3. 高速组装 DTO List
        return records.stream()
                .map(
                        param -> {
                            WechatTemplateParamEntryResp resp = toParamEntryResp(param);
                            if (param.getModuleId() != null) {
                                resp.setModuleName(moduleNameMap.get(param.getModuleId()));
                            }
                            if (param.getFieldId() != null) {
                                resp.setFieldName(fieldNameMap.get(param.getFieldId()));
                            }
                            return resp;
                        })
                .collect(Collectors.toList());
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
