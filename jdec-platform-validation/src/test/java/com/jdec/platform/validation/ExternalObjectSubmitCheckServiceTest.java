package com.jdec.platform.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

/** 主单ID没有值时的提交校验测试：基于前端提交的数据校验，缺失模块按校验不通过处理。 */
class ExternalObjectSubmitCheckServiceTest {

    private static final Long INFO_MODULE_ID = 1001L;

    private static final Long BASIC_INFO_MODULE_ID = 1002L;

    private static final Long MEMBER_MODULE_ID = 1003L;

    private static final Long ADDRESS_MODULE_ID = 1005L;

    private static final Long COMPANY_CONTACT_MODULE_ID = 1006L;

    private static final Long FINANCIAL_ACCOUNT_MODULE_ID = 1008L;

    private static final Long DIGITAL_ACCOUNT_MODULE_ID = 1009L;

    private static final Long CREDIT_RATING_MODULE_ID = 1010L;

    private final ExternalObjectSnapshotRepository repository =
            mock(ExternalObjectSnapshotRepository.class);

    private final ExternalObjectSubmitCheckService service =
            new ExternalObjectSubmitCheckService(
                    repository,
                    new ExternalObjectSaveSnapshotConverter(new ObjectMapper()),
                    new ExternalObjectRequiredValidator(),
                    new ExternalObjectBusinessValidator(),
                    new ExternalObjectDuplicateValidator(repository));

    /** 主单ID没有值且只提交主档部分字段：未提交的模块按该模块校验全部不通过处理。 */
    @Test
    void submitCheckShouldTreatMissingModulesAsFailed() {
        NestedModuleDataDTO root = module(INFO_MODULE_ID, row("info.company_name", "测试企业"));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.checkOnSubmit(root));

        String message = exception.getMessage();
        assertTrue(message.contains("对象性质"));
        // 基本信息、信用等级未提交，按一行空值校验
        assertTrue(message.contains("基本信息 - 统一社会信用代码"));
        assertTrue(message.contains("信用等级 - 内部信用等级"));
        // 一对多子表模块未提交，模块下的必填项全部不通过
        assertTrue(message.contains("人员构成 - 类型"));
        assertTrue(message.contains("人员构成 - 手机号码"));
        assertTrue(message.contains("人员构成 - 其他联系方式 - 联系方式"));
        assertTrue(message.contains("人员构成 - 其他联系方式 - 号码"));
        assertTrue(message.contains("财务信息 - 银行卡 - 账号状态"));
        assertTrue(message.contains("财务信息 - 电子账户 - 账号状态"));
    }

    /** 主单ID没有值且基本信息、联系方式、财务信息未提交：一对一与一对多模块都参与校验。 */
    @Test
    void submitCheckShouldRequireAllMissingModulesWhenMainIdAbsent() {
        NestedModuleDataDTO root =
                module(INFO_MODULE_ID, enterpriseInfoRow("测试企业"), completeBasicInfo());

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.checkOnSubmit(root));

        String message = exception.getMessage();
        // 联系方式（一对一）、信用等级（一对一）未提交，按规则提示
        assertTrue(message.contains("联系方式 - 通讯地址"));
        assertTrue(message.contains("联系方式 - 注册地址"));
        assertTrue(message.contains("信用等级 - 内部信用等级"));
        assertTrue(message.contains("信用等级 - 企查查评分"));
        // 人员构成、联系方式、财务信息（一对多）未提交，模块下的必填项全部不通过
        assertTrue(message.contains("人员构成 - 类型"));
        assertTrue(message.contains("人员构成 - 手机号码"));
        assertTrue(message.contains("人员构成 - 其他联系方式 - 联系方式"));
        assertTrue(message.contains("财务信息 - 银行卡 - 账号状态"));
        assertTrue(message.contains("财务信息 - 电子账户 - 账号状态"));
    }

    /** 子模块有记录时，逐条记录校验每个需要校验的字段规则。 */
    @Test
    void submitCheckShouldValidateEveryRecordOfSubmittedModules() {
        NestedModuleDataDTO members =
                module(
                        MEMBER_MODULE_ID,
                        row(
                                "member.type", word("员工"),
                                "member.name", "李四",
                                "member.mobile_phone_no", "18854878548"),
                        row("member.type", word("员工"), "member.name", "王五"));
        NestedModuleDataDTO companyContacts =
                module(
                        COMPANY_CONTACT_MODULE_ID,
                        row("company_contact.contact_type", word("手机")),
                        row(
                                "company_contact.contact_name", "张三",
                                "company_contact.contact_type", word("手机"),
                                "company_contact.contact_value", "13800000000"));
        NestedModuleDataDTO root =
                module(
                        INFO_MODULE_ID,
                        enterpriseInfoRow("测试企业"),
                        completeBasicInfo(),
                        members,
                        address(),
                        companyContacts,
                        module(
                                FINANCIAL_ACCOUNT_MODULE_ID,
                                row("financial_account.account_status", word("正常"))),
                        module(
                                DIGITAL_ACCOUNT_MODULE_ID,
                                row("digital_account.account_status", word("正常"))),
                        creditRating());

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.checkOnSubmit(root));

        String message = exception.getMessage();
        // 第二条人员记录缺少手机号码
        assertTrue(message.contains("人员构成 - 手机号码"));
        // 第一条联系方式记录缺少号码
        assertTrue(message.contains("人员构成 - 其他联系方式 - 号码"));
    }

    /** 所有需要校验的模块都满足规则时提交校验通过。 */
    @Test
    void submitCheckShouldPassWhenAllRequiredModulesSatisfied() {
        NestedModuleDataDTO root =
                module(
                        INFO_MODULE_ID,
                        enterpriseInfoRow("测试企业"),
                        completeBasicInfo(),
                        module(
                                MEMBER_MODULE_ID,
                                row(
                                        "member.type", word("员工"),
                                        "member.name", "李四",
                                        "member.mobile_phone_no", "18854878548")),
                        address(),
                        module(
                                COMPANY_CONTACT_MODULE_ID,
                                row(
                                        "company_contact.contact_type",
                                        word("手机"),
                                        "company_contact.contact_value",
                                        "13800000000")),
                        module(
                                FINANCIAL_ACCOUNT_MODULE_ID,
                                row("financial_account.account_status", word("正常"))),
                        module(
                                DIGITAL_ACCOUNT_MODULE_ID,
                                row("digital_account.account_status", word("正常"))),
                        creditRating());

        when(repository.findDuplicateInfos(any())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.checkOnSubmit(root));
    }

    /** 提交校验同样执行查重：主单ID没有值时按已保存对象比对。 */
    @Test
    void submitCheckShouldRejectDuplicatedCompanyName() {
        when(repository.findDuplicateInfos(any()))
                .thenReturn(
                        List.of(
                                new ExternalObjectSnapshot.InfoBrief(
                                        9L, "CR202001010000000009", "测试企业")));
        NestedModuleDataDTO root =
                module(
                        INFO_MODULE_ID,
                        enterpriseInfoRow("测试企业"),
                        completeBasicInfo(),
                        module(
                                MEMBER_MODULE_ID,
                                row(
                                        "member.type", word("员工"),
                                        "member.name", "李四",
                                        "member.mobile_phone_no", "18854878548")),
                        address(),
                        module(
                                COMPANY_CONTACT_MODULE_ID,
                                row(
                                        "company_contact.contact_type",
                                        word("手机"),
                                        "company_contact.contact_value",
                                        "13800000000")),
                        module(
                                FINANCIAL_ACCOUNT_MODULE_ID,
                                row("financial_account.account_status", word("正常"))),
                        module(
                                DIGITAL_ACCOUNT_MODULE_ID,
                                row("digital_account.account_status", word("正常"))),
                        creditRating());

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.checkOnSubmit(root));

        assertTrue(exception.getMessage().contains("外部对象全称【测试企业】已存在"));
    }

    private static Map<String, Object> enterpriseInfoRow(String companyName) {
        return row(
                "info.company_name", companyName,
                "info.nature", word("企业"),
                "info.reg_status", word("存续"),
                "info.cooperation_types", word("客户"),
                "info.industry_tags", word("煤炭"),
                "info.industry_types", List.of("褐煤开采洗选"));
    }

    private static NestedModuleDataDTO completeBasicInfo() {
        return module(
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
    }

    private static NestedModuleDataDTO address() {
        return module(
                ADDRESS_MODULE_ID,
                row(
                        "address.comm_region", "北京市朝阳区",
                        "address.comm_detail", "测试路 1 号",
                        "address.reg_region", "北京市朝阳区",
                        "address.reg_detail", "测试路 2 号"));
    }

    private static NestedModuleDataDTO creditRating() {
        return module(
                CREDIT_RATING_MODULE_ID,
                row("credit_rating.internal_level", word("默认"), "credit_rating.qcc_score", "95"));
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
        return moduleWithRows(moduleId, List.of(row), children);
    }

    private static NestedModuleDataDTO module(
            Long moduleId, Map<String, Object> firstRow, Map<String, Object> secondRow) {
        return moduleWithRows(moduleId, List.of(firstRow, secondRow));
    }

    private static NestedModuleDataDTO moduleWithRows(
            Long moduleId, List<Map<String, Object>> rows, NestedModuleDataDTO... children) {
        List<RecordDataDTO> records =
                rows.stream()
                        .map(
                                row ->
                                        RecordDataDTO.builder()
                                                .row(row)
                                                .children(List.of(children))
                                                .build())
                        .toList();
        return NestedModuleDataDTO.builder().moduleId(moduleId).record(records).build();
    }
}
