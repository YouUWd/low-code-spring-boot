package com.jdec.platform.externalobject.biz.service.impl;

import com.jdec.platform.externalobject.api.ExternalObjectRealtimeValidateApi;
import com.jdec.platform.externalobject.api.dto.request.CompanyRegStatusUpdateReq;
import com.jdec.platform.externalobject.api.dto.request.ExternalObjectRealtimeValidateReq;
import com.jdec.platform.externalobject.api.dto.response.ExternalObjectRealtimeValidateResp;
import com.jdec.platform.externalobject.biz.entity.Info;
import com.jdec.platform.externalobject.biz.mapper.InfoMapper;
import com.jdec.platform.externalobject.biz.service.CompanyCustomService;
import com.jdec.platform.externalobject.biz.service.support.ExternalObjectWordValues;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 外部对象特定业务定制服务实现类 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.COMPANY)
public class CompanyCustomServiceImpl implements CompanyCustomService {

    /** 业务状态：启用 (99) */
    public static final int STATUS_ACTIVE = 99;

    /** 业务状态：取消 (-99) */
    public static final int STATUS_CANCEL = -99;

    private final InfoMapper infoMapper;
    private final ExternalObjectRealtimeValidateApi realtimeValidateApi;

    @Override
    public void updateRegStatus(CompanyRegStatusUpdateReq req) {
        if (req == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        if (req.getId() == null) {
            throw new IllegalArgumentException("单据 ID 不能为空");
        }
        if (req.getRegStatus() == null) {
            throw new IllegalArgumentException("登记状态 regStatus 不能为空");
        }
        String regStatusText = ExternalObjectWordValues.toText(req.getRegStatus()).trim();
        if (ExternalObjectWordValues.isBlank(regStatusText)) {
            throw new IllegalArgumentException("登记状态 regStatus 不能为空");
        }

        // 1. 根据物理主键 id 定位目标单据
        Info info = infoMapper.selectById(req.getId());
        if (info == null || (info.getDeleted() != null && info.getDeleted() != 0)) {
            throw new BusinessException("未找到对应的外部对象单据记录");
        }

        // 2. 状态检查：仅启用 (99) 或取消 (-99) 状态允许修改登记状态
        Integer businessStatus = info.getBusinessStatus();
        if (businessStatus == null
                || (businessStatus != STATUS_ACTIVE && businessStatus != STATUS_CANCEL)) {
            throw new BusinessException("只有处于【已启用 (99)】或【已取消 (-99)】状态的单据才允许修改登记状态");
        }

        // 3. 关联对象性质 (nature) 执行登记状态规则校验 (传入原始 Object 或转换文本)
        validateRegStatus(info, req.getRegStatus());

        // 4. 执行更新 (数据库中保存 JSON 字符串)
        String oldRegStatus = info.getRegStatus();
        info.setRegStatus(regStatusText);
        info.setUpdatedBy(UserContext.getOperateUserId());
        info.setUpdatedDate(LocalDateTime.now());
        infoMapper.updateById(info);

        log.info(
                "[CompanyCustom-UpdateRegStatus] 登记状态修改成功: id={}, businessNo={}, businessStatus={}, 原状态={}, 新状态={}, 操作人={}",
                info.getId(),
                info.getBusinessNo(),
                info.getBusinessStatus(),
                oldRegStatus,
                info.getRegStatus(),
                UserContext.getOperateUserId());
    }

    private void validateRegStatus(Info info, Object regStatus) {
        if (realtimeValidateApi == null || info == null) {
            return;
        }
        ExternalObjectRealtimeValidateReq validateReq =
                new ExternalObjectRealtimeValidateReq(
                        info.getId(), info.getId(), "info.reg_status", regStatus);
        ExternalObjectRealtimeValidateResp validateResp = realtimeValidateApi.validate(validateReq);
        if (validateResp != null && !validateResp.valid()) {
            throw new BusinessException(validateResp.message());
        }
    }
}
