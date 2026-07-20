package com.jdec.platform.common_business.biz.service.impl;

import com.jdec.platform.common_business.api.dto.common.ModuleField;
import com.jdec.platform.common_business.api.dto.common.ModuleInfo;
import com.jdec.platform.common_business.api.dto.common.ModuleTable;
import com.jdec.platform.common_business.biz.common.DataSourceConstants;
import com.jdec.platform.common_business.biz.service.SqlCreateService;
import com.jdec.platform.shared.datasource.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.STUDENT_MANAGE)
public class SqlCreateServiceImpl implements SqlCreateService {
    @Override
    public String dynamicQueryDetailData(
            ModuleInfo moduleInfo,
            List<ModuleTable> moduleTables,
            List<ModuleField> moduleFields,
            Long id) {
        StringBuilder builder = new StringBuilder();
        builder.append("SELECT ");
        builder.append(moduleFieldsSql(moduleInfo, moduleTables, moduleFields));
        builder.append(" FROM ");
        builder.append(moduleInfo.getPrimaryTable());
        builder.append(" ");
        builder.append(moduleTableJoinSql(moduleInfo, moduleTables, moduleFields));
        builder.append(moduleWhereSql(moduleInfo, "id"));
        builder.append(moduleGroupSql(moduleInfo, moduleTables, moduleFields));

        String sql = builder.toString();
        log.info("final SqlStr = {} ", sql);

        return sql;
    }

    @Override
    public String dynamicQueryListData(
            ModuleInfo moduleInfo, List<ModuleTable> moduleTables, List<ModuleField> moduleFields) {
        StringBuilder builder = new StringBuilder();
        builder.append("SELECT ");
        builder.append(moduleFieldsSql(moduleInfo, moduleTables, moduleFields));
        builder.append(" FROM ");
        builder.append(moduleInfo.getPrimaryTable());
        builder.append(" ");
        builder.append(moduleTableJoinSql(moduleInfo, moduleTables, moduleFields));
        builder.append(moduleGroupSql(moduleInfo, moduleTables, moduleFields));

        String sql = builder.toString();
        log.info("final SqlStr = {}", sql);
        return sql;
    }

    private String moduleWhereSql(ModuleInfo moduleInfo, String idKey) {
        return " WHERE " + moduleInfo.getPrimaryTable() + "." + idKey + " = {0}";
    }

    private String moduleGroupSql(
            ModuleInfo moduleInfo, List<ModuleTable> moduleTables, List<ModuleField> moduleFields) {
        return " GROUP BY " + moduleInfo.getPrimaryTable() + ".id ";
    }

    private String moduleTableJoinSql(
            ModuleInfo moduleInfo, List<ModuleTable> moduleTables, List<ModuleField> moduleFields) {
        StringBuilder moduleTableJoinSql = new StringBuilder();
        for (ModuleTable moduleTable : moduleTables) {
            moduleTableJoinSql.append("LEFT JOIN ");
            moduleTableJoinSql.append(moduleTable.getTableName());
            moduleTableJoinSql.append(" ON ");
            moduleTableJoinSql.append(moduleInfo.getPrimaryTable());
            moduleTableJoinSql.append(".");
            moduleTableJoinSql.append(moduleTable.getJoinLeftField());
            moduleTableJoinSql.append(" = ");
            moduleTableJoinSql.append(moduleTable.getTableName());
            moduleTableJoinSql.append(".");
            moduleTableJoinSql.append(moduleTable.getJoinRightField());
            moduleTableJoinSql.append(" ");
        }
        return moduleTableJoinSql.toString();
    }

    private String moduleFieldsSql(
            ModuleInfo moduleInfo, List<ModuleTable> moduleTables, List<ModuleField> moduleFields) {
        StringBuilder moduleFieldsSql = new StringBuilder();
        List<String> fieldItemSql = new ArrayList<>();
        if (!CollectionUtils.isEmpty(moduleFields)) {
            for (ModuleField moduleField : moduleFields) {
                StringBuilder moduleFieldItemSql = new StringBuilder();
                // 是否需要groupby标志位
                boolean needGroupBy = false;
                List<ModuleTable> matchedModuleTables = new ArrayList<>();

                // 简单查询SIMPLE，sourceMappings为空
                if (CollectionUtils.isEmpty(moduleField.getSourceMappings())) {
                    matchedModuleTables =
                            moduleTables.stream()
                                    .filter(
                                            e ->
                                                    moduleField
                                                            .getTableName()
                                                            .equals(e.getTableName()))
                                    .collect(Collectors.toList());
                } else {
                    // sourceMapping中涉及到的moduleTable
                    matchedModuleTables.addAll(
                            moduleTables.stream()
                                    .filter(
                                            e ->
                                                    moduleField.getSourceMappings().stream()
                                                            .anyMatch(
                                                                    item ->
                                                                            e.getTableName()
                                                                                    .equals(
                                                                                            item
                                                                                                    .entity())))
                                    .collect(Collectors.toList()));
                }
                // 主表字段或者跟主表关系都不为1:N(1:1或N:1)，此时不需要groupby
                if (!moduleInfo.getPrimaryTable().equals(moduleField.getTableName())
                        && (!CollectionUtils.isEmpty(matchedModuleTables)
                                && matchedModuleTables.stream()
                                        .anyMatch(e -> "1:N".equals(e.getRelationType())))) {
                    needGroupBy = true;
                }

                // 判断是否是主表显示字段，非主表字段要加GROUP_CONCAT
                if (needGroupBy) {
                    moduleFieldItemSql.append("GROUP_CONCAT(");
                }

                switch (moduleField.getFiledType()) {
                    case "SIMPLE":
                        moduleFieldItemSql.append(moduleField.getTableName());
                        moduleFieldItemSql.append(".");
                        moduleFieldItemSql.append(moduleField.getFieldCode());
                        break;

                    case "COMBINE":
                        moduleFieldItemSql.append(moduleField.getTransformer());
                        moduleFieldItemSql.append("(");
                        List<String> sourceMappingStr = new ArrayList<>();

                        for (ModuleField.SourceMapping sourceMapping :
                                moduleField.getSourceMappings()) {
                            sourceMappingStr.add(
                                    sourceMapping.entity() + "." + sourceMapping.field());
                        }

                        moduleFieldItemSql.append(StringUtils.join(sourceMappingStr, ","));

                        moduleFieldItemSql.append(")");
                }

                if (needGroupBy) {
                    moduleFieldItemSql.append(")");
                }

                moduleFieldItemSql.append(" AS ");
                moduleFieldItemSql.append(
                        "`" + moduleField.getTableName() + "." + moduleField.getFieldCode() + "`");

                fieldItemSql.add(moduleFieldItemSql.toString());
            }
        }

        moduleFieldsSql.append(StringUtils.join(fieldItemSql, " , "));
        return moduleFieldsSql.toString();
    }
}
