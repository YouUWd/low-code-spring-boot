package com.jdec.platform.common.biz.monitorlog.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.common.api.dto.request.MonitorLogSaveReq;
import com.jdec.platform.common.biz.monitorlog.entity.MonitorLog;
import com.jdec.platform.shared.exception.BusinessException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 监控日志保存处理器抽象基类
 *
 * <p>公共逻辑：字段搬运 + {@code approve}、{@code content} 等对象的 JSON 序列化；子类差异仅在于日志类型，需要时重写 {@link
 * #buildEntity(MonitorLogSaveReq)} 追加各自处理。
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractMonitorLogSaveHandler implements MonitorLogSaveHandler {

    protected final ObjectMapper objectMapper;

    @Override
    public MonitorLog buildEntity(MonitorLogSaveReq req) {
        MonitorLog entity = new MonitorLog();
        entity.setType(supportType());
        entity.setSysType(req.getSysType());
        entity.setSubjectId(req.getSubjectId());
        entity.setSubjectName(req.getSubjectName());
        entity.setIp(req.getIp());
        entity.setModuleId(req.getModuleId());
        entity.setDataId(req.getDataId());
        entity.setBrowser(req.getBrowser());
        entity.setPlatForm(req.getPlatForm());
        entity.setRecordTime(
                req.getRecordTime() != null ? req.getRecordTime() : LocalDateTime.now());
        entity.setCreatedDate(LocalDateTime.now());
        entity.setApprove(toJson(req.getApprove(), "approve"));
        entity.setLoginInfo(toJson(req.getLoginInfo(), "loginInfo"));
        entity.setMockInfo(toJson(req.getMockInfo(), "mockInfo"));
        entity.setContent(toJson(req.getContent(), "content"));
        entity.setRemark(toJson(req.getRemark(), "remark"));
        return entity;
    }

    /** 对象序列化为 JSON 字符串，空值返回 null（避免库中写入 "null"） */
    private String toJson(Object value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("监控日志 {} 序列化失败: type={}, value={}", field, supportType(), value, e);
            throw new BusinessException("监控日志保存失败：日志内容格式不正确");
        }
    }
}
