package com.jdec.platform.config.biz.audit.util;

import com.jdec.platform.config.biz.audit.dto.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 审计日志格式化工具
 *
 * <p>将 FieldDiff 列表转换为新的 ModuleAction 格式
 */
@Slf4j
@Component
public class AuditLogFormatter {

    /**
     * 构建新增操作的 ModuleAction
     *
     * @param moduleName 模块名称（如：角色配置、菜单配置）
     * @param recordName 记录名称（通过@AuditField标注的唯一标识）
     * @param diffs 字段差异列表
     * @return ModuleAction列表
     */
    public List<ModuleAction> buildCreateAction(
            String moduleName, String recordName, List<FieldDiff> diffs) {
        if (diffs == null || diffs.isEmpty()) {
            return Collections.emptyList();
        }

        // 构建字段变更列表
        List<ColumnChange> columns = new ArrayList<>();
        for (FieldDiff diff : diffs) {
            ColumnChange column =
                    ColumnChange.builder()
                            .name(diff.getFieldName())
                            .old("") // 新增操作，old为空
                            .newer(formatValue(diff.getNewer()))
                            .field(diff.getFieldCode())
                            .build();
            columns.add(column);
        }

        // 构建记录变更
        RecordChange recordChange =
                RecordChange.builder()
                        .name(recordName != null ? recordName : "")
                        .columns(columns)
                        .build();

        // 构建操作详情（新增操作，放在 i 字段）
        ActionDetail actionDetail =
                ActionDetail.builder()
                        .i(Collections.singletonList(recordChange))
                        .u(null)
                        .d(null)
                        .build();

        // 构建模块操作
        ModuleAction moduleAction =
                ModuleAction.builder()
                        .name(moduleName)
                        .mId("") // 暂时为空
                        .actions(actionDetail)
                        .build();

        return Collections.singletonList(moduleAction);
    }

    /**
     * 构建修改操作的 ModuleAction
     *
     * @param moduleName 模块名称
     * @param recordName 记录名称
     * @param diffs 字段差异列表
     * @return ModuleAction列表
     */
    public List<ModuleAction> buildUpdateAction(
            String moduleName, String recordName, List<FieldDiff> diffs) {
        if (diffs == null || diffs.isEmpty()) {
            return Collections.emptyList();
        }

        // 构建字段变更列表
        List<ColumnChange> columns = new ArrayList<>();
        for (FieldDiff diff : diffs) {
            ColumnChange column =
                    ColumnChange.builder()
                            .name(diff.getFieldName())
                            .old(formatValue(diff.getOldValue()))
                            .newer(formatValue(diff.getNewer()))
                            .field(diff.getFieldCode())
                            .build();
            columns.add(column);
        }

        // 构建记录变更
        RecordChange recordChange =
                RecordChange.builder()
                        .name(recordName != null ? recordName : "")
                        .columns(columns)
                        .build();

        // 构建操作详情（修改操作，放在 u 字段）
        ActionDetail actionDetail =
                ActionDetail.builder()
                        .i(null)
                        .u(Collections.singletonList(recordChange))
                        .d(null)
                        .build();

        // 构建模块操作
        ModuleAction moduleAction =
                ModuleAction.builder()
                        .name(moduleName)
                        .mId("") // 暂时为空
                        .actions(actionDetail)
                        .build();

        return Collections.singletonList(moduleAction);
    }

    /**
     * 构建删除操作的 ModuleAction
     *
     * @param moduleName 模块名称
     * @param recordName 记录名称
     * @param diffs 字段差异列表
     * @return ModuleAction列表
     */
    public List<ModuleAction> buildDeleteAction(
            String moduleName, String recordName, List<FieldDiff> diffs) {
        // 构建记录变更（删除操作只包含 name，不包含 columns）
        RecordChange recordChange =
                RecordChange.builder()
                        .name(recordName != null ? recordName : "")
                        .columns(null) // 删除操作不需要 columns
                        .build();

        // 构建操作详情（删除操作，放在 d 字段）
        ActionDetail actionDetail =
                ActionDetail.builder()
                        .i(null)
                        .u(null)
                        .d(Collections.singletonList(recordChange))
                        .build();

        // 构建模块操作
        ModuleAction moduleAction =
                ModuleAction.builder()
                        .name(moduleName)
                        .mId("") // 暂时为空
                        .actions(actionDetail)
                        .build();

        return Collections.singletonList(moduleAction);
    }

    /**
     * 格式化值为字符串
     *
     * @param value 原始值
     * @return 格式化后的字符串
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString();
    }
}
