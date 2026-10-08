package com.jdec.platform.config.biz.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.request.SaveModuleReq;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysTableRelation;
import com.jdec.platform.config.biz.mapper.SysModuleFieldMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.mapper.SysModuleStatusMapper;
import com.jdec.platform.config.biz.mapper.SysStatusMapper;
import com.jdec.platform.config.biz.mapper.SysTableRelationMapper;
import com.jdec.platform.config.biz.reference.ReferenceCheckService;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SysModuleServiceTest {

    @Mock private SysModuleMapper sysModuleMapper;

    @Mock private SysModuleFieldMapper sysModuleFieldMapper;

    @Mock private SysModuleStatusMapper sysModuleStatusMapper;

    @Mock private SysStatusMapper sysStatusMapper;

    @Mock private SysTableRelationMapper sysTableRelationMapper;

    @Mock private ReferenceCheckService referenceCheckService;

    @InjectMocks private SysModuleService sysModuleService;

    private List<SysTableRelation> tableRelations;
    private MockedStatic<Db> mockedDb;

    @BeforeEach
    void setUp() {
        mockedDb = mockStatic(Db.class);
        tableRelations = new ArrayList<>();
        // info <-> basic_info (1:1)
        tableRelations.add(
                SysTableRelation.builder()
                        .projectNo("company")
                        .mainTable("info")
                        .joinTable("basic_info")
                        .relationType("1:1")
                        .build());
        // info <-> company_user (N:1, info.user_id -> company_user.id)
        tableRelations.add(
                SysTableRelation.builder()
                        .projectNo("company")
                        .mainTable("info")
                        .joinTable("company_user")
                        .relationType("N:1")
                        .build());
        // info <-> member (1:N)
        tableRelations.add(
                SysTableRelation.builder()
                        .projectNo("company")
                        .mainTable("info")
                        .joinTable("member")
                        .relationType("1:N")
                        .build());
        // member <-> member_contact (1:N)
        tableRelations.add(
                SysTableRelation.builder()
                        .projectNo("company")
                        .mainTable("member")
                        .joinTable("member_contact")
                        .relationType("1:N")
                        .build());
        // info <-> company_contact (1:N)
        tableRelations.add(
                SysTableRelation.builder()
                        .projectNo("company")
                        .mainTable("info")
                        .joinTable("company_contact")
                        .relationType("1:N")
                        .build());

        when(sysTableRelationMapper.selectList(any())).thenReturn(tableRelations);
    }

    @AfterEach
    void tearDown() {
        if (mockedDb != null) {
            mockedDb.close();
        }
    }

    @Test
    @DisplayName("测试规则2：模块内部关联表为 1:1 或 N:1 时校验通过")
    void testInternalJoinedTable_1to1_or_Nto1_passes() {
        SaveModuleReq req = new SaveModuleReq();
        SaveModuleReq.SaveSysModuleReq moduleReq = new SaveModuleReq.SaveSysModuleReq();
        moduleReq.setId(1001L);
        moduleReq.setModuleCode("MOD-COMPANY");
        moduleReq.setModuleName("企业模块");
        moduleReq.setPrimaryTable("info");
        req.setModule(moduleReq);

        ModuleFieldDTO f1 = new ModuleFieldDTO();
        f1.setTableName("info");
        f1.setColumnName("company_name");

        ModuleFieldDTO f2 = new ModuleFieldDTO();
        f2.setTableName("basic_info"); // 1:1
        f2.setColumnName("reg_capital");

        ModuleFieldDTO f3 = new ModuleFieldDTO();
        f3.setTableName("company_user"); // N:1
        f3.setColumnName("user_name");

        req.setFields(Arrays.asList(f1, f2, f3));

        SysModule existing = new SysModule();
        existing.setId(1001L);
        existing.setPrimaryTable("info");
        existing.setParentId(0L);
        when(sysModuleMapper.selectById(1001L)).thenReturn(existing);

        Long savedId = sysModuleService.saveModule("company", 1L, req);
        assertEquals(1001L, savedId);
    }

    @Test
    @DisplayName("测试规则2：模块内部关联表为 1:N 时抛出 400 异常（防模块内笛卡尔积）")
    void testInternalJoinedTable_1toN_fails() {
        SaveModuleReq req = new SaveModuleReq();
        SaveModuleReq.SaveSysModuleReq moduleReq = new SaveModuleReq.SaveSysModuleReq();
        moduleReq.setId(1001L);
        moduleReq.setModuleCode("MOD-COMPANY");
        moduleReq.setModuleName("企业模块");
        moduleReq.setPrimaryTable("info");
        req.setModule(moduleReq);

        ModuleFieldDTO f1 = new ModuleFieldDTO();
        f1.setTableName("info");
        f1.setColumnName("company_name");

        ModuleFieldDTO f2 = new ModuleFieldDTO();
        f2.setTableName("member"); // 1:N (非法置于同一模块内)
        f2.setColumnName("member_name");

        req.setFields(Arrays.asList(f1, f2));

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> sysModuleService.saveModule("company", 1L, req));
        assertTrue(ex.getMessage().contains("必须为 1:1 或 N:1 关系，不能为 1:N"));
    }

    @Test
    @DisplayName("测试规则2：模块内部关联表未在 sys_table_relation 配置关系时抛出 400 异常")
    void testInternalJoinedTable_noRelation_fails() {
        SaveModuleReq req = new SaveModuleReq();
        SaveModuleReq.SaveSysModuleReq moduleReq = new SaveModuleReq.SaveSysModuleReq();
        moduleReq.setId(1001L);
        moduleReq.setModuleCode("MOD-COMPANY");
        moduleReq.setModuleName("企业模块");
        moduleReq.setPrimaryTable("info");
        req.setModule(moduleReq);

        ModuleFieldDTO f1 = new ModuleFieldDTO();
        f1.setTableName("info");
        f1.setColumnName("company_name");

        ModuleFieldDTO f2 = new ModuleFieldDTO();
        f2.setTableName("unrelated_tab"); // 未配置关联关系
        f2.setColumnName("col");

        req.setFields(Arrays.asList(f1, f2));

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> sysModuleService.saveModule("company", 1L, req));
        assertTrue(ex.getMessage().contains("未配置关联关系"));
    }

    @Test
    @DisplayName("测试规则1：父子模块主表关系为 1:1 或 1:N 时校验通过")
    void testParentChildRelation_1to1_or_1toN_passes() {
        SaveModuleReq req = new SaveModuleReq();
        SaveModuleReq.SaveSysModuleReq moduleReq = new SaveModuleReq.SaveSysModuleReq();
        moduleReq.setId(1003L);
        moduleReq.setModuleCode("MOD-MEMBER");
        moduleReq.setModuleName("成员模块");
        moduleReq.setPrimaryTable("member");
        req.setModule(moduleReq);

        // 拓扑树：1003(member) 的父节点为 1001(info)，关系为 1:N
        SaveModuleReq.ModuleTreeNodeDTO rootNode = new SaveModuleReq.ModuleTreeNodeDTO();
        rootNode.setId(1001L);
        rootNode.setParentId(0L);

        SaveModuleReq.ModuleTreeNodeDTO childNode = new SaveModuleReq.ModuleTreeNodeDTO();
        childNode.setId(1003L);
        childNode.setParentId(1001L);

        req.setTreeNodes(Arrays.asList(rootNode, childNode));

        SysModule m1001 = new SysModule();
        m1001.setId(1001L);
        m1001.setPrimaryTable("info");
        m1001.setParentId(0L);

        SysModule m1003 = new SysModule();
        m1003.setId(1003L);
        m1003.setPrimaryTable("member");
        m1003.setParentId(1001L);

        when(sysModuleMapper.selectList(any())).thenReturn(Arrays.asList(m1001, m1003));
        when(sysModuleMapper.selectById(1003L)).thenReturn(m1003);

        Long savedId = sysModuleService.saveModule("company", 1L, req);
        assertEquals(1003L, savedId);
    }

    @Test
    @DisplayName("测试规则1：父子模块主表关系为 N:1 时抛出 400 异常（防跨模块笛卡尔积）")
    void testParentChildRelation_Nto1_fails() {
        SaveModuleReq req = new SaveModuleReq();
        SaveModuleReq.SaveSysModuleReq moduleReq = new SaveModuleReq.SaveSysModuleReq();
        moduleReq.setId(1001L);
        moduleReq.setModuleCode("MOD-COMPANY");
        moduleReq.setModuleName("企业模块");
        moduleReq.setPrimaryTable("info");
        req.setModule(moduleReq);

        // 错误配置：把 1001(info) 作为 1003(member) 的子模块（member -> info 是 N:1）
        SaveModuleReq.ModuleTreeNodeDTO parentNode = new SaveModuleReq.ModuleTreeNodeDTO();
        parentNode.setId(1003L);
        parentNode.setParentId(0L);

        SaveModuleReq.ModuleTreeNodeDTO childNode = new SaveModuleReq.ModuleTreeNodeDTO();
        childNode.setId(1001L);
        childNode.setParentId(1003L);

        req.setTreeNodes(Arrays.asList(parentNode, childNode));

        SysModule m1001 = new SysModule();
        m1001.setId(1001L);
        m1001.setPrimaryTable("info");

        SysModule m1003 = new SysModule();
        m1003.setId(1003L);
        m1003.setPrimaryTable("member");

        when(sysModuleMapper.selectList(any())).thenReturn(Arrays.asList(m1001, m1003));

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> sysModuleService.saveModule("company", 1L, req));
        assertTrue(ex.getMessage().contains("必须为 1:1 或 1:N 关系，不能为 N:1"));
    }

    @Test
    @DisplayName("测试规则1：父子模块跨虚拟模块时，能正确追溯物理祖先主表并完成校验")
    void testParentChildRelation_throughVirtualParent_resolvesCorrectly() {
        SaveModuleReq req = new SaveModuleReq();
        SaveModuleReq.SaveSysModuleReq moduleReq = new SaveModuleReq.SaveSysModuleReq();
        moduleReq.setId(1006L);
        moduleReq.setModuleCode("MOD-CONTACT");
        moduleReq.setModuleName("联系方式模块");
        moduleReq.setPrimaryTable("company_contact");
        req.setModule(moduleReq);

        // 拓扑树：1001(info) -> 1020(虚拟分类，无主表) -> 1006(company_contact)
        SaveModuleReq.ModuleTreeNodeDTO node1 = new SaveModuleReq.ModuleTreeNodeDTO();
        node1.setId(1001L);
        node1.setParentId(0L);

        SaveModuleReq.ModuleTreeNodeDTO node2 = new SaveModuleReq.ModuleTreeNodeDTO();
        node2.setId(1020L);
        node2.setParentId(1001L);

        SaveModuleReq.ModuleTreeNodeDTO node3 = new SaveModuleReq.ModuleTreeNodeDTO();
        node3.setId(1006L);
        node3.setParentId(1020L);

        req.setTreeNodes(Arrays.asList(node1, node2, node3));

        SysModule m1001 = new SysModule();
        m1001.setId(1001L);
        m1001.setPrimaryTable("info");

        SysModule m1020 = new SysModule();
        m1020.setId(1020L);
        m1020.setPrimaryTable(null); // 虚拟模块

        SysModule m1006 = new SysModule();
        m1006.setId(1006L);
        m1006.setPrimaryTable("company_contact");

        when(sysModuleMapper.selectList(any())).thenReturn(Arrays.asList(m1001, m1020, m1006));
        when(sysModuleMapper.selectById(1006L)).thenReturn(m1006);

        Long savedId = sysModuleService.saveModule("company", 1L, req);
        assertEquals(1006L, savedId);
    }
}
