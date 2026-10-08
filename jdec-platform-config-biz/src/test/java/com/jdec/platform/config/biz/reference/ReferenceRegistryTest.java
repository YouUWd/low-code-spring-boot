package com.jdec.platform.config.biz.reference;

import static org.junit.jupiter.api.Assertions.*;

import com.jdec.platform.config.biz.entity.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 验证 {@link ReferenceRegistry} 启动扫描注解后构建的引用关系索引。 */
class ReferenceRegistryTest {

    private ReferenceRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ReferenceRegistry();
        registry.init();
    }

    @Test
    @DisplayName("模块的引用关系：7 条（含自引用子模块）")
    void moduleReferences() {
        List<ReferenceMetadata> references = registry.findByTarget(SysModule.class);
        assertEquals(7, references.size());
        assertTrue(
                references.stream()
                        .anyMatch(
                                r ->
                                        r.sourceTable().equals("sys_menu")
                                                && r.sourceField().equals("module_id")));
        assertTrue(
                references.stream()
                        .anyMatch(
                                r ->
                                        r.sourceTable().equals("sys_module")
                                                && r.sourceField().equals("parent_id")));
    }

    @Test
    @DisplayName("角色的引用关系：6 条")
    void roleReferences() {
        List<ReferenceMetadata> references = registry.findByTarget(SysRole.class);
        assertEquals(6, references.size());
        assertTrue(
                references.stream()
                        .anyMatch(
                                r ->
                                        r.sourceTable().equals("sys_approval_chain_config")
                                                && r.sourceField().equals("approve_role_id")));
    }

    @Test
    @DisplayName("交互/特殊/数据权限各 1 条引用关系")
    void permissionReferences() {
        assertEquals(1, registry.findByTarget(SysInteractionPermission.class).size());
        assertEquals(1, registry.findByTarget(SysSpecialPermission.class).size());
        assertEquals(1, registry.findByTarget(SysDataPermission.class).size());
    }

    @Test
    @DisplayName("配置项的引用关系：按 categoryAlias 引用配置分类（非主键列）")
    void configItemReferences() {
        List<ReferenceMetadata> references =
                registry.findByTarget(SysConfigCategory.class, "categoryAlias");
        assertEquals(1, references.size());
        ReferenceMetadata ref = references.get(0);
        assertEquals("sys_config_item", ref.sourceTable());
        assertEquals("category_alias", ref.sourceField());
        assertEquals("label", ref.displayField());
        assertEquals("配置项", ref.name());
        assertEquals("categoryAlias", ref.targetField());
    }

    @Test
    @DisplayName("未声明引用的实体返回空（保持既有规则：菜单/按钮/状态不拦截）")
    void noReferences() {
        assertTrue(registry.findByTarget(SysMenu.class).isEmpty());
        assertTrue(registry.findByTarget(SysButton.class).isEmpty());
        assertTrue(registry.findByTarget(SysStatus.class).isEmpty());
    }

    @Test
    @DisplayName("展示字段被解析为列名")
    void displayFieldResolved() {
        ReferenceMetadata menuRef =
                registry.findByTarget(SysModule.class).stream()
                        .filter(r -> r.sourceTable().equals("sys_menu"))
                        .findFirst()
                        .orElseThrow();
        assertEquals("title", menuRef.displayField());
        assertEquals("菜单", menuRef.name());
        assertEquals("id", menuRef.sourcePk());
    }
}
