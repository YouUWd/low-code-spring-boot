package com.jdec.platform.validation.externalobject;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jdec.platform.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 人员构成必填：公司节点行是组织架构根节点，不是人员行，不能要求手机号码。 */
class ExternalObjectRequiredValidatorTest {

    private final ExternalObjectRequiredValidator validator = new ExternalObjectRequiredValidator();

    /** 企业性质：公司节点行（company）没有手机号不参与校验，部门行、员工行照常校验。 */
    @Test
    void shouldSkipCompanyNodeMobilePhone() {
        ExternalObjectValidationMessages messages = messages();
        validator.validate(
                snapshot(
                        List.of(
                                member("company", "甲公司", null),
                                member("department", "人力", null),
                                member("employee", "张三", "13800000000"))),
                messages);

        assertDoesNotThrow(messages::throwIfNotEmpty);
    }

    /** 企业性质：公司节点行的类型可能是中文“公司”，同样不校验手机号码。 */
    @Test
    void shouldSkipChineseCompanyNodeMobilePhone() {
        ExternalObjectValidationMessages messages = messages();
        validator.validate(snapshot(List.of(member("公司", "甲公司", null))), messages);

        assertDoesNotThrow(messages::throwIfNotEmpty);
    }

    /** 员工行没有手机号仍然提示必填。 */
    @Test
    void shouldStillRequireEmployeeMobilePhone() {
        ExternalObjectValidationMessages messages = messages();
        validator.validate(snapshot(List.of(member("employee", "张三", null))), messages);

        BusinessException exception =
                assertThrows(BusinessException.class, messages::throwIfNotEmpty);
        assertTrue(exception.getMessage().contains("人员构成 - 手机号码"));
    }

    private static ExternalObjectValidationMessages messages() {
        return new ExternalObjectValidationMessages(ExternalObjectCheckStage.REQUIRED);
    }

    /** 企业性质、其余必填项均已填写的整单数据。 */
    private static ExternalObjectSnapshot snapshot(List<ExternalObjectSnapshot.MemberRow> members) {
        return new ExternalObjectSnapshot(
                new ExternalObjectSnapshot.InfoRow(
                        1L, "YW001", 1, null, null, "甲公司", null, "企业", "存续", "客户", "行业标签", "行业类型",
                        null, 1),
                new ExternalObjectSnapshot.BasicInfoRow(
                        "91330000MA2XXXXXXX",
                        null,
                        "李四",
                        5,
                        BigDecimal.TEN,
                        BigDecimal.ONE,
                        LocalDate.of(2020, 1, 1),
                        null,
                        null,
                        null),
                members,
                new ExternalObjectSnapshot.AddressRow("浙江省杭州市", "文三路 1 号", "浙江省杭州市", "文三路 1 号"),
                List.of(),
                List.of(),
                List.of(),
                new ExternalObjectSnapshot.CreditRatingRow("默认", "80"));
    }

    private static ExternalObjectSnapshot.MemberRow member(
            String type, String name, String mobilePhoneNo) {
        return new ExternalObjectSnapshot.MemberRow(null, type, name, null, mobilePhoneNo, null);
    }
}
