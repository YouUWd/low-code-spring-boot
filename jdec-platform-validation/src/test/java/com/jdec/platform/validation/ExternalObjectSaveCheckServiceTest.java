package com.jdec.platform.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.validation.externalobject.ExternalObjectBusinessValidator;
import com.jdec.platform.validation.externalobject.ExternalObjectDuplicateValidator;
import com.jdec.platform.validation.externalobject.ExternalObjectRequiredValidator;
import com.jdec.platform.validation.externalobject.ExternalObjectSaveSnapshotConverter;
import com.jdec.platform.validation.externalobject.ExternalObjectSnapshot;
import com.jdec.platform.validation.externalobject.ExternalObjectSnapshotRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 无审批链保存校验测试：校验只基于前端提交的参数，不按主单ID查询业务库。 */
class ExternalObjectSaveCheckServiceTest {

    private static final Long INFO_MODULE_ID = 1001L;

    private static final Long BASIC_INFO_MODULE_ID = 1002L;

    private static final Long MEMBER_MODULE_ID = 1003L;

    private static final Long CONTACT_GROUP_MODULE_ID = 1020L;

    private final ExternalObjectSnapshotRepository repository =
            mock(ExternalObjectSnapshotRepository.class);

    private final ExternalObjectSaveCheckService service =
            new ExternalObjectSaveCheckService(
                    new ExternalObjectSaveSnapshotConverter(new ObjectMapper()),
                    new ExternalObjectRequiredValidator(),
                    new ExternalObjectBusinessValidator(),
                    new ExternalObjectDuplicateValidator(repository));

    /** 无主单ID且未提交基本信息：按对象性质的必填规则，基本信息全部视为未填。 */
    @Test
    void saveCheckShouldRequireBasicInfoRulesWhenMainIdAndBasicInfoAbsent() {
        NestedModuleDataDTO root =
                module(
                        INFO_MODULE_ID,
                        row(
                                "info.company_name", "测试企业",
                                "info.nature", word("企业"),
                                "info.reg_status", word("存续"),
                                "info.cooperation_types", word("客户"),
                                "info.industry_tags", word("煤炭"),
                                "info.industry_types", List.of("褐煤开采洗选")));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.check(root));

        String message = exception.getMessage();
        assertTrue(message.contains("基本信息 - 统一社会信用代码"));
        assertTrue(message.contains("基本信息 - 法定代表人/负责人"));
        assertTrue(message.contains("基本信息 - 参保人数"));
        assertTrue(message.contains("基本信息 - 注册资本"));
        assertTrue(message.contains("基本信息 - 实缴资本"));
        assertTrue(message.contains("基本信息 - 注册登记日期"));
        // 企业性质不要求身份证号
        assertFalse(message.contains("基本信息 - 身份证号"));
        assertFalse(message.contains("外部对象全称"));
    }

    /** 无主单ID、未提交基本信息且对象性质也未填：基本信息全部候选规则都视为未满足。 */
    @Test
    void saveCheckShouldRequireAllBasicInfoCandidatesWhenNatureAbsent() {
        NestedModuleDataDTO root = module(INFO_MODULE_ID, row("info.company_name", "测试企业"));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.check(root));

        String message = exception.getMessage();
        assertTrue(message.contains("对象性质"));
        assertTrue(message.contains("基本信息 - 统一社会信用代码"));
        assertTrue(message.contains("基本信息 - 身份证号"));
        assertTrue(message.contains("基本信息 - 法定代表人/负责人"));
        assertTrue(message.contains("基本信息 - 参保人数"));
        assertTrue(message.contains("基本信息 - 注册资本"));
        assertTrue(message.contains("基本信息 - 实缴资本"));
        assertTrue(message.contains("基本信息 - 注册登记日期"));
    }

    /** 无主单ID、对象性质为个人、未提交基本信息：只按个人规则要求身份证号。 */
    @Test
    void saveCheckShouldRequireOnlyIdCardWhenNatureIsIndividual() {
        NestedModuleDataDTO root =
                module(
                        INFO_MODULE_ID,
                        row(
                                "info.company_name", "测试个人",
                                "info.nature", word("个人"),
                                "info.gender", word("男"),
                                "info.cooperation_types", word("客户"),
                                "info.industry_types", List.of("其他")));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.check(root));

        String message = exception.getMessage();
        assertTrue(message.contains("基本信息 - 身份证号"));
        assertFalse(message.contains("基本信息 - 统一社会信用代码"));
        assertFalse(message.contains("基本信息 - 参保人数"));
    }

    /** 有主单ID且只提交人员模块：只校验人员模块，不校验未提交的主档与基本信息。 */
    @Test
    void saveCheckShouldOnlyCheckSubmittedModulesWhenMainIdPresent() {
        NestedModuleDataDTO member =
                module(MEMBER_MODULE_ID, row("member.id", 5L, "member.type", word("员工")));
        NestedModuleDataDTO root = module(INFO_MODULE_ID, row("info.id", 123L), member);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.check(root));

        String message = exception.getMessage();
        assertTrue(message.contains("人员构成 - 手机号码"));
        assertFalse(message.contains("外部对象全称"));
        assertFalse(message.contains("基本信息"));
    }

    /** 基本信息挂在虚模块（联系方式分组结构）下时同样能被收集。 */
    @Test
    void saveCheckShouldCollectModulesNestedUnderVirtualModule() {
        NestedModuleDataDTO basicInfo = module(BASIC_INFO_MODULE_ID, row("basic_info.id", 1L));
        NestedModuleDataDTO contactGroup =
                module(CONTACT_GROUP_MODULE_ID, new LinkedHashMap<>(), basicInfo);
        NestedModuleDataDTO root =
                module(
                        INFO_MODULE_ID,
                        row(
                                "info.id", 123L,
                                "info.company_name", "测试个人",
                                "info.nature", word("个人"),
                                "info.gender", word("男"),
                                "info.cooperation_types", word("客户"),
                                "info.industry_types", List.of("其他")),
                        contactGroup);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.check(root));

        assertTrue(exception.getMessage().contains("基本信息 - 身份证号"));
    }

    /** 提交的数据满足必填与业务规则时保存校验通过。 */
    @Test
    void saveCheckShouldPassWhenSubmittedDataSatisfiesRules() {
        NestedModuleDataDTO basicInfo =
                module(
                        BASIC_INFO_MODULE_ID,
                        row(
                                "basic_info.uscc",
                                "91110108MA01ABCD12",
                                "basic_info.legal_person",
                                "张三",
                                "basic_info.insured_count",
                                10,
                                "basic_info.reg_capital",
                                100,
                                "basic_info.paid_in_capital",
                                50,
                                "basic_info.reg_date",
                                "2020-01-01"));
        NestedModuleDataDTO root =
                module(
                        INFO_MODULE_ID,
                        row(
                                "info.id", 123L,
                                "info.company_name", "测试企业",
                                "info.nature", word("企业"),
                                "info.reg_status", word("存续"),
                                "info.cooperation_types", word("客户"),
                                "info.industry_tags", word("煤炭"),
                                "info.industry_types", List.of("褐煤开采洗选")),
                        basicInfo);

        assertDoesNotThrow(() -> service.check(root));
    }

    /** 保存校验同样执行业务规则：参保人数必须为正整数。 */
    @Test
    void saveCheckShouldValidateInsuredCount() {
        NestedModuleDataDTO basicInfo =
                module(
                        BASIC_INFO_MODULE_ID,
                        row(
                                "basic_info.uscc",
                                "91110108MA01ABCD12",
                                "basic_info.legal_person",
                                "张三",
                                "basic_info.insured_count",
                                0,
                                "basic_info.reg_capital",
                                100,
                                "basic_info.paid_in_capital",
                                50,
                                "basic_info.reg_date",
                                "2020-01-01"));
        NestedModuleDataDTO root =
                module(
                        INFO_MODULE_ID,
                        row(
                                "info.id", 123L,
                                "info.company_name", "测试企业",
                                "info.nature", word("企业"),
                                "info.reg_status", word("存续"),
                                "info.cooperation_types", word("客户"),
                                "info.industry_tags", word("煤炭"),
                                "info.industry_types", List.of("褐煤开采洗选")),
                        basicInfo);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.check(root));

        assertTrue(exception.getMessage().contains("参保人数必须为正整数"));
    }

    /** 保存校验执行查重：外部对象全称与其他已保存对象重复时中断保存。 */
    @Test
    void saveCheckShouldRejectDuplicatedCompanyName() {
        when(repository.findDuplicateInfos(any()))
                .thenReturn(
                        List.of(
                                new ExternalObjectSnapshot.InfoBrief(
                                        9L, "CR202001010000000009", "测试企业")));
        NestedModuleDataDTO root =
                module(
                        INFO_MODULE_ID,
                        row(
                                "info.id", 123L,
                                "info.company_name", "测试企业",
                                "info.nature", word("企业"),
                                "info.reg_status", word("存续"),
                                "info.cooperation_types", word("客户"),
                                "info.industry_tags", word("煤炭"),
                                "info.industry_types", List.of("褐煤开采洗选")));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.check(root));

        assertTrue(exception.getMessage().contains("外部对象全称【测试企业】已存在"));
    }

    /** 保存校验执行查重：人员手机号与其他已保存对象重复时中断保存。 */
    @Test
    void saveCheckShouldRejectDuplicatedMemberMobile() {
        when(repository.findDuplicateInfos(any()))
                .thenReturn(
                        List.of(
                                new ExternalObjectSnapshot.InfoBrief(
                                        9L, "CR202001010000000009", "其他企业")));
        when(repository.findDuplicateMembers(any()))
                .thenReturn(
                        List.of(
                                new ExternalObjectSnapshot.MemberBrief(
                                        9L, "其他企业", "王五", null, "18854878548", null)));
        NestedModuleDataDTO member =
                module(
                        MEMBER_MODULE_ID,
                        row(
                                "member.id",
                                5L,
                                "member.type",
                                word("员工"),
                                "member.name",
                                "李四",
                                "member.mobile_phone_no",
                                "18854878548"));
        NestedModuleDataDTO root = module(INFO_MODULE_ID, row("info.id", 123L), member);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.check(root));

        assertTrue(exception.getMessage().contains("手机号【18854878548】"));
    }

    /** 编辑保存时当前对象自身按主单ID、业务编号排除在查重范围之外。 */
    @Test
    void saveCheckShouldExcludeSelfFromDuplicateCheck() {
        when(repository.findDuplicateInfos(any()))
                .thenReturn(
                        List.of(
                                new ExternalObjectSnapshot.InfoBrief(
                                        9L, "CR202001010000000009", "测试企业")));
        NestedModuleDataDTO root =
                module(
                        INFO_MODULE_ID,
                        row(
                                "info.id", 9L,
                                "info.business_no", "CR202001010000000009",
                                "info.company_name", "测试企业",
                                "info.nature", word("企业"),
                                "info.reg_status", word("存续"),
                                "info.cooperation_types", word("客户"),
                                "info.industry_tags", word("煤炭"),
                                "info.industry_types", List.of("褐煤开采洗选")));

        assertDoesNotThrow(() -> service.check(root));
    }

    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            row.put((String) pairs[i], pairs[i + 1]);
        }
        return row;
    }

    private static List<Map<String, String>> word(String title) {
        return List.of(Map.of("alias_value_title", title));
    }

    private static NestedModuleDataDTO module(
            Long moduleId, Map<String, Object> row, NestedModuleDataDTO... children) {
        return NestedModuleDataDTO.builder()
                .moduleId(moduleId)
                .record(
                        List.of(
                                RecordDataDTO.builder()
                                        .row(row)
                                        .children(List.of(children))
                                        .build()))
                .build();
    }
}
