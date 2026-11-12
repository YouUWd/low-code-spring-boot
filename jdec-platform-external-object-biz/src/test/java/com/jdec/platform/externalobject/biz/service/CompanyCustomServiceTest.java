package com.jdec.platform.externalobject.biz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.SysMenuApi;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.externalobject.api.ExternalObjectRealtimeValidateApi;
import com.jdec.platform.externalobject.api.dto.response.MemberContactResp;
import com.jdec.platform.externalobject.api.dto.response.MemberTreeNodeResp;
import com.jdec.platform.externalobject.biz.entity.Member;
import com.jdec.platform.externalobject.biz.entity.MemberContact;
import com.jdec.platform.externalobject.biz.mapper.*;
import com.jdec.platform.externalobject.biz.service.impl.CompanyCustomServiceImpl;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyCustomServiceTest {

    @Mock private InfoMapper infoMapper;
    @Mock private BasicInfoMapper basicInfoMapper;
    @Mock private MemberMapper memberMapper;
    @Mock private MemberContactMapper memberContactMapper;
    @Mock private AddressMapper addressMapper;
    @Mock private CompanyContactMapper companyContactMapper;
    @Mock private DigitalAccountMapper digitalAccountMapper;
    @Mock private FinancialAccountMapper financialAccountMapper;
    @Mock private CreditRatingMapper creditRatingMapper;
    @Mock private ExternalObjectRealtimeValidateApi realtimeValidateApi;
    @Mock private ConfigApi configApi;
    @Mock private SysMenuApi sysMenuApi;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CompanyCustomServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new CompanyCustomServiceImpl(
                        infoMapper,
                        basicInfoMapper,
                        memberMapper,
                        memberContactMapper,
                        addressMapper,
                        companyContactMapper,
                        digitalAccountMapper,
                        financialAccountMapper,
                        creditRatingMapper,
                        realtimeValidateApi,
                        configApi,
                        sysMenuApi,
                        objectMapper);
    }

    @Test
    @DisplayName("companyId为空时应抛出IllegalArgumentException")
    void getMemberTree_nullCompanyId_throwsException() {
        assertThatThrownBy(() -> service.getMemberTree(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("公司 ID (companyId) 不能为空");
    }

    @Test
    @DisplayName("没有成员数据时应返回空列表")
    void getMemberTree_noMembers_returnsEmptyList() {
        when(memberMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<MemberTreeNodeResp> tree = service.getMemberTree(100L);

        assertThat(tree).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("正常构建树形结构并挂载人员联系方式及JSON字段解析")
    void getMemberTree_success() {
        Long companyId = 100L;

        // 构造组织人员数据：
        // 顶级部门: node-1 (研发部)
        // 子部门: node-2 (后端组, parent: node-1)
        // 员工: node-3 (张三, parent: node-2, id: 201)
        // 独立部门/员工 (旧数据未分配 node_id): id: 301
        Map<String, Object> deptLeaderJson = Map.of("name", "李主管", "phone", "13800000000");
        Map<String, Object> contactTypeJson =
                Map.of("alias_value_title", "手机", "alias_value_no", "W12345");

        Member dept1 =
                new Member()
                        .setId(101L)
                        .setCompanyId(companyId)
                        .setType("department")
                        .setName("研发部")
                        .setCode("D01")
                        .setNodeId("node-1")
                        .setParentNodeId(null)
                        .setDeptLeader(deptLeaderJson)
                        .setSortOrder(1)
                        .setDeleted(0);

        Member dept2 =
                new Member()
                        .setId(102L)
                        .setCompanyId(companyId)
                        .setType("department")
                        .setName("后端组")
                        .setCode("D0101")
                        .setNodeId("node-2")
                        .setParentNodeId("node-1")
                        .setSortOrder(1)
                        .setDeleted(0);

        Member emp1 =
                new Member()
                        .setId(201L)
                        .setCompanyId(companyId)
                        .setType("employee")
                        .setName("张三")
                        .setCode("EMP001")
                        .setPosition("Java开发工程师")
                        .setGender("男")
                        .setNodeId("node-3")
                        .setParentNodeId("node-2")
                        .setSortOrder(1)
                        .setDeleted(0);

        Member legacyEmp =
                new Member()
                        .setId(301L)
                        .setCompanyId(companyId)
                        .setType("employee")
                        .setName("老员工")
                        .setCode("EMP999")
                        .setNodeId("")
                        .setParentNodeId(null)
                        .setSortOrder(99)
                        .setDeleted(0);

        when(memberMapper.selectList(any())).thenReturn(List.of(dept1, dept2, emp1, legacyEmp));

        // 构造联系方式数据
        MemberContact contact1 =
                new MemberContact()
                        .setId(1001L)
                        .setMemberId(201L)
                        .setContactType(contactTypeJson)
                        .setContactValue("13812345678")
                        .setIsPrimary(1)
                        .setSortOrder(1)
                        .setDeleted(0);

        MemberContact contact2 =
                new MemberContact()
                        .setId(1002L)
                        .setMemberId(201L)
                        .setContactType("邮箱")
                        .setContactValue("zhangsan@jdec.com")
                        .setIsPrimary(0)
                        .setSortOrder(2)
                        .setDeleted(0);

        when(memberContactMapper.selectList(any())).thenReturn(List.of(contact1, contact2));

        // 执行调用
        List<MemberTreeNodeResp> tree = service.getMemberTree(companyId);

        // 验证结果结构
        assertThat(tree).hasSize(2); // 研发部 (node-1) 与 老员工 (legacyEmp)

        // 验证顶级部门 研发部
        MemberTreeNodeResp rootDept = tree.get(0);
        assertThat(rootDept.getId()).isEqualTo(101L);
        assertThat(rootDept.getName()).isEqualTo("研发部");
        assertThat(rootDept.getDeptLeader()).isEqualTo(deptLeaderJson);
        assertThat(rootDept.getChildren()).hasSize(1);

        // 验证子部门 后端组
        MemberTreeNodeResp subDept = rootDept.getChildren().get(0);
        assertThat(subDept.getId()).isEqualTo(102L);
        assertThat(subDept.getName()).isEqualTo("后端组");
        assertThat(subDept.getChildren()).hasSize(1);

        // 验证员工 张三
        MemberTreeNodeResp empNode = subDept.getChildren().get(0);
        assertThat(empNode.getId()).isEqualTo(201L);
        assertThat(empNode.getName()).isEqualTo("张三");
        assertThat(empNode.getPosition()).isEqualTo("Java开发工程师");
        assertThat(empNode.getGender()).isEqualTo("男");

        // 验证员工联系方式
        List<MemberContactResp> contacts = empNode.getContacts();
        assertThat(contacts).hasSize(2);
        assertThat(contacts.get(0).getContactValue()).isEqualTo("13812345678");
        assertThat(contacts.get(0).getContactType()).isEqualTo(contactTypeJson);
        assertThat(contacts.get(0).getIsPrimary()).isEqualTo(1);
        assertThat(contacts.get(1).getContactValue()).isEqualTo("zhangsan@jdec.com");

        // 验证平铺老员工
        MemberTreeNodeResp legacyNode = tree.get(1);
        assertThat(legacyNode.getId()).isEqualTo(301L);
        assertThat(legacyNode.getName()).isEqualTo("老员工");
        assertThat(legacyNode.getContacts()).isEmpty();
    }
}
