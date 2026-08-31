package com.jdec.platform.data.biz.service;

import com.jdec.platform.config.api.dto.common.ModuleTableHeaderDTO;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

/** 权限与表头过滤服务 负责字段读写权限判定与数据权限行级条件注入 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionFilterService {

    private final DataSourceResolver dataSourceResolver;

    /** 从 config_engine 库获取指定角色的真实字段权限规则列表 */
    public List<Map<String, Object>> getFieldPermissionsByRoleId(Long roleId) {
        if (roleId == null) {
            return new ArrayList<>();
        }
        try {
            DataSource ds = dataSourceResolver.resolve(DataSourceConstants.CONFIG_ENGINE);
            DSLContext dsl = DSL.using(ds, SQLDialect.MYSQL);

            return dsl.select(
                            DSL.field(DSL.name("role_id")).as("roleId"),
                            DSL.field(DSL.name("module_id")).as("moduleId"),
                            DSL.field(DSL.name("table_name")).as("tableName"),
                            DSL.field(DSL.name("column_name")).as("columnName"),
                            DSL.field(DSL.name("apply")).as("apply"),
                            DSL.field(DSL.name("view")).as("view"),
                            DSL.field(DSL.name("edit")).as("edit"))
                    .from(DSL.table(DSL.name("sys_role_module_field_permission")))
                    .where(
                            DSL.field(DSL.name("role_id"))
                                    .eq(roleId)
                                    .and(DSL.field(DSL.name("deleted")).eq(0)))
                    .fetchMaps();
        } catch (Exception ex) {
            log.error("从 config_engine 读取角色字段权限失败: roleId={}, err={}", roleId, ex.getMessage(), ex);
            return new ArrayList<>();
        }
    }

    /** 根据当前用户角色权限裁剪动态表头列表 */
    public List<ModuleTableHeaderDTO> filterReadableHeaders(SysModuleCompleteResp completeResp) {
        if (completeResp == null || completeResp.getModule() == null) {
            return new ArrayList<>();
        }
        List<ModuleTableHeaderDTO> originalHeaders = completeResp.getModuleHeaders();
        if (originalHeaders == null || originalHeaders.isEmpty()) {
            return new ArrayList<>();
        }

        // 当前版本默认全部可读（后续对接 SysRoleModuleFieldPermissionApi 权限校验）
        return new ArrayList<>(originalHeaders);
    }

    /** 根据当前用户角色权限裁剪动态字段字典列表 */
    public List<com.jdec.platform.config.api.dto.common.ModuleSimpleFieldDTO> filterReadableFields(
            SysModuleCompleteResp completeResp) {
        if (completeResp == null || completeResp.getSimpleFields() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(completeResp.getSimpleFields());
    }

    /** 校验待保存物理表的数据是否有越权写入字段 */
    public void validateWritableFields(Long moduleId, String tableName, List<String> fieldNames) {
        // 当前版本通过校验（后续对接 SysRoleModuleFieldPermissionApi 进行 writable / updatable 校验）
        log.debug(
                "校验写入字段权限: moduleId={}, tableName={}, fields={}", moduleId, tableName, fieldNames);
    }
}
