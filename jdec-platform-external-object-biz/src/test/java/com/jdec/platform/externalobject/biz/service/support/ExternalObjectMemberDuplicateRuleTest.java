package com.jdec.platform.externalobject.biz.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** {@link ExternalObjectMemberDuplicateRule} 的人员组合查重规则。 */
class ExternalObjectMemberDuplicateRuleTest {

    private static final String COMPANY = "甲公司";

    @Test
    void samePersonHitsNameRule() {
        String message =
                ExternalObjectMemberDuplicateRule.message(
                        "member.name",
                        "张三",
                        "13800000000",
                        "110101199003071234",
                        "张三",
                        "13800000000",
                        "110101199003071234",
                        COMPANY);

        assertEquals("姓名【张三】的人员信息在外部对象【甲公司】中已存在，是否继续添加？", message);
    }

    @Test
    void sameMobileWithDifferentIdentityHitsMobileRule() {
        String message =
                ExternalObjectMemberDuplicateRule.message(
                        "member.mobile_phone_no",
                        "张三",
                        "13800000000",
                        "110101199003071234",
                        "张三",
                        "13800000000",
                        "110101199003075678",
                        COMPANY);

        assertEquals("手机号【13800000000】在外部对象【甲公司】中已存在，请核对后重新填写！", message);
    }

    @Test
    void sameIdentityWithDifferentNameHitsIdentityRule() {
        String message =
                ExternalObjectMemberDuplicateRule.message(
                        "member.identity_no",
                        "张三",
                        "13800000000",
                        "110101199003071234",
                        "李四",
                        "13900000000",
                        "110101199003071234",
                        COMPANY);

        assertEquals("证件号【110101199003071234】在外部对象【甲公司】中已存在，请核对后重新填写！", message);
    }

    @Test
    void nameRuleIgnoresDifferentPerson() {
        assertNull(
                ExternalObjectMemberDuplicateRule.message(
                        "member.name",
                        "张三",
                        "13800000000",
                        "110101199003071234",
                        "张三",
                        "13800000000",
                        "110101199003075678",
                        COMPANY));
    }

    @Test
    void blankFieldNeverMatches() {
        assertNull(
                ExternalObjectMemberDuplicateRule.message(
                        "member.name", "", "", "", "", "", "", COMPANY));

        String message =
                ExternalObjectMemberDuplicateRule.message(
                        "member.mobile_phone_no",
                        "",
                        "13800000000",
                        "",
                        "",
                        "13800000000",
                        "",
                        COMPANY);
        assertEquals("手机号【13800000000】在外部对象【甲公司】中已存在，请核对后重新填写！", message);
    }

    @Test
    void sameMobileAndIdentityButDifferentNameHitsBothRules() {
        String mobileMessage =
                ExternalObjectMemberDuplicateRule.message(
                        "member.mobile_phone_no",
                        "张三",
                        "13800000000",
                        "110101199003071234",
                        "李四",
                        "13800000000",
                        "110101199003071234",
                        COMPANY);
        String identityMessage =
                ExternalObjectMemberDuplicateRule.message(
                        "member.identity_no",
                        "张三",
                        "13800000000",
                        "110101199003071234",
                        "李四",
                        "13800000000",
                        "110101199003071234",
                        COMPANY);

        assertEquals("手机号【13800000000】在外部对象【甲公司】中已存在，请核对后重新填写！", mobileMessage);
        assertEquals("证件号【110101199003071234】在外部对象【甲公司】中已存在，请核对后重新填写！", identityMessage);
    }

    @Test
    void normalizeRemovesBlankAndNormalizesIdentityCase() {
        assertEquals("张三", ExternalObjectMemberDuplicateRule.normalizeName(" 张三 "));
        assertEquals(
                "13800000000",
                ExternalObjectMemberDuplicateRule.normalizeMobilePhoneNo("138 0000 0000"));
        assertEquals(
                "11010119900307123X",
                ExternalObjectMemberDuplicateRule.normalizeIdentityNo(" 11010119900307123x "));
    }

    @Test
    void objectNameFallsBackWhenBlank() {
        assertEquals("其他外部对象", ExternalObjectMemberDuplicateRule.objectName("  ", "其他外部对象"));
        assertEquals(COMPANY, ExternalObjectMemberDuplicateRule.objectName(COMPANY, "其他外部对象"));
    }
}
