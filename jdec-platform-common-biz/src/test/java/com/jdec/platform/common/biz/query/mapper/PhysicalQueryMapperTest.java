package com.jdec.platform.common.biz.query.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jdec.platform.common.api.dto.response.DataRecordItem;
import com.jdec.platform.common.api.dto.response.ModuleDataNode;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.data.api.dto.request.TableSpec;
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
}
