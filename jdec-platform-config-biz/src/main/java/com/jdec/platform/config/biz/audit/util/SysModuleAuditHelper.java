package com.jdec.platform.config.biz.audit.util;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.api.dto.common.ModuleSimpleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleStatusDTO;
import com.jdec.platform.config.api.dto.common.ModuleTableDTO;
import com.jdec.platform.config.api.dto.common.ModuleTableHeaderDTO;
import com.jdec.platform.config.api.dto.request.MoveModuleReq;
import com.jdec.platform.config.api.dto.request.SaveModuleReq;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.config.biz.entity.SysField;
import com.jdec.platform.config.biz.entity.SysStatus;
import com.jdec.platform.config.biz.mapper.SysFieldMapper;
import com.jdec.platform.config.biz.mapper.SysStatusMapper;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 模块配置变更审计日志树构造器
 *
 * <p>统一使用 SaveModuleReq 进行新旧比对。 规范：d(删除) 节点只保留 name，消灭硬编码文本。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysModuleAuditHelper {

    private final SysStatusMapper sysStatusMapper;
    private final SysFieldMapper sysFieldMapper;
    private final DataSourceResolver dataSourceResolver;

    /** 转换工具：将数据库查询出的 SysModuleCompleteResp 转为标准的 SaveModuleReq */
    public SaveModuleReq convertRespToReq(SysModuleCompleteResp resp) {
        if (resp == null) {
            return null;
        }
        SaveModuleReq req = new SaveModuleReq();
        if (resp.getModule() != null) {
            SaveModuleReq.SaveSysModuleReq moduleReq = new SaveModuleReq.SaveSysModuleReq();
            moduleReq.setId(resp.getModule().getId());
            moduleReq.setModuleCode(resp.getModule().getModuleCode());
            moduleReq.setModuleName(resp.getModule().getModuleName());
            moduleReq.setModuleDesc(resp.getModule().getModuleDesc());
            moduleReq.setParentId(resp.getModule().getParentId());
            moduleReq.setDetailModuleId(resp.getModule().getDetailModuleId());
            moduleReq.setPrimaryTable(resp.getModule().getPrimaryTable());
            moduleReq.setModuleType(
                    resp.getModule().getModuleType() != null
                            ? resp.getModule().getModuleType().name()
                            : null);
            moduleReq.setApprovalRequired(resp.getModule().getApprovalRequired());
            moduleReq.setBizDefFlag(resp.getModule().getBizDefFlag());
            moduleReq.setCategory(resp.getModule().getCategory());
            moduleReq.setSortOrder(resp.getModule().getSortOrder());
            moduleReq.setTableHeader(resp.getModule().getTableHeader());
            moduleReq.setRelateSearchField(resp.getModule().getRelateSearchField());
            req.setModule(moduleReq);
        }
        req.setModuleTables(resp.getModuleTables());
        req.setSimpleFields(resp.getSimpleFields());
        //        req.setCombineFields(resp.getCombineFields());
        req.setModuleStatuses(resp.getModuleStatuses());
        return req;
    }

    private String getCategoryName(Integer category) {
        return (category != null && category == 2) ? "系统模块" : "业务模块";
    }

    /** 审计预查询 Lookup 缓存上下文 */
    private static class AuditLookupContext {
        final Map<String, String> tableCommentMap = new HashMap<>();
        final Map<String, String> fieldCommentMap = new HashMap<>();
        final Map<Long, String> statusTitleMap = new HashMap<>();
    }

    private AuditLookupContext initLookupContext(
            String projectNo, Long subjectId, SaveModuleReq oldReq, SaveModuleReq newReq) {
        AuditLookupContext ctx = new AuditLookupContext();
        String projNo = StringUtils.hasText(projectNo) ? projectNo : AppContext.getProjectNo();
        Long subjId = subjectId != null ? subjectId : AppContext.getSubjectId();

        Set<String> tableNames = new HashSet<>();
        Set<Long> statusIds = new HashSet<>();

        List<SaveModuleReq> reqs = new ArrayList<>();
        if (oldReq != null) reqs.add(oldReq);
        if (newReq != null) reqs.add(newReq);

        for (SaveModuleReq req : reqs) {
            if (req.getModule() != null) {
                if (StringUtils.hasText(req.getModule().getPrimaryTable())) {
                    tableNames.add(req.getModule().getPrimaryTable());
                }
            }
            if (req.getModuleTables() != null) {
                for (ModuleTableDTO t : req.getModuleTables()) {
                    if (StringUtils.hasText(t.getTableName())) {
                        tableNames.add(t.getTableName());
                        if (StringUtils.hasText(t.getTableDesc())) {
                            ctx.tableCommentMap.put(t.getTableName(), t.getTableDesc());
                        }
                    }
                }
            }
            if (req.getModuleStatuses() != null) {
                for (ModuleStatusDTO s : req.getModuleStatuses()) {
                    if (s.getStatusId() != null) statusIds.add(s.getStatusId());
                    if (s.getStatusPid() != null) statusIds.add(s.getStatusPid());
                }
            }
        }

        // 1. 批量查询缺失的表注释 (INFORMATION_SCHEMA.TABLES)
        Set<String> missingTableComments =
                tableNames.stream()
                        .filter(t -> !ctx.tableCommentMap.containsKey(t))
                        .collect(Collectors.toSet());
        if (!missingTableComments.isEmpty() && StringUtils.hasText(projNo)) {
            try {
                JdbcTemplate jdbcTemplate = dataSourceResolver.getJdbcTemplate(projNo);
                String inSql =
                        missingTableComments.stream()
                                .map(t -> "?")
                                .collect(Collectors.joining(","));
                String sql =
                        String.format(
                                "SELECT TABLE_NAME, TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME IN (%s)",
                                inSql);
                List<Object> params = new ArrayList<>();
                params.add(projNo);
                params.addAll(missingTableComments);

                jdbcTemplate.query(
                        sql,
                        rs -> {
                            String tName = rs.getString("TABLE_NAME");
                            String tComment = rs.getString("TABLE_COMMENT");
                            if (StringUtils.hasText(tName) && StringUtils.hasText(tComment)) {
                                ctx.tableCommentMap.put(tName, tComment);
                            }
                        },
                        params.toArray());
            } catch (Exception e) {
                log.warn(
                        "批量查询 INFORMATION_SCHEMA.TABLES 表注释失败, tableNames: {}",
                        missingTableComments,
                        e);
            }
        }

        // 2. 批量查询 sys_field 自定义显示名称
        if (!tableNames.isEmpty() && StringUtils.hasText(projNo) && subjId != null) {
            try {
                List<SysField> sysFields =
                        sysFieldMapper.selectList(
                                new LambdaQueryWrapper<SysField>()
                                        .eq(SysField::getProjectNo, projNo)
                                        .eq(SysField::getSubjectId, subjId)
                                        .in(SysField::getTableName, tableNames)
                                        .eq(SysField::getDeleted, 0));
                for (SysField sf : sysFields) {
                    if (StringUtils.hasText(sf.getTableName())
                            && StringUtils.hasText(sf.getColumnName())
                            && StringUtils.hasText(sf.getDisplayName())) {
                        ctx.fieldCommentMap.put(
                                sf.getTableName() + ":" + sf.getColumnName(), sf.getDisplayName());
                    }
                }
            } catch (Exception e) {
                log.warn("批量查询 sys_field 失败, tableNames: {}", tableNames, e);
            }
        }

        // 3. 批量查询 INFORMATION_SCHEMA.COLUMNS 列注释 (补全 sys_field 未配置的列)
        if (!tableNames.isEmpty() && StringUtils.hasText(projNo)) {
            try {
                JdbcTemplate jdbcTemplate = dataSourceResolver.getJdbcTemplate(projNo);
                String inSql = tableNames.stream().map(t -> "?").collect(Collectors.joining(","));
                String sql =
                        String.format(
                                "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_COMMENT FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME IN (%s)",
                                inSql);
                List<Object> params = new ArrayList<>();
                params.add(projNo);
                params.addAll(tableNames);

                jdbcTemplate.query(
                        sql,
                        rs -> {
                            String tName = rs.getString("TABLE_NAME");
                            String cName = rs.getString("COLUMN_NAME");
                            String cComment = rs.getString("COLUMN_COMMENT");
                            if (StringUtils.hasText(tName)
                                    && StringUtils.hasText(cName)
                                    && StringUtils.hasText(cComment)) {
                                String key = tName + ":" + cName;
                                ctx.fieldCommentMap.putIfAbsent(key, cComment);
                            }
                        },
                        params.toArray());
            } catch (Exception e) {
                log.warn("批量查询 INFORMATION_SCHEMA.COLUMNS 列注释失败, tableNames: {}", tableNames, e);
            }
        }

        // 4. 批量查询状态名称 (sys_status)
        if (!statusIds.isEmpty()) {
            try {
                List<SysStatus> statusList =
                        sysStatusMapper.selectList(
                                new LambdaQueryWrapper<SysStatus>()
                                        .in(SysStatus::getId, statusIds));
                for (SysStatus st : statusList) {
                    if (st.getId() != null && StringUtils.hasText(st.getTitle())) {
                        ctx.statusTitleMap.put(st.getId(), st.getTitle());
                    }
                }
            } catch (Exception e) {
                log.warn("批量查询 sys_status 失败, statusIds: {}", statusIds, e);
            }
        }

        return ctx;
    }

    /** 1. 顶层入口：构建保存/编辑模块的树状 logRemark JSON */
    public String buildSaveModuleRemark(
            String projectNo, Long subjectId, SaveModuleReq oldReq, SaveModuleReq newReq) {
        Map<String, Object> root = new HashMap<>();
        List<Map<String, Object>> change = new ArrayList<>();
        Map<String, Object> businessModule = new HashMap<>();

        AuditLookupContext ctx = initLookupContext(projectNo, subjectId, oldReq, newReq);

        Integer category = null;
        if (newReq != null
                && newReq.getModule() != null
                && newReq.getModule().getCategory() != null) {
            category = newReq.getModule().getCategory();
        } else if (oldReq != null && oldReq.getModule() != null) {
            category = oldReq.getModule().getCategory();
        }
        businessModule.put("name", getCategoryName(category));

        Map<String, Object> actions = new HashMap<>();
        if (oldReq == null) {
            // 全局新增模块 -> i
            List<Map<String, Object>> iList = new ArrayList<>();
            iList.add(buildCreateModuleNode(ctx, projectNo, subjectId, newReq));
            actions.put("i", iList);
        } else {
            // 全局编辑模块 -> u
            List<Map<String, Object>> uList = new ArrayList<>();
            uList.add(buildUpdateModuleNode(ctx, projectNo, subjectId, oldReq, newReq));
            actions.put("u", uList);
        }

        businessModule.put("actions", actions);
        change.add(businessModule);
        root.put("change", change);

        String jsonStr = JSONUtil.toJsonStr(root);
        log.info("生成模块保存/编辑审计 logRemark JSON: {}", jsonStr);
        return jsonStr;
    }

    /** 2. 顶层入口：构建删除整个模块的 logRemark JSON（严格规范：d 只保留 name） */
    public String buildDeleteModuleRemark(SysModuleCompleteResp moduleData) {
        Map<String, Object> root = new HashMap<>();
        List<Map<String, Object>> change = new ArrayList<>();
        Map<String, Object> businessModule = new HashMap<>();

        Integer category =
                moduleData != null && moduleData.getModule() != null
                        ? moduleData.getModule().getCategory()
                        : null;
        businessModule.put("name", getCategoryName(category));

        Map<String, Object> actions = new HashMap<>();
        List<Map<String, Object>> dList = new ArrayList<>();
        Map<String, Object> delItem = new HashMap<>();
        delItem.put(
                "name",
                moduleData != null && moduleData.getModule() != null
                        ? moduleData.getModule().getModuleName()
                        : "已知模块");
        dList.add(delItem);
        actions.put("d", dList);

        businessModule.put("actions", actions);
        change.add(businessModule);
        root.put("change", change);

        String jsonStr = JSONUtil.toJsonStr(root);
        log.info("生成模块删除审计 logRemark JSON: {}", jsonStr);
        return jsonStr;
    }

    /** 3. 顶层入口：构建移动模块位置的 logRemark JSON */
    public String buildMoveModuleRemark(
            String moduleName, String oldParentName, String newParentName, MoveModuleReq request) {
        Map<String, Object> root = new HashMap<>();
        List<Map<String, Object>> change = new ArrayList<>();
        Map<String, Object> businessModule = new HashMap<>();
        businessModule.put("name", "业务模块");

        Map<String, Object> actions = new HashMap<>();
        List<Map<String, Object>> uList = new ArrayList<>();

        Map<String, Object> moveItem = new HashMap<>();
        moveItem.put("name", moduleName);

        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(createColumn("parent_id", "父级模块", oldParentName, newParentName));
        columns.add(
                createColumn(
                        "sort_order", "排序位置", "", String.valueOf(request.getTargetSortOrder())));

        moveItem.put("columns", columns);
        uList.add(moveItem);
        actions.put("u", uList);

        businessModule.put("actions", actions);
        change.add(businessModule);
        root.put("change", change);

        String jsonStr = JSONUtil.toJsonStr(root);
        log.info("生成模块移动审计 logRemark JSON: {}", jsonStr);
        return jsonStr;
    }

    // =========================================================================
    // 第一部分：新增模块场景节点构建 (CREATE / i)
    // =========================================================================

    private Map<String, Object> buildCreateModuleNode(
            AuditLookupContext ctx, String projectNo, Long subjectId, SaveModuleReq newReq) {
        Map<String, Object> node = new HashMap<>();
        String moduleName = newReq.getModule() != null ? newReq.getModule().getModuleName() : "";
        node.put("name", moduleName);

        List<Map<String, Object>> columns = new ArrayList<>();
        if (newReq.getModule() != null) {
            SaveModuleReq.SaveSysModuleReq m = newReq.getModule();
            if (StringUtils.hasText(m.getModuleName())) {
                columns.add(createColumnNullOld("module_name", "模块名称", m.getModuleName()));
            }
            if (StringUtils.hasText(m.getModuleCode())) {
                columns.add(createColumnNullOld("module_code", "模块编码", m.getModuleCode()));
            }
            if (StringUtils.hasText(m.getPrimaryTable())) {
                columns.add(createColumnNullOld("primary_table", "主表名称", m.getPrimaryTable()));
            }
            if (StringUtils.hasText(m.getModuleType())) {
                columns.add(createColumnNullOld("module_type", "模块类型", m.getModuleType()));
            }
            if (m.getApprovalRequired() != null) {
                columns.add(
                        createColumnNullOld(
                                "approval_required",
                                "是否启用审批",
                                String.valueOf(m.getApprovalRequired())));
            }
            if (StringUtils.hasText(m.getRelateSearchField())) {
                columns.add(
                        createColumnNullOld(
                                "relate_search_field", "外键关联字段", m.getRelateSearchField()));
            }
        }

        if (newReq.getModuleTables() != null && !newReq.getModuleTables().isEmpty()) {
            String subTableNames =
                    newReq.getModuleTables().stream()
                            .map(t -> (resolveTableDisplayName(ctx, t)))
                            .collect(Collectors.joining(", "));
            columns.add(createColumnNullOld("module_tables", "模块子表", subTableNames));
        }
        node.put("columns", columns);

        List<Map<String, Object>> children = new ArrayList<>();
        children.add(buildSubTableChildNodeForCreate(ctx, projectNo, subjectId, newReq));
        children.add(buildTableFieldConfigChildNodeForCreate(ctx, projectNo, subjectId, newReq));

        String moduleType = newReq.getModule() != null ? newReq.getModule().getModuleType() : null;
        if (!"DETAIL".equalsIgnoreCase(moduleType)) {
            children.add(buildColumnConfigChildNodeForCreate(ctx, newReq));
        }
        if ("DETAIL".equalsIgnoreCase(moduleType)) {
            children.add(buildStatusConfigChildNodeForCreate(ctx, newReq));
        }

        node.put("children", children);
        return node;
    }

    private Map<String, Object> buildSubTableChildNodeForCreate(
            AuditLookupContext ctx, String projectNo, Long subjectId, SaveModuleReq newReq) {
        Map<String, Object> childNode = new HashMap<>();
        childNode.put("name", "模块子表关联");

        Map<String, Object> actions = new HashMap<>();
        List<Map<String, Object>> iList = new ArrayList<>();

        if (newReq.getModuleTables() != null) {
            String primaryTable =
                    newReq.getModule() != null ? newReq.getModule().getPrimaryTable() : "";
            for (ModuleTableDTO table : newReq.getModuleTables()) {
                String tableName = table.getTableName() != null ? table.getTableName() : "";
                String tDisplayName = resolveTableDisplayName(ctx, table);

                Map<String, Object> tItem = new HashMap<>();
                tItem.put("name", tDisplayName);
                List<Map<String, Object>> cols = new ArrayList<>();
                cols.add(createColumnNullOld("sub_table_name", "模块子表名称", tDisplayName));
                if (StringUtils.hasText(primaryTable)) {
                    cols.add(
                            createColumnNullOld(
                                    "primary_table",
                                    "模块主表名称",
                                    resolveTableDisplayName(
                                            ctx,
                                            ModuleTableDTO.builder()
                                                    .tableName(primaryTable)
                                                    .build())));
                }
                if (StringUtils.hasText(table.getJoinLeftField())) {
                    cols.add(
                            createColumnNullOld(
                                    "join_left_field",
                                    "主表字段",
                                    resolveTableFieldDisplayName(
                                            ctx, primaryTable, table.getJoinLeftField())));
                }
                if (StringUtils.hasText(table.getJoinRightField())) {
                    cols.add(
                            createColumnNullOld(
                                    "join_right_field",
                                    "子表字段",
                                    resolveTableFieldDisplayName(
                                            ctx, table.getTableName(), table.getJoinRightField())));
                }
                if (StringUtils.hasText(table.getRelationType())) {
                    cols.add(createColumnNullOld("relation_type", "关联关系", table.getRelationType()));
                }
                if (table.getReadOnly() != null) {
                    cols.add(
                            createColumnNullOld(
                                    "read_only", "子表是否只读", table.getReadOnly() == 1 ? "是" : "否"));
                }
                if (table.getSortOrder() != null) {
                    cols.add(
                            createColumnNullOld(
                                    "sort_order", "子表排序", String.valueOf(table.getSortOrder())));
                }
                tItem.put("columns", cols);
                iList.add(tItem);
            }
        }

        actions.put("i", iList);
        childNode.put("actions", actions);
        return childNode;
    }

    private Map<String, Object> buildTableFieldConfigChildNodeForCreate(
            AuditLookupContext ctx, String projectNo, Long subjectId, SaveModuleReq newReq) {
        Map<String, Object> childNode = new HashMap<>();
        childNode.put("name", "表字段配置");

        Map<String, Object> actions = new HashMap<>();
        List<Map<String, Object>> iList = new ArrayList<>();

        // 1. 基础字段新增 (i): 基础字段只留 new 字段，组合所有新增的基础字段名称
        if (newReq.getSimpleFields() != null && !newReq.getSimpleFields().isEmpty()) {
            List<String> addNames = new ArrayList<>();
            for (ModuleSimpleFieldDTO s : newReq.getSimpleFields()) {
                String colDisplayName =
                        resolveFieldDisplayName(
                                ctx, s.getTableName(), s.getColumnName(), s.getDisplayName());
                addNames.add(colDisplayName);
            }
            Map<String, Object> simpleItem = new HashMap<>();
            simpleItem.put("name", "基础字段");
            List<Map<String, Object>> cols = new ArrayList<>();
            cols.add(createColumnNullOld(null, null, String.join(", ", addNames)));
            simpleItem.put("columns", cols);
            iList.add(simpleItem);
        }

        //        // 2. 组合字段新增 (i): 组合字段保留完整组合字段信息
        //        if (newReq.getCombineFields() != null && !newReq.getCombineFields().isEmpty()) {
        //            for (ModuleCombineFieldDTO combine : newReq.getCombineFields()) {
        //                String combineDisplayName =
        //                        StringUtils.hasText(combine.getDisplayName())
        //                                ? combine.getDisplayName()
        //                                : combine.getLogicalField();
        //                Map<String, Object> cItem = buildCombineFieldItem(combine,
        // combineDisplayName);
        //                cItem.put("name", "组合字段");
        //                iList.add(cItem);
        //            }
        //        }

        if (!iList.isEmpty()) {
            actions.put("i", iList);
        }
        childNode.put("actions", actions);
        return childNode;
    }

    private Map<String, Object> buildColumnConfigChildNodeForCreate(
            AuditLookupContext ctx, SaveModuleReq newReq) {
        Map<String, Object> childNode = new HashMap<>();
        childNode.put("name", "模块列配置");
        Map<String, Object> actions = new HashMap<>();
        List<Map<String, Object>> iList = new ArrayList<>();

        if (newReq.getModule() != null && newReq.getModule().getTableHeader() != null) {
            for (ModuleTableHeaderDTO header : newReq.getModule().getTableHeader()) {
                iList.add(buildTableHeaderItem(ctx, header));
            }
        }

        actions.put("i", iList);
        childNode.put("actions", actions);
        return childNode;
    }

    private Map<String, Object> buildStatusConfigChildNodeForCreate(
            AuditLookupContext ctx, SaveModuleReq newReq) {
        Map<String, Object> childNode = new HashMap<>();
        childNode.put("name", "模块状态配置");
        Map<String, Object> actions = new HashMap<>();
        List<Map<String, Object>> iList = new ArrayList<>();

        if (newReq.getModuleStatuses() != null) {
            for (ModuleStatusDTO status : newReq.getModuleStatuses()) {
                if (status.getStatusId() != null) {
                    String idTitle = resolveStatusTitle(ctx, status.getStatusId());
                    String pidTitle =
                            status.getStatusPid() != null && status.getStatusPid() != 0L
                                    ? resolveStatusTitle(ctx, status.getStatusPid())
                                    : idTitle;

                    Map<String, Object> item = new HashMap<>();
                    item.put("name", idTitle);
                    List<Map<String, Object>> cols = new ArrayList<>();
                    Map<String, Object> colMap = new HashMap<>();
                    colMap.put("newer", pidTitle + "—" + idTitle);
                    cols.add(colMap);
                    item.put("columns", cols);
                    iList.add(item);
                }
            }
        }

        actions.put("i", iList);
        childNode.put("actions", actions);
        return childNode;
    }

    // =========================================================================
    // 第二部分：编辑模块场景节点构建 (UPDATE / u)
    // =========================================================================

    private Map<String, Object> buildUpdateModuleNode(
            AuditLookupContext ctx,
            String projectNo,
            Long subjectId,
            SaveModuleReq oldReq,
            SaveModuleReq newReq) {
        Map<String, Object> node = new HashMap<>();
        String moduleName = newReq.getModule() != null ? newReq.getModule().getModuleName() : "";
        node.put("name", moduleName);

        List<Map<String, Object>> columns = new ArrayList<>();
        if (oldReq.getModule() != null && newReq.getModule() != null) {
            SaveModuleReq.SaveSysModuleReq oldM = oldReq.getModule();
            SaveModuleReq.SaveSysModuleReq newM = newReq.getModule();

            if (!Objects.equals(oldM.getId(), newM.getId())) {
                columns.add(
                        createColumn(
                                "id",
                                "模块ID",
                                oldM.getId() != null ? String.valueOf(oldM.getId()) : "",
                                newM.getId() != null ? String.valueOf(newM.getId()) : ""));
            }
            if (!Objects.equals(oldM.getModuleCode(), newM.getModuleCode())) {
                columns.add(
                        createColumn(
                                "module_code",
                                "模块唯一编码",
                                oldM.getModuleCode(),
                                newM.getModuleCode()));
            }
            if (!Objects.equals(oldM.getModuleName(), newM.getModuleName())) {
                columns.add(
                        createColumn(
                                "module_name", "模块名称", oldM.getModuleName(), newM.getModuleName()));
            }
            if (!Objects.equals(oldM.getModuleDesc(), newM.getModuleDesc())) {
                columns.add(
                        createColumn(
                                "module_desc", "模块描述", oldM.getModuleDesc(), newM.getModuleDesc()));
            }
            if (!Objects.equals(oldM.getParentId(), newM.getParentId())) {
                columns.add(
                        createColumn(
                                "parent_id",
                                "父模块ID",
                                oldM.getParentId() != null
                                        ? String.valueOf(oldM.getParentId())
                                        : "",
                                newM.getParentId() != null
                                        ? String.valueOf(newM.getParentId())
                                        : ""));
            }
            if (!Objects.equals(oldM.getDetailModuleId(), newM.getDetailModuleId())) {
                columns.add(
                        createColumn(
                                "detail_module_id",
                                "详情模块ID",
                                oldM.getDetailModuleId() != null
                                        ? String.valueOf(oldM.getDetailModuleId())
                                        : "",
                                newM.getDetailModuleId() != null
                                        ? String.valueOf(newM.getDetailModuleId())
                                        : ""));
            }
            if (!Objects.equals(oldM.getPrimaryTable(), newM.getPrimaryTable())) {
                columns.add(
                        createColumn(
                                "primary_table",
                                "主表名称",
                                oldM.getPrimaryTable(),
                                newM.getPrimaryTable()));
            }
            if (!Objects.equals(oldM.getModuleType(), newM.getModuleType())) {
                columns.add(
                        createColumn(
                                "module_type", "模块类型", oldM.getModuleType(), newM.getModuleType()));
            }
            if (!Objects.equals(oldM.getApprovalRequired(), newM.getApprovalRequired())) {
                columns.add(
                        createColumn(
                                "approval_required",
                                "是否启用审批",
                                oldM.getApprovalRequired() != null
                                        ? String.valueOf(oldM.getApprovalRequired())
                                        : "",
                                newM.getApprovalRequired() != null
                                        ? String.valueOf(newM.getApprovalRequired())
                                        : ""));
            }
            if (!Objects.equals(oldM.getBizDefFlag(), newM.getBizDefFlag())) {
                columns.add(
                        createColumn(
                                "biz_def_flag",
                                "是否模块业务定义",
                                oldM.getBizDefFlag() != null
                                        ? String.valueOf(oldM.getBizDefFlag())
                                        : "",
                                newM.getBizDefFlag() != null
                                        ? String.valueOf(newM.getBizDefFlag())
                                        : ""));
            }
            if (!Objects.equals(oldM.getCategory(), newM.getCategory())) {
                columns.add(
                        createColumn(
                                "category",
                                "模块类别",
                                oldM.getCategory() != null
                                        ? getCategoryName(oldM.getCategory())
                                        : "",
                                newM.getCategory() != null
                                        ? getCategoryName(newM.getCategory())
                                        : ""));
            }
            if (!Objects.equals(oldM.getSortOrder(), newM.getSortOrder())) {
                columns.add(
                        createColumn(
                                "sort_order",
                                "排序顺序",
                                oldM.getSortOrder() != null
                                        ? String.valueOf(oldM.getSortOrder())
                                        : "",
                                newM.getSortOrder() != null
                                        ? String.valueOf(newM.getSortOrder())
                                        : ""));
            }
            if (!Objects.equals(oldM.getSourceSubjects(), newM.getSourceSubjects())) {
                columns.add(
                        createColumn(
                                "source_subjects",
                                "数据来源主体",
                                oldM.getSourceSubjects() != null
                                        ? oldM.getSourceSubjects().toString()
                                        : "",
                                newM.getSourceSubjects() != null
                                        ? newM.getSourceSubjects().toString()
                                        : ""));
            }
            if (!Objects.equals(oldM.getRelateSearchField(), newM.getRelateSearchField())) {
                columns.add(
                        createColumn(
                                "relate_search_field",
                                "主表外键关联字段",
                                oldM.getRelateSearchField(),
                                newM.getRelateSearchField()));
            }
        }
        node.put("columns", columns);

        List<Map<String, Object>> children = new ArrayList<>();
        children.add(buildSubTableChildNodeForUpdate(ctx, projectNo, subjectId, oldReq, newReq));
        children.add(
                buildTableFieldConfigChildNodeForUpdate(ctx, projectNo, subjectId, oldReq, newReq));

        String moduleType = newReq.getModule() != null ? newReq.getModule().getModuleType() : null;
        if (!StringUtils.hasText(moduleType) && oldReq.getModule() != null) {
            moduleType = oldReq.getModule().getModuleType();
        }

        if (!"DETAIL".equalsIgnoreCase(moduleType)) {
            children.add(buildColumnConfigChildNodeForUpdate(ctx, oldReq, newReq));
        }
        if ("DETAIL".equalsIgnoreCase(moduleType)) {
            children.add(buildStatusConfigChildNodeForUpdate(ctx, oldReq, newReq));
        }

        node.put("children", children);
        return node;
    }

    private Map<String, Object> buildSubTableChildNodeForUpdate(
            AuditLookupContext ctx,
            String projectNo,
            Long subjectId,
            SaveModuleReq oldReq,
            SaveModuleReq newReq) {
        Map<String, Object> childNode = new HashMap<>();
        childNode.put("name", "模块子表关联");
        Map<String, Object> actions = new HashMap<>();

        List<ModuleTableDTO> oldTables =
                oldReq.getModuleTables() != null
                        ? oldReq.getModuleTables()
                        : Collections.emptyList();
        List<ModuleTableDTO> newTables =
                newReq.getModuleTables() != null
                        ? newReq.getModuleTables()
                        : Collections.emptyList();

        Map<String, ModuleTableDTO> oldMap =
                oldTables.stream()
                        .filter(t -> t.getTableName() != null)
                        .collect(
                                Collectors.toMap(
                                        ModuleTableDTO::getTableName,
                                        Function.identity(),
                                        (e1, e2) -> e1));
        Map<String, ModuleTableDTO> newMap =
                newTables.stream()
                        .filter(t -> t.getTableName() != null)
                        .collect(
                                Collectors.toMap(
                                        ModuleTableDTO::getTableName,
                                        Function.identity(),
                                        (e1, e2) -> e1));

        List<Map<String, Object>> uList = new ArrayList<>();
        List<Map<String, Object>> iList = new ArrayList<>();
        List<Map<String, Object>> dList = new ArrayList<>();

        for (Map.Entry<String, ModuleTableDTO> entry : newMap.entrySet()) {
            String tableName = entry.getKey();
            ModuleTableDTO newT = entry.getValue();
            String tDisplayName = resolveTableDisplayName(ctx, newT);
            String primaryTable =
                    newReq.getModule() != null ? newReq.getModule().getPrimaryTable() : "";
            if (!StringUtils.hasText(primaryTable) && oldReq.getModule() != null) {
                primaryTable = oldReq.getModule().getPrimaryTable();
            }

            if (!oldMap.containsKey(tableName)) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", tDisplayName);
                List<Map<String, Object>> cols = new ArrayList<>();
                cols.add(createColumnNullOld("sub_table_name", "模块子表名称", tDisplayName));
                if (StringUtils.hasText(primaryTable)) {
                    cols.add(
                            createColumnNullOld(
                                    "primary_table",
                                    "模块主表名称",
                                    resolveTableDisplayName(
                                            ctx,
                                            ModuleTableDTO.builder()
                                                    .tableName(primaryTable)
                                                    .build())));
                }
                if (StringUtils.hasText(newT.getJoinLeftField())) {
                    cols.add(
                            createColumnNullOld(
                                    "join_left_field",
                                    "主表字段",
                                    resolveTableFieldDisplayName(
                                            ctx, primaryTable, newT.getJoinLeftField())));
                }
                if (StringUtils.hasText(newT.getJoinRightField())) {
                    cols.add(
                            createColumnNullOld(
                                    "join_right_field",
                                    "子表字段",
                                    resolveTableFieldDisplayName(
                                            ctx, tableName, newT.getJoinRightField())));
                }
                if (StringUtils.hasText(newT.getRelationType())) {
                    cols.add(createColumnNullOld("relation_type", "关联关系", newT.getRelationType()));
                }
                if (newT.getReadOnly() != null) {
                    cols.add(
                            createColumnNullOld(
                                    "read_only", "子表是否只读", newT.getReadOnly() == 1 ? "是" : "否"));
                }
                if (newT.getSortOrder() != null) {
                    cols.add(
                            createColumnNullOld(
                                    "sort_order", "子表排序", String.valueOf(newT.getSortOrder())));
                }
                item.put("columns", cols);
                iList.add(item);
            } else {
                ModuleTableDTO oldT = oldMap.get(tableName);
                List<Map<String, Object>> cols = new ArrayList<>();
                if (!Objects.equals(oldT.getJoinLeftField(), newT.getJoinLeftField())) {
                    cols.add(
                            createColumn(
                                    "join_left_field",
                                    "主表字段",
                                    resolveTableFieldDisplayName(
                                            ctx, primaryTable, oldT.getJoinLeftField()),
                                    resolveTableFieldDisplayName(
                                            ctx, primaryTable, newT.getJoinLeftField())));
                }
                if (!Objects.equals(oldT.getJoinRightField(), newT.getJoinRightField())) {
                    cols.add(
                            createColumn(
                                    "join_right_field",
                                    "子表字段",
                                    resolveTableFieldDisplayName(
                                            ctx, tableName, oldT.getJoinRightField()),
                                    resolveTableFieldDisplayName(
                                            ctx, tableName, newT.getJoinRightField())));
                }
                if (!Objects.equals(oldT.getRelationType(), newT.getRelationType())) {
                    cols.add(
                            createColumn(
                                    "relation_type",
                                    "关联关系",
                                    oldT.getRelationType(),
                                    newT.getRelationType()));
                }
                if (!Objects.equals(oldT.getReadOnly(), newT.getReadOnly())) {
                    cols.add(
                            createColumn(
                                    "read_only",
                                    "子表是否只读",
                                    oldT.getReadOnly() != null && oldT.getReadOnly() == 1
                                            ? "是"
                                            : "否",
                                    newT.getReadOnly() != null && newT.getReadOnly() == 1
                                            ? "是"
                                            : "否"));
                }
                if (!cols.isEmpty()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", tDisplayName);
                    item.put("columns", cols);
                    uList.add(item);
                }
            }
        }

        // d (删除从表): 严格规范，删除节点只保留 name 属性（优先转换为中文描述）
        for (Map.Entry<String, ModuleTableDTO> entry : oldMap.entrySet()) {
            String tableName = entry.getKey();
            ModuleTableDTO oldT = entry.getValue();
            if (!newMap.containsKey(tableName)) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", resolveTableDisplayName(ctx, oldT));
                dList.add(item);
            }
        }

        if (!uList.isEmpty()) actions.put("u", uList);
        if (!iList.isEmpty()) actions.put("i", iList);
        if (!dList.isEmpty()) actions.put("d", dList);

        childNode.put("actions", actions);
        return childNode;
    }

    private Map<String, Object> buildTableFieldConfigChildNodeForUpdate(
            AuditLookupContext ctx,
            String projectNo,
            Long subjectId,
            SaveModuleReq oldReq,
            SaveModuleReq newReq) {
        Map<String, Object> childNode = new HashMap<>();
        childNode.put("name", "表字段配置");
        Map<String, Object> actions = new HashMap<>();

        List<Map<String, Object>> uList = new ArrayList<>();
        List<Map<String, Object>> iList = new ArrayList<>();
        List<Map<String, Object>> dList = new ArrayList<>();

        // ---------------------------------------------------------------------
        // 1. 基础字段处理
        // ---------------------------------------------------------------------
        List<ModuleSimpleFieldDTO> oldSimple =
                oldReq.getSimpleFields() != null
                        ? oldReq.getSimpleFields()
                        : Collections.emptyList();
        List<ModuleSimpleFieldDTO> newSimple =
                newReq.getSimpleFields() != null
                        ? newReq.getSimpleFields()
                        : Collections.emptyList();

        Map<String, ModuleSimpleFieldDTO> oldSimpleMap =
                oldSimple.stream()
                        .filter(s -> s.getColumnName() != null)
                        .collect(
                                Collectors.toMap(
                                        s ->
                                                (s.getTableName() != null ? s.getTableName() : "")
                                                        + ":"
                                                        + s.getColumnName(),
                                        Function.identity(),
                                        (e1, e2) -> e1));
        Map<String, ModuleSimpleFieldDTO> newSimpleMap =
                newSimple.stream()
                        .filter(s -> s.getColumnName() != null)
                        .collect(
                                Collectors.toMap(
                                        s ->
                                                (s.getTableName() != null ? s.getTableName() : "")
                                                        + ":"
                                                        + s.getColumnName(),
                                        Function.identity(),
                                        (e1, e2) -> e1));

        Set<String> deletedSimpleCols = new HashSet<>(oldSimpleMap.keySet());
        deletedSimpleCols.removeAll(newSimpleMap.keySet());

        Set<String> addedSimpleCols = new HashSet<>(newSimpleMap.keySet());
        addedSimpleCols.removeAll(oldSimpleMap.keySet());

        // 基础字段删除 (d): 只留 old 属性，聚合删除的基础字段中文名
        if (!deletedSimpleCols.isEmpty()) {
            List<String> delNames = new ArrayList<>();
            for (String colKey : deletedSimpleCols) {
                ModuleSimpleFieldDTO s = oldSimpleMap.get(colKey);
                String colDisplayName =
                        resolveFieldDisplayName(
                                ctx,
                                s != null ? s.getTableName() : null,
                                s != null ? s.getColumnName() : null,
                                s != null ? s.getDisplayName() : null);
                delNames.add(colDisplayName);
            }
            Map<String, Object> simpleItem = new HashMap<>();
            simpleItem.put("name", "基础字段");
            List<Map<String, Object>> cols = new ArrayList<>();
            Map<String, Object> cMap = new HashMap<>();
            cMap.put("old", String.join("，", delNames));
            cols.add(cMap);
            simpleItem.put("columns", cols);
            dList.add(simpleItem);
        }

        // 基础字段新增 (i): 只留 newer 字段，聚合新增的基础字段中文名
        if (!addedSimpleCols.isEmpty()) {
            List<String> addNames = new ArrayList<>();
            for (String colKey : addedSimpleCols) {
                ModuleSimpleFieldDTO s = newSimpleMap.get(colKey);
                String colDisplayName =
                        resolveFieldDisplayName(
                                ctx, s.getTableName(), s.getColumnName(), s.getDisplayName());
                addNames.add(colDisplayName);
            }
            Map<String, Object> simpleItem = new HashMap<>();
            simpleItem.put("name", "基础字段");
            List<Map<String, Object>> cols = new ArrayList<>();
            Map<String, Object> cMap = new HashMap<>();
            cMap.put("newer", String.join("，", addNames));
            cols.add(cMap);
            simpleItem.put("columns", cols);
            iList.add(simpleItem);
        }

        if (!iList.isEmpty()) actions.put("i", iList);
        if (!dList.isEmpty()) actions.put("d", dList);

        childNode.put("actions", actions);
        return childNode;
    }

    private Map<String, Object> buildColumnConfigChildNodeForUpdate(
            AuditLookupContext ctx, SaveModuleReq oldReq, SaveModuleReq newReq) {
        Map<String, Object> childNode = new HashMap<>();
        childNode.put("name", "模块列配置");
        Map<String, Object> actions = new HashMap<>();

        List<ModuleTableHeaderDTO> oldHeaders =
                (oldReq.getModule() != null && oldReq.getModule().getTableHeader() != null)
                        ? oldReq.getModule().getTableHeader()
                        : Collections.emptyList();
        List<ModuleTableHeaderDTO> newHeaders =
                (newReq.getModule() != null && newReq.getModule().getTableHeader() != null)
                        ? newReq.getModule().getTableHeader()
                        : Collections.emptyList();

        Map<String, ModuleTableHeaderDTO> oldMap =
                oldHeaders.stream()
                        .filter(h -> h.getField() != null)
                        .collect(
                                Collectors.toMap(
                                        ModuleTableHeaderDTO::getField,
                                        Function.identity(),
                                        (e1, e2) -> e1));
        Map<String, ModuleTableHeaderDTO> newMap =
                newHeaders.stream()
                        .filter(h -> h.getField() != null)
                        .collect(
                                Collectors.toMap(
                                        ModuleTableHeaderDTO::getField,
                                        Function.identity(),
                                        (e1, e2) -> e1));

        List<Map<String, Object>> uList = new ArrayList<>();
        List<Map<String, Object>> iList = new ArrayList<>();
        List<Map<String, Object>> dList = new ArrayList<>();

        for (Map.Entry<String, ModuleTableHeaderDTO> entry : newMap.entrySet()) {
            String field = entry.getKey();
            ModuleTableHeaderDTO newH = entry.getValue();
            String colName = newH.getName() != null ? newH.getName() : field;

            if (!oldMap.containsKey(field)) {
                iList.add(buildTableHeaderItem(ctx, newH));
            } else {
                ModuleTableHeaderDTO oldH = oldMap.get(field);
                List<Map<String, Object>> cols = new ArrayList<>();
                if (!Objects.equals(oldH.getName(), newH.getName())) {
                    cols.add(
                            createColumn(
                                    "name",
                                    "列名称",
                                    oldH.getName() != null ? oldH.getName() : "",
                                    newH.getName() != null ? newH.getName() : ""));
                }
                if (!Objects.equals(oldH.getTable(), newH.getTable())) {
                    cols.add(
                            createColumn(
                                    "table",
                                    "表名称",
                                    resolveTableDisplayName(
                                            ctx,
                                            ModuleTableDTO.builder()
                                                    .tableName(oldH.getTable())
                                                    .build()),
                                    resolveTableDisplayName(
                                            ctx,
                                            ModuleTableDTO.builder()
                                                    .tableName(newH.getTable())
                                                    .build())));
                }
                if (!Objects.equals(oldH.getField(), newH.getField())) {
                    cols.add(
                            createColumn(
                                    "field",
                                    "关联字段",
                                    resolveTableFieldDisplayName(
                                            ctx, oldH.getTable(), oldH.getField()),
                                    resolveTableFieldDisplayName(
                                            ctx, newH.getTable(), newH.getField())));
                }
                if (!Objects.equals(oldH.getWidth(), newH.getWidth())) {
                    cols.add(
                            createColumn(
                                    "width",
                                    "列宽度",
                                    oldH.getWidth() != null ? String.valueOf(oldH.getWidth()) : "",
                                    newH.getWidth() != null
                                            ? String.valueOf(newH.getWidth())
                                            : ""));
                }
                if (!Objects.equals(oldH.getSortOrder(), newH.getSortOrder())) {
                    cols.add(
                            createColumn(
                                    "sort_order",
                                    "排序顺序",
                                    oldH.getSortOrder() != null
                                            ? String.valueOf(oldH.getSortOrder())
                                            : "",
                                    newH.getSortOrder() != null
                                            ? String.valueOf(newH.getSortOrder())
                                            : ""));
                }
                if (!Objects.equals(oldH.getSearchType(), newH.getSearchType())) {
                    cols.add(
                            createColumn(
                                    "search_type",
                                    "搜索类型",
                                    oldH.getSearchType() != null ? oldH.getSearchType() : "",
                                    newH.getSearchType() != null ? newH.getSearchType() : ""));
                }
                if (!Objects.equals(oldH.getFixed(), newH.getFixed())) {
                    cols.add(
                            createColumn(
                                    "fixed",
                                    "固定列位置",
                                    oldH.getFixed() != null ? oldH.getFixed() : "",
                                    newH.getFixed() != null ? newH.getFixed() : ""));
                }
                if (!Objects.equals(oldH.getEllipsis(), newH.getEllipsis())) {
                    cols.add(
                            createColumn(
                                    "ellipsis",
                                    "文本超出省略",
                                    oldH.getEllipsis() != null
                                            ? (oldH.getEllipsis() ? "是" : "否")
                                            : "",
                                    newH.getEllipsis() != null
                                            ? (newH.getEllipsis() ? "是" : "否")
                                            : ""));
                }
                if (!Objects.equals(oldH.getSortable(), newH.getSortable())) {
                    cols.add(
                            createColumn(
                                    "sortable",
                                    "是否可排序",
                                    oldH.getSortable() != null
                                            ? (oldH.getSortable() ? "是" : "否")
                                            : "",
                                    newH.getSortable() != null
                                            ? (newH.getSortable() ? "是" : "否")
                                            : ""));
                }
                if (!cols.isEmpty()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", colName);
                    item.put("columns", cols);
                    uList.add(item);
                }
            }
        }

        List<String> delHeaderNames = new ArrayList<>();
        for (Map.Entry<String, ModuleTableHeaderDTO> entry : oldMap.entrySet()) {
            String field = entry.getKey();
            ModuleTableHeaderDTO oldH = entry.getValue();
            if (!newMap.containsKey(field)) {
                String name = oldH.getName() != null ? oldH.getName() : field;
                delHeaderNames.add(name);
            }
        }
        if (!delHeaderNames.isEmpty()) {
            Map<String, Object> item = new HashMap<>();
            List<Map<String, Object>> cols = new ArrayList<>();
            Map<String, Object> cMap = new HashMap<>();
            cMap.put("old", String.join("，", delHeaderNames));
            cols.add(cMap);
            item.put("columns", cols);
            dList.add(item);
        }

        if (!uList.isEmpty()) actions.put("u", uList);
        if (!iList.isEmpty()) actions.put("i", iList);
        if (!dList.isEmpty()) actions.put("d", dList);

        childNode.put("actions", actions);
        return childNode;
    }

    private Map<String, Object> buildTableHeaderItem(
            AuditLookupContext ctx, ModuleTableHeaderDTO header) {
        Map<String, Object> item = new HashMap<>();
        String colName =
                StringUtils.hasText(header.getName())
                        ? header.getName()
                        : (header.getField() != null ? header.getField() : "");
        item.put("name", colName);
        List<Map<String, Object>> cols = new ArrayList<>();
        if (StringUtils.hasText(header.getName())) {
            cols.add(createColumnNullOld("name", "列名称", header.getName()));
        }
        if (StringUtils.hasText(header.getTable())) {
            cols.add(
                    createColumnNullOld(
                            "table",
                            "表名称",
                            resolveTableDisplayName(
                                    ctx,
                                    ModuleTableDTO.builder()
                                            .tableName(header.getTable())
                                            .build())));
        }
        if (StringUtils.hasText(header.getField())) {
            cols.add(
                    createColumnNullOld(
                            "field",
                            "关联字段",
                            resolveTableFieldDisplayName(
                                    ctx, header.getTable(), header.getField())));
        }
        if (header.getWidth() != null) {
            cols.add(createColumnNullOld("width", "列宽度", String.valueOf(header.getWidth())));
        }
        if (header.getSortOrder() != null) {
            cols.add(
                    createColumnNullOld(
                            "sort_order", "排序顺序", String.valueOf(header.getSortOrder())));
        }
        if (StringUtils.hasText(header.getSearchType())) {
            cols.add(createColumnNullOld("search_type", "搜索类型", header.getSearchType()));
        }
        if (StringUtils.hasText(header.getFixed())) {
            cols.add(createColumnNullOld("fixed", "固定列位置", header.getFixed()));
        }
        if (header.getEllipsis() != null) {
            cols.add(createColumnNullOld("ellipsis", "文本超出省略", header.getEllipsis() ? "是" : "否"));
        }
        if (header.getSortable() != null) {
            cols.add(createColumnNullOld("sortable", "是否可排序", header.getSortable() ? "是" : "否"));
        }
        item.put("columns", cols);
        return item;
    }

    private Map<String, Object> buildStatusConfigChildNodeForUpdate(
            AuditLookupContext ctx, SaveModuleReq oldReq, SaveModuleReq newReq) {
        Map<String, Object> childNode = new HashMap<>();
        childNode.put("name", "模块状态配置");
        Map<String, Object> actions = new HashMap<>();

        List<ModuleStatusDTO> oldStatuses =
                oldReq.getModuleStatuses() != null
                        ? oldReq.getModuleStatuses()
                        : Collections.emptyList();
        List<ModuleStatusDTO> newStatuses =
                newReq.getModuleStatuses() != null
                        ? newReq.getModuleStatuses()
                        : Collections.emptyList();

        Map<String, ModuleStatusDTO> oldMap =
                oldStatuses.stream()
                        .filter(s -> s.getStatusId() != null)
                        .collect(
                                Collectors.toMap(
                                        s -> s.getStatusPid() + ":" + s.getStatusId(),
                                        Function.identity(),
                                        (e1, e2) -> e1));
        Map<String, ModuleStatusDTO> newMap =
                newStatuses.stream()
                        .filter(s -> s.getStatusId() != null)
                        .collect(
                                Collectors.toMap(
                                        s -> s.getStatusPid() + ":" + s.getStatusId(),
                                        Function.identity(),
                                        (e1, e2) -> e1));

        List<Map<String, Object>> uList = new ArrayList<>();
        List<Map<String, Object>> iList = new ArrayList<>();
        List<Map<String, Object>> dList = new ArrayList<>();

        for (Map.Entry<String, ModuleStatusDTO> entry : newMap.entrySet()) {
            String key = entry.getKey();
            ModuleStatusDTO newS = entry.getValue();
            String idTitle = resolveStatusTitle(ctx, newS.getStatusId());
            String pidTitle =
                    newS.getStatusPid() != null && newS.getStatusPid() != 0L
                            ? resolveStatusTitle(ctx, newS.getStatusPid())
                            : idTitle;

            if (!oldMap.containsKey(key)) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", idTitle);
                List<Map<String, Object>> cols = new ArrayList<>();
                Map<String, Object> colMap = new HashMap<>();
                colMap.put("newer", pidTitle + "—" + idTitle);
                cols.add(colMap);
                item.put("columns", cols);
                iList.add(item);
            }
        }

        for (Map.Entry<String, ModuleStatusDTO> entry : oldMap.entrySet()) {
            String key = entry.getKey();
            ModuleStatusDTO oldS = entry.getValue();
            if (!newMap.containsKey(key)) {
                String oldIdTitle = resolveStatusTitle(ctx, oldS.getStatusId());
                String oldPidTitle =
                        oldS.getStatusPid() != null && oldS.getStatusPid() != 0L
                                ? resolveStatusTitle(ctx, oldS.getStatusPid())
                                : oldIdTitle;

                Map<String, Object> item = new HashMap<>();
                item.put("name", oldIdTitle);
                List<Map<String, Object>> cols = new ArrayList<>();
                Map<String, Object> colMap = new HashMap<>();
                colMap.put("old", oldPidTitle + "-" + oldIdTitle);
                cols.add(colMap);
                item.put("columns", cols);
                dList.add(item);
            }
        }

        if (!uList.isEmpty()) actions.put("u", uList);
        if (!iList.isEmpty()) actions.put("i", iList);
        if (!dList.isEmpty()) actions.put("d", dList);

        childNode.put("actions", actions);
        return childNode;
    }

    /** 辅助方法：通过 statusId 查询拿到对应的状态中文 title 说明（例如 "草稿"） */
    private String resolveStatusTitle(AuditLookupContext ctx, Long statusId) {
        if (statusId == null) {
            return "";
        }
        if (statusId == 0L) {
            return "无";
        }
        if (ctx != null && ctx.statusTitleMap.containsKey(statusId)) {
            String title = ctx.statusTitleMap.get(statusId);
            if (StringUtils.hasText(title)) {
                return title;
            }
        }
        return String.valueOf(statusId);
    }

    // =========================================================================
    // 第三部分：通用 Column 映射构造器
    // =========================================================================

    private Map<String, Object> createColumn(
            String field, String name, String oldVal, String newVal) {
        Map<String, Object> col = new HashMap<>();
        col.put("field", field != null ? field : "");
        col.put("name", name != null ? name : "");
        col.put("old", oldVal != null ? oldVal : "");
        col.put("newer", newVal != null ? newVal : "");
        return col;
    }

    private Map<String, Object> createColumnNullOld(String field, String name, String newVal) {
        Map<String, Object> col = new HashMap<>();
        col.put("field", field != null ? field : "");
        col.put("name", name != null ? name : "");
        col.put("old", null);
        col.put("newer", newVal != null ? newVal : "");
        return col;
    }

    private String resolveFieldDisplayName(
            AuditLookupContext ctx, String tableName, String columnName, String displayName) {
        if (StringUtils.hasText(displayName)) {
            return displayName;
        }
        if (StringUtils.hasText(tableName) && StringUtils.hasText(columnName) && ctx != null) {
            String key = tableName + ":" + columnName;
            if (ctx.fieldCommentMap.containsKey(key)) {
                return ctx.fieldCommentMap.get(key);
            }
        }

        // 通用内置审计字段名称兜底
        if ("id".equalsIgnoreCase(columnName)) {
            return "主键ID";
        }
        if ("tenant_id".equalsIgnoreCase(columnName)) {
            return "租户ID";
        }
        if ("project_no".equalsIgnoreCase(columnName)) {
            return "项目编号";
        }
        if ("deleted".equalsIgnoreCase(columnName)) {
            return "删除状态";
        }
        if ("create_date".equalsIgnoreCase(columnName)
                || "created_date".equalsIgnoreCase(columnName)) {
            return "创建时间";
        }
        if ("update_date".equalsIgnoreCase(columnName)
                || "updated_date".equalsIgnoreCase(columnName)) {
            return "更新时间";
        }
        if ("subject_id".equalsIgnoreCase(columnName)) {
            return "主体ID";
        }
        if ("subject_name".equalsIgnoreCase(columnName)) {
            return "主体名称";
        }
        return columnName != null ? columnName : "";
    }

    private String resolveTableDisplayName(AuditLookupContext ctx, ModuleTableDTO table) {
        if (table == null) {
            return "";
        }
        String tableName = table.getTableName();
        String tableDesc = table.getTableDesc();

        if (!StringUtils.hasText(tableDesc) && StringUtils.hasText(tableName) && ctx != null) {
            if (ctx.tableCommentMap.containsKey(tableName)) {
                tableDesc = ctx.tableCommentMap.get(tableName);
            }
        }

        if (StringUtils.hasText(tableDesc) && StringUtils.hasText(tableName)) {
            return tableDesc + "(" + tableName + ")";
        }
        if (StringUtils.hasText(tableDesc)) {
            return tableDesc;
        }
        return tableName != null ? tableName : "";
    }

    private String resolveTableFieldDisplayName(
            AuditLookupContext ctx, String tableName, String columnName) {
        if (!StringUtils.hasText(columnName)) {
            return "";
        }
        String fieldDesc = resolveFieldDisplayName(ctx, tableName, columnName, null);
        if (StringUtils.hasText(fieldDesc) && !fieldDesc.equals(columnName)) {
            return fieldDesc + "(" + columnName + ")";
        }
        return columnName;
    }
}
