package com.jdec.platform.common.biz.query.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jdec.platform.common.api.dto.response.DataRecordItem;
import com.jdec.platform.common.api.dto.response.ModuleDataNode;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.request.TableSpec;
import com.jdec.platform.data.api.dto.response.DataPage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhysicalQueryMapperTest {

    @Test
    @DisplayName("测试 supplementShadowColumns：当根模块包含 nature 且未被勾选时，自动补入 TableSpec 并返回影子列名")
    void testSupplementShadowColumns_addWhenMissing() {
        ModuleTreeDTO tree = buildTestModuleTree(true, true);
        List<TableSpec> tableSpecs = new ArrayList<>();
        tableSpecs.add(TableSpec.of(1001L, "info", new ArrayList<>(List.of("id", "company_name"))));

        Set<String> shadowKeys =
                PhysicalQueryMapper.supplementShadowColumns(
                        tableSpecs, tree, 1001L, Set.of("nature"));

        assertEquals(1, shadowKeys.size());
        assertTrue(shadowKeys.contains("info.nature"));

        TableSpec primarySpec = tableSpecs.get(0);
        assertEquals(List.of("id", "company_name", "nature"), primarySpec.getColumns());
    }

    @Test
    @DisplayName("测试 supplementShadowColumns：当查询条件中已存在（无论是否带表名前缀），绝不作为影子列记录，避免后续错误移除")
    void testSupplementShadowColumns_doNotDuplicateWhenAlreadyPresent() {
        ModuleTreeDTO tree = buildTestModuleTree(true, true);

        // 场景 A：TableSpec 中存的是不带表名的 "nature"
        List<TableSpec> specs1 = new ArrayList<>();
        specs1.add(
                TableSpec.of(
                        1001L, "info", new ArrayList<>(List.of("id", "company_name", "nature"))));
        Set<String> shadowKeys1 =
                PhysicalQueryMapper.supplementShadowColumns(specs1, tree, 1001L, Set.of("nature"));
        assertTrue(shadowKeys1.isEmpty(), "已有投影列不应被加入待移除列表，避免后续错误移除");

        // 场景 B：TableSpec 中存的是带表名前缀的 "info.nature"
        List<TableSpec> specs2 = new ArrayList<>();
        specs2.add(
                TableSpec.of(
                        1001L,
                        "info",
                        new ArrayList<>(List.of("id", "company_name", "info.nature"))));
        Set<String> shadowKeys2 =
                PhysicalQueryMapper.supplementShadowColumns(specs2, tree, 1001L, Set.of("nature"));
        assertTrue(shadowKeys2.isEmpty(), "带表名前缀的已有投影列也必须被精准识别，避免后续错误移除");

        // 场景 C：TableSpec 中使用了全列通配符 "*"
        List<TableSpec> specs3 = new ArrayList<>();
        specs3.add(TableSpec.of(1001L, "info", new ArrayList<>(List.of("*"))));
        Set<String> shadowKeys3 =
                PhysicalQueryMapper.supplementShadowColumns(
                        specs3, tree, 1001L, Set.of("info.nature"));
        assertTrue(shadowKeys3.isEmpty(), "已包含 * 通配符的表不应重复补入或误删");
    }

    @Test
    @DisplayName("测试 supplementShadowColumns：支持位于其他表（如 basic_info）的影子列补齐")
    void testSupplementShadowColumns_crossTableSupport() {
        ModuleTreeDTO tree = buildTestModuleTree(true, true);

        // 1. 当其他表（basic_info）原本包含在当前查询条件中时，且缺少依赖列，能够为其精准补入
        List<TableSpec> specsWithSubTable = new ArrayList<>();
        specsWithSubTable.add(
                TableSpec.of(1001L, "info", new ArrayList<>(List.of("id", "company_name"))));
        specsWithSubTable.add(
                TableSpec.of(1002L, "basic_info", new ArrayList<>(List.of("currency"))));

        Set<String> shadowKeys =
                PhysicalQueryMapper.supplementShadowColumns(
                        specsWithSubTable, tree, 1001L, Set.of("basic_info.reg_capital"));

        assertEquals(1, shadowKeys.size());
        assertTrue(shadowKeys.contains("basic_info.reg_capital"));
        assertEquals(List.of("currency", "reg_capital"), specsWithSubTable.get(1).getColumns());

        // 2. 当其他表未参与当前查询条件时，绝不随意强行引入无关从表
        List<TableSpec> specsWithoutSubTable = new ArrayList<>();
        specsWithoutSubTable.add(
                TableSpec.of(1001L, "info", new ArrayList<>(List.of("id", "company_name"))));

        Set<String> shadowKeysSkipped =
                PhysicalQueryMapper.supplementShadowColumns(
                        specsWithoutSubTable, tree, 1001L, Set.of("basic_info.reg_capital"));

        assertTrue(shadowKeysSkipped.isEmpty(), "未参与当前查询的从表不应被强行引入");
        assertEquals(1, specsWithoutSubTable.size());
    }

    @Test
    @DisplayName("测试 supplementShadowColumns：当模块元数据中根本不存在该列时，不进行多余补入")
    void testSupplementShadowColumns_doNotAddWhenNotExistsInMetadata() {
        ModuleTreeDTO tree = buildTestModuleTree(false, false);
        List<TableSpec> tableSpecs = new ArrayList<>();
        tableSpecs.add(TableSpec.of(1001L, "info", new ArrayList<>(List.of("id", "company_name"))));

        Set<String> shadowKeys =
                PhysicalQueryMapper.supplementShadowColumns(
                        tableSpecs, tree, 1001L, Set.of("nature"));

        assertTrue(shadowKeys.isEmpty());
        assertEquals(List.of("id", "company_name"), tableSpecs.get(0).getColumns());
    }

    @Test
    @DisplayName("测试 removeShadowColumns：严格基于 table.column 剥离，绝不误删其他表的同名合法列")
    void testRemoveShadowColumns_stripAccuratelyWithoutCollateralDamage() {
        Map<String, Object> parentRow = new LinkedHashMap<>();
        parentRow.put("info.id", 101L);
        parentRow.put("info.company_name", "测试企业");
        parentRow.put("info.nature", "[{\"alias_value_title\":\"个人\"}]"); // 待剥离
        parentRow.put("basic_info.nature", "保留此其他表的合法字段"); // 属于其他表，绝不能误删！

        Map<String, Object> childRow = new LinkedHashMap<>();
        childRow.put("member.id", 201L);
        childRow.put("info.nature", "冗余影子列");

        DataRecordItem childItem =
                DataRecordItem.builder().row(childRow).children(new ArrayList<>()).build();
        ModuleDataNode childNode =
                ModuleDataNode.builder()
                        .moduleId(1003L)
                        .record(new ArrayList<>(List.of(childItem)))
                        .build();

        DataRecordItem parentItem =
                DataRecordItem.builder()
                        .row(parentRow)
                        .children(new ArrayList<>(List.of(childNode)))
                        .build();

        List<DataRecordItem> items = List.of(parentItem);

        PhysicalQueryMapper.removeShadowColumns(items, Set.of("info.nature"));

        // 验证父节点
        assertEquals(101L, parentItem.getRow().get("info.id"));
        assertEquals("测试企业", parentItem.getRow().get("info.company_name"));
        assertFalse(parentItem.getRow().containsKey("info.nature"), "info.nature 影子列应被彻底移除");
        assertEquals(
                "保留此其他表的合法字段",
                parentItem.getRow().get("basic_info.nature"),
                "basic_info.nature 绝不能被误删");

        // 验证子节点
        assertEquals(201L, childItem.getRow().get("member.id"));
        assertFalse(childItem.getRow().containsKey("info.nature"), "子节点影子列应被递归移除");
    }

    private ModuleTreeDTO buildTestModuleTree(
            boolean includeNatureInInfo, boolean includeBasicInfo) {
        Map<Long, ModuleInfo> moduleMap = new HashMap<>();
        Map<Long, ModuleFieldDTO> fieldMap = new HashMap<>();
        Map<Long, Long> fieldToModuleMap = new HashMap<>();

        List<ModuleFieldDTO> infoFields = new ArrayList<>();
        ModuleFieldDTO idField =
                ModuleFieldDTO.builder()
                        .id(1L)
                        .moduleId(1001L)
                        .tableName("info")
                        .columnName("id")
                        .displayName("主键")
                        .build();
        ModuleFieldDTO nameField =
                ModuleFieldDTO.builder()
                        .id(2L)
                        .moduleId(1001L)
                        .tableName("info")
                        .columnName("company_name")
                        .displayName("企业名称")
                        .build();
        infoFields.add(idField);
        infoFields.add(nameField);
        fieldMap.put(1L, idField);
        fieldMap.put(2L, nameField);
        fieldToModuleMap.put(1L, 1001L);
        fieldToModuleMap.put(2L, 1001L);

        if (includeNatureInInfo) {
            ModuleFieldDTO natureField =
                    ModuleFieldDTO.builder()
                            .id(3L)
                            .moduleId(1001L)
                            .tableName("info")
                            .columnName("nature")
                            .displayName("对象性质")
                            .build();
            infoFields.add(natureField);
            fieldMap.put(3L, natureField);
            fieldToModuleMap.put(3L, 1001L);
        }

        moduleMap.put(
                1001L,
                ModuleInfo.builder()
                        .moduleId(1001L)
                        .moduleCode("COMPANY-ROOT")
                        .primaryTable("info")
                        .fields(infoFields)
                        .build());

        if (includeBasicInfo) {
            List<ModuleFieldDTO> basicFields = new ArrayList<>();
            ModuleFieldDTO currencyField =
                    ModuleFieldDTO.builder()
                            .id(4L)
                            .moduleId(1002L)
                            .tableName("basic_info")
                            .columnName("currency")
                            .displayName("币种")
                            .build();
            ModuleFieldDTO regCapitalField =
                    ModuleFieldDTO.builder()
                            .id(5L)
                            .moduleId(1002L)
                            .tableName("basic_info")
                            .columnName("reg_capital")
                            .displayName("注册资本")
                            .build();
            ModuleFieldDTO subNatureField =
                    ModuleFieldDTO.builder()
                            .id(6L)
                            .moduleId(1002L)
                            .tableName("basic_info")
                            .columnName("nature")
                            .displayName("从属性质")
                            .build();
            basicFields.add(currencyField);
            basicFields.add(regCapitalField);
            basicFields.add(subNatureField);

            fieldMap.put(4L, currencyField);
            fieldMap.put(5L, regCapitalField);
            fieldMap.put(6L, subNatureField);
            fieldToModuleMap.put(4L, 1002L);
            fieldToModuleMap.put(5L, 1002L);
            fieldToModuleMap.put(6L, 1002L);

            moduleMap.put(
                    1002L,
                    ModuleInfo.builder()
                            .moduleId(1002L)
                            .moduleCode("COMPANY-BASIC")
                            .primaryTable("basic_info")
                            .fields(basicFields)
                            .build());
        }

        return ModuleTreeDTO.builder()
                .rootModuleId(1001L)
                .moduleMap(moduleMap)
                .fieldMap(fieldMap)
                .fieldToModuleMap(fieldToModuleMap)
                .build();
    }

    @Test
    @DisplayName("测试变更申请单表头与数据字段过滤：id/source_id不进表头但返回数据，read_status既不进表头也不进数据")
    void testChangeColumnsHeaderAndDataRules() {
        MenuHeaderColumnResp headerRoot =
                MenuHeaderColumnResp.builder().moduleId(1001L).children(new ArrayList<>()).build();

        // 1. ensureChangeColumnsInHeader 仅补入 change_no 和 modify_reason
        PhysicalQueryMapper.ensureChangeColumnsInHeader(headerRoot, "info", 1001L);
        List<String> dataIndexes =
                headerRoot.getChildren().stream().map(MenuHeaderColumnResp::getDataIndex).toList();
        assertTrue(dataIndexes.contains("info.change_no"));
        assertTrue(dataIndexes.contains("info.modify_reason"));
        assertFalse(dataIndexes.contains("info.id"));
        assertFalse(dataIndexes.contains("info.source_id"));
        assertFalse(dataIndexes.contains("info.read_status"));

        // 2. pruneHeaderToFirstLevel 裁剪时排除 id、source_id、read_status
        MenuHeaderColumnResp headerWithExtras =
                MenuHeaderColumnResp.builder()
                        .moduleId(1001L)
                        .children(
                                new ArrayList<>(
                                        List.of(
                                                MenuHeaderColumnResp.builder()
                                                        .moduleId(1001L)
                                                        .label("变更ID")
                                                        .dataIndex("info.id")
                                                        .build(),
                                                MenuHeaderColumnResp.builder()
                                                        .moduleId(1001L)
                                                        .label("原单ID")
                                                        .dataIndex("info.source_id")
                                                        .build(),
                                                MenuHeaderColumnResp.builder()
                                                        .moduleId(1001L)
                                                        .label("读取状态")
                                                        .dataIndex("info.read_status")
                                                        .build(),
                                                MenuHeaderColumnResp.builder()
                                                        .moduleId(1001L)
                                                        .label("变更单号")
                                                        .dataIndex("info.change_no")
                                                        .build())))
                        .build();
        MenuHeaderColumnResp pruned = PhysicalQueryMapper.pruneHeaderToFirstLevel(headerWithExtras);
        List<String> prunedLabels =
                pruned.getChildren().stream().map(MenuHeaderColumnResp::getLabel).toList();
        assertEquals(1, prunedLabels.size());
        assertTrue(prunedLabels.contains("变更单号"));
        assertFalse(prunedLabels.contains("变更ID"));
        assertFalse(prunedLabels.contains("原单ID"));
        assertFalse(prunedLabels.contains("读取状态"));

        // 3. collectChangeTargetColumns 必须包含 id 与 source_id，但不得包含 read_status
        ModuleTreeDTO tree = buildTestModuleTree(false, false);
        List<String> targetColumns =
                PhysicalQueryMapper.collectChangeTargetColumns(pruned, tree, "info");
        assertTrue(targetColumns.contains("id"));
        assertTrue(targetColumns.contains("source_id"));
        assertTrue(targetColumns.contains("change_no"));
        assertTrue(targetColumns.contains("modify_reason"));
        assertTrue(targetColumns.contains("read_status"));

        // 4. toChangeDataRows 返回数据中移除 read_status，但保留 id、change_id 与 source_id
        Map<String, Object> rawMap = new LinkedHashMap<>();
        rawMap.put("id", 100L);
        rawMap.put("source_id", 200L);
        rawMap.put("change_no", "CHG001");
        rawMap.put("read_status", 1);
        DataPage<List<Map<String, Object>>> page = DataPage.of(1, 10, 1L, List.of(rawMap));
        List<DataRow> rows = PhysicalQueryMapper.toChangeDataRows(page, "info");
        assertEquals(1, rows.size());
        Map<String, Object> tableData = rows.get(0).getTable("info");
        assertEquals(100L, tableData.get("id"));
        assertEquals(100L, tableData.get("change_id"));
        assertEquals(200L, tableData.get("source_id"));
        assertTrue(tableData.containsKey("read_status"));
    }

    @Test
    @DisplayName("测试变更申请单表头排布顺序与字段重命名：变更单号放最前，变更原因在变更申请人之前，创建时间改为消息发送时间，移除更新人/更新时间/主体ID")
    void testChangeHeaderOrder_changeNoAtFirst_modifyReasonBeforeCreator() {
        // 构建原单表头：包含 企业名称、业务编号、创建人、主体ID、创建时间、更新时间、更新人
        MenuHeaderColumnResp colComp =
                MenuHeaderColumnResp.builder()
                        .moduleId(1001L)
                        .label("企业名称")
                        .dataIndex("info.company_name")
                        .build();
        MenuHeaderColumnResp colBiz =
                MenuHeaderColumnResp.builder()
                        .moduleId(1001L)
                        .label("业务编号")
                        .dataIndex("info.business_no")
                        .build();
        MenuHeaderColumnResp colCreator =
                MenuHeaderColumnResp.builder()
                        .moduleId(1001L)
                        .label("创建人")
                        .dataIndex("info.created_by")
                        .build();
        MenuHeaderColumnResp colSubjectId =
                MenuHeaderColumnResp.builder()
                        .moduleId(1001L)
                        .label("主体ID")
                        .dataIndex("info.subject_id")
                        .build();
        MenuHeaderColumnResp colCreateTime =
                MenuHeaderColumnResp.builder()
                        .moduleId(1001L)
                        .label("创建时间")
                        .dataIndex("info.created_date")
                        .build();
        MenuHeaderColumnResp colUpdateTime =
                MenuHeaderColumnResp.builder()
                        .moduleId(1001L)
                        .label("更新时间")
                        .dataIndex("info.updated_date")
                        .build();
        MenuHeaderColumnResp colUpdater =
                MenuHeaderColumnResp.builder()
                        .moduleId(1001L)
                        .label("更新人")
                        .dataIndex("info.updated_by")
                        .build();

        MenuHeaderColumnResp headerRoot =
                MenuHeaderColumnResp.builder()
                        .moduleId(1001L)
                        .children(
                                new ArrayList<>(
                                        List.of(
                                                colComp,
                                                colBiz,
                                                colCreator,
                                                colSubjectId,
                                                colCreateTime,
                                                colUpdateTime,
                                                colUpdater)))
                        .build();

        // 1. 补齐变更字段
        PhysicalQueryMapper.ensureChangeColumnsInHeader(headerRoot, "info", 1001L);

        // 2. 剪枝并重新排布、重命名、排除更新人/更新时间/主体ID
        MenuHeaderColumnResp pruned = PhysicalQueryMapper.pruneHeaderToFirstLevel(headerRoot);

        assertNotNull(pruned);
        List<String> labels =
                pruned.getChildren().stream().map(MenuHeaderColumnResp::getLabel).toList();

        // 验证变更单号位于第 0 位
        assertEquals("变更单号", labels.get(0));

        // 验证更新人、更新时间、主体ID被彻底移除
        assertFalse(labels.contains("更新人"), "更新人必须在表头中移除");
        assertFalse(labels.contains("更新时间"), "更新时间必须在表头中移除");
        assertFalse(labels.contains("主体ID"), "主体ID必须在表头中移除");

        // 验证创建人被重命名为“变更申请人”，创建时间被重命名为“消息发送时间”
        assertFalse(labels.contains("创建人"), "原'创建人'必须重命名");
        assertFalse(labels.contains("创建时间"), "原'创建时间'必须重命名");
        assertTrue(labels.contains("变更申请人"), "必须包含'变更申请人'");
        assertTrue(labels.contains("消息发送时间"), "必须包含'消息发送时间'");

        // 验证变更原因紧邻在变更申请人之前
        int reasonIndex = labels.indexOf("变更原因");
        int creatorIndex = labels.indexOf("变更申请人");
        assertTrue(reasonIndex >= 0, "表头中必须包含变更原因");
        assertTrue(creatorIndex >= 0, "表头中必须包含变更申请人");
        assertEquals(creatorIndex - 1, reasonIndex, "变更原因必须位于变更申请人紧邻的前一位");

        // 整体顺序校验：变更单号 -> 企业名称 -> 业务编号 -> 变更原因 -> 变更申请人 -> 消息发送时间
        assertEquals(List.of("变更单号", "企业名称", "业务编号", "变更原因", "变更申请人", "消息发送时间"), labels);
    }

    @Test
    @DisplayName(
            "测试 supplementSystemColumns：根据物理表真实列交集自动补齐 BUILTIN_SYSTEM_COLUMNS，且不进入 shadowKeys 被剥离")
    void testSupplementSystemColumns_addActualSystemColumnsAndKeepOutOfShadowKeys() {
        ModuleTreeDTO tree = buildTestModuleTree(true, true);
        List<TableSpec> tableSpecs = new ArrayList<>();
        tableSpecs.add(TableSpec.of(1001L, "info", new ArrayList<>(List.of("company_name"))));
        tableSpecs.add(TableSpec.of(1002L, "basic_info", new ArrayList<>(List.of("currency"))));

        Map<String, Set<String>> requestedMap = new HashMap<>();
        requestedMap.put("info", new java.util.HashSet<>(Set.of("company_name")));
        requestedMap.put("basic_info", new java.util.HashSet<>(Set.of("currency")));

        PhysicalQueryMapper.supplementSystemColumns(
                tableSpecs,
                tree,
                1001L,
                table -> {
                    if ("info".equalsIgnoreCase(table)) {
                        return Set.of(
                                "id",
                                "company_name",
                                "business_no",
                                "business_status",
                                "version_no",
                                "created_by",
                                "created_date");
                    }
                    if ("basic_info".equalsIgnoreCase(table)) {
                        return Set.of("id", "company_id", "subject_id", "currency", "deleted");
                    }
                    return Set.of();
                },
                requestedMap);

        assertEquals(
                List.of(
                        "company_name",
                        "id",
                        "business_no",
                        "version_no",
                        "business_status",
                        "created_by",
                        "created_date"),
                tableSpecs.get(0).getColumns());
        assertEquals(
                List.of("currency", "id", "deleted", "subject_id"), tableSpecs.get(1).getColumns());

        assertTrue(requestedMap.get("info").contains("business_status"));
        assertTrue(requestedMap.get("info").contains("created_by"));
        assertTrue(requestedMap.get("basic_info").contains("subject_id"));
        assertFalse(requestedMap.get("basic_info").contains("company_id"));

        // 随后执行 supplementShadowColumns（包含 business_status、created_by、nature），
        // 已作为系统辅助列补入的 business_status、created_by 绝不进入 shadowKeys，仅 nature 进入 shadowKeys
        Set<String> shadowKeys =
                PhysicalQueryMapper.supplementShadowColumns(
                        tableSpecs, tree, 1001L, Set.of("business_status", "created_by", "nature"));
        assertEquals(Set.of("info.nature"), shadowKeys);
    }
}
