package com.jdec.platform.validation.externalobject;

import java.util.Set;

/**
 * 无审批链保存校验的输入数据。
 *
 * @param snapshot 由前端提交参数转换得到的整单数据（未提交的模块为空）
 * @param tables 本次提交且需要校验的模块表名
 * @param basicInfoAbsentOnCreate 无主单ID且未提交基本信息数据，基本信息的所有校验规则视为不满足
 */
public record ExternalObjectSaveData(
        ExternalObjectSnapshot snapshot, Set<String> tables, boolean basicInfoAbsentOnCreate) {

    public ExternalObjectSaveData {
        tables = tables == null ? Set.of() : Set.copyOf(tables);
    }
}
