package com.jdec.platform.validation.externalobject;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jdec.platform.shared.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 整单查重：人员组合判断必须合并库中同一主单的人员行，不能只看前端提交的数据。 */
class ExternalObjectDuplicateValidatorTest {

    private static final Long COMPANY_ID = 10L;

    private static final Long SUBJECT_ID = 9L;

    private final ExternalObjectSnapshotRepository repository =
            mock(ExternalObjectSnapshotRepository.class);

    private final ExternalObjectDuplicateValidator validator =
            new ExternalObjectDuplicateValidator(repository);

    /** 新增行与库中未提交的人员行三同：也要提示“是否继续添加”。 */
    @Test
    void shouldDetectDuplicateAgainstStoredMembers() {
        givenNoOtherObjects();
        when(repository.findMembersOfCompany(anyLong()))
                .thenReturn(List.of(member(1L, "张三", "13800000000", "110101199003071234")));

        ExternalObjectValidationMessages messages = messages();
        validator.validate(
                snapshot(member(null, "张三", "13800000000", "110101199003071234")), messages);
        BusinessException exception =
                assertThrows(BusinessException.class, messages::throwIfNotEmpty);

        verify(repository).findMembersOfCompany(COMPANY_ID);
        assertEquals("姓名【张三】的人员信息在外部对象【甲公司】中已存在，是否继续添加？", exception.getMessage());
    }

    /** 更新行按行ID覆盖库中旧值：库中旧姓名不参与比对，不产生误报。 */
    @Test
    void shouldOverrideStoredRowById() {
        givenNoOtherObjects();
        when(repository.findMembersOfCompany(anyLong()))
                .thenReturn(List.of(member(1L, "张三", "13800000000", "110101199003071234")));

        ExternalObjectValidationMessages messages = messages();
        validator.validate(
                snapshot(member(1L, "李四", "13800000000", "110101199003071234")), messages);

        assertDoesNotThrow(messages::throwIfNotEmpty);
    }

    private void givenNoOtherObjects() {
        when(repository.findDuplicateInfos(SUBJECT_ID)).thenReturn(List.of());
    }

    /** 变更申请单保存：原单据及其同业务编号的其它版本都不参与查重比对。 */
    @Test
    void shouldExcludeSourceCompanyAndItsVersionsOnChangeApply() {
        when(repository.findDuplicateInfos(SUBJECT_ID))
                .thenReturn(
                        List.of(
                                infoBrief(COMPANY_ID, "YW001", "甲公司"),
                                infoBrief(11L, "YW001", "甲公司"),
                                infoBrief(20L, "YW002", "乙公司")));

        ExternalObjectValidationMessages messages = messages();
        validator.validate(
                snapshot(
                        null,
                        null,
                        "甲公司",
                        List.of(member(null, "张三", "13800000000", "110101199003071234"))),
                messages,
                COMPANY_ID);

        assertDoesNotThrow(messages::throwIfNotEmpty);
        // 人员行合并仍按原单据查库
        verify(repository).findMembersOfCompany(COMPANY_ID);
    }

    /** 变更申请单保存：除原单据外，其它已保存对象仍然参与查重。 */
    @Test
    void shouldStillDetectOtherObjectsOnChangeApply() {
        when(repository.findDuplicateInfos(SUBJECT_ID))
                .thenReturn(
                        List.of(
                                infoBrief(COMPANY_ID, "YW001", "甲公司"),
                                infoBrief(20L, "YW002", "乙公司")));

        ExternalObjectValidationMessages messages = messages();
        validator.validate(snapshot(null, null, "乙公司", List.of()), messages, COMPANY_ID);
        BusinessException exception =
                assertThrows(BusinessException.class, messages::throwIfNotEmpty);

        assertEquals("外部对象全称【乙公司】已存在，请核对后重新填写!", exception.getMessage());
    }

    private static ExternalObjectSnapshot.InfoBrief infoBrief(
            Long id, String businessNo, String companyName) {
        return new ExternalObjectSnapshot.InfoBrief(id, businessNo, companyName);
    }

    private static ExternalObjectValidationMessages messages() {
        return new ExternalObjectValidationMessages(ExternalObjectCheckStage.DUPLICATE);
    }

    private static ExternalObjectSnapshot snapshot(ExternalObjectSnapshot.MemberRow member) {
        return snapshot(COMPANY_ID, "YW001", "甲公司", List.of(member));
    }

    private static ExternalObjectSnapshot snapshot(
            Long id,
            String businessNo,
            String companyName,
            List<ExternalObjectSnapshot.MemberRow> members) {
        return new ExternalObjectSnapshot(
                new ExternalObjectSnapshot.InfoRow(
                        id,
                        businessNo,
                        1,
                        SUBJECT_ID,
                        null,
                        companyName,
                        null,
                        "企业",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                null,
                members,
                null,
                List.of(),
                List.of(),
                List.of(),
                null);
    }

    private static ExternalObjectSnapshot.MemberRow member(
            Long id, String name, String mobilePhoneNo, String identityNo) {
        return new ExternalObjectSnapshot.MemberRow(
                id, "EMPLOYEE", name, null, mobilePhoneNo, identityNo);
    }
}
