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

    private static ExternalObjectValidationMessages messages() {
        return new ExternalObjectValidationMessages(ExternalObjectCheckStage.DUPLICATE);
    }

    private static ExternalObjectSnapshot snapshot(ExternalObjectSnapshot.MemberRow member) {
        return new ExternalObjectSnapshot(
                new ExternalObjectSnapshot.InfoRow(
                        COMPANY_ID,
                        "YW001",
                        1,
                        SUBJECT_ID,
                        null,
                        "甲公司",
                        null,
                        "企业",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                null,
                List.of(member),
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
