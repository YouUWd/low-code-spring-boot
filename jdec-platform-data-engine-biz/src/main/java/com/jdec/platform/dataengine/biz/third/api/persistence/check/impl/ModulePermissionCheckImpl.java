package com.jdec.platform.dataengine.biz.third.api.persistence.check.impl;

import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.enums.SaveType;
import com.jdec.platform.dataengine.biz.third.api.persistence.DataPersistenceUtil;
import com.jdec.platform.dataengine.biz.third.api.persistence.check.BaseDataSaveCheck;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModulePermissionCheckImpl implements BaseDataSaveCheck {
    @Override
    public Boolean check(RecordSaveContext context) {
        if (context.getIsInsert()) {
            StringBuilder sb = new StringBuilder();
            for (String key : context.getRecord().keySet()) {
                if (!context.getSysModuleCompleteResp()
                        .getWriteFields()
                        .getOrDefault(context.getTableName(), new HashSet<>())
                        .contains(key)) {
                    if (sb.length() > 0) {
                        sb.append(";");
                        sb.append("您没有表【")
                                .append(context.getTableName())
                                .append("】的字段【")
                                .append(key)
                                .append("】的申请权限");
                    }
                }
            }
            if (sb.length() > 0) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_FIELD_APPLY_RIGHT_ERROR.getCode(),
                        sb.toString());
            }
            return true;
        } else {
            StringBuilder sb = new StringBuilder();
            for (String key : context.getRecord().keySet()) {
                Boolean isEqual =
                        DataPersistenceUtil.isEqualValue(
                                context.getRecord().get(key), context.getReadOutRecord().get(key));
                // 不相等，说明 要更新，检查是否有更新权限
                if (!isEqual
                        && !context.getSysModuleCompleteResp()
                                .getUpdateFields()
                                .getOrDefault(context.getTableName(), new HashSet<>())
                                .contains(key)) {
                    if (sb.length() > 0) {
                        sb.append(";");
                        sb.append("您没有表【")
                                .append(context.getTableName())
                                .append("】的字段【")
                                .append(key)
                                .append("】的编辑权限");
                    }
                }
            }
            if (sb.length() > 0) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_FIELD_APPLY_RIGHT_ERROR.getCode(),
                        sb.toString());
            }
            return true;
        }
    }

    @Override
    public Boolean isReferTo(SaveType type) {
        return true;
    }
}
