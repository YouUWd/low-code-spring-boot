package com.jdec.platform.externalobject.biz.service.support;

import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;

/**
 * 外部对象实时校验/查重的数据库读取入口。
 *
 * <p>直连 {@code company} 业务库，按主单（info）读取对象性质、业务编号，按人员ID读取人员关键字段。
 */
@Component
@RequiredArgsConstructor
public class ExternalObjectRealtimeRepository {

    private final DataSourceResolver dataSourceResolver;

    /** company 业务库的 DSLContext。 */
    public DSLContext dsl() {
        return dataSourceResolver.getDSLContext(DataSourceConstants.COMPANY);
    }

    /** 读取 info.nature 原始值（JSON 字符串）。 */
    public String findInfoNature(Long infoId) {
        if (infoId == null) {
            return null;
        }
        DSLContext dsl = dsl();
        return dsl.select(ExternalObjectTable.INFO.textField("nature"))
                .from(ExternalObjectTable.INFO.table())
                .where(ExternalObjectTable.INFO.longField("id").eq(infoId))
                .and(ExternalObjectTable.INFO.intField("deleted").eq(0))
                .fetchOne(ExternalObjectTable.INFO.textField("nature"));
    }

    /**
     * 按请求上下文解析当前外部对象的对象性质。
     *
     * <p>子表未传主单ID时，先通过子单行反查其 company_id。
     */
    public String findInfoNature(ExternalObjectTable table, Long companyId, Long subId) {
        Long infoId = resolveInfoId(table, companyId, subId);
        return findInfoNature(infoId);
    }

    /** 读取行记录所属的主单ID；info 直接返回自身，子表读取 company_id。 */
    public Long findCompanyId(ExternalObjectTable table, Long rowId) {
        if (rowId == null) {
            return null;
        }
        if (table == ExternalObjectTable.INFO) {
            return rowId;
        }
        DSLContext dsl = dsl();
        return dsl.select(table.longField("company_id"))
                .from(table.table())
                .where(table.longField("id").eq(rowId))
                .and(table.intField("deleted").eq(0))
                .fetchOne(table.longField("company_id"));
    }

    /** 解析当前外部对象主单ID。 */
    public Long resolveInfoId(ExternalObjectTable table, Long companyId, Long subId) {
        if (table == ExternalObjectTable.INFO) {
            return subId != null ? subId : companyId;
        }
        if (companyId != null) {
            return companyId;
        }
        return findCompanyId(table, subId);
    }

    /** 读取人员行关键字段；不存在或已删除返回 {@code null}。 */
    public MemberRow findMember(Long memberId) {
        if (memberId == null) {
            return null;
        }
        DSLContext dsl = dsl();
        Record record =
                dsl.select(
                                ExternalObjectTable.MEMBER.longField("id"),
                                ExternalObjectTable.MEMBER.textField("name"),
                                ExternalObjectTable.MEMBER.textField("mobile_phone_no"),
                                ExternalObjectTable.MEMBER.textField("identity_no"),
                                ExternalObjectTable.MEMBER.textField("type"))
                        .from(ExternalObjectTable.MEMBER.table())
                        .where(ExternalObjectTable.MEMBER.longField("id").eq(memberId))
                        .and(ExternalObjectTable.MEMBER.intField("deleted").eq(0))
                        .fetchOne();
        return record == null ? null : toMember(record);
    }

    /** 查询同一主单下的全部人员（排除指定记录），用于同一单内人员一致性校验。 */
    public List<MemberRow> findEmployees(Long companyId, Long excludeId) {
        if (companyId == null) {
            return List.of();
        }
        DSLContext dsl = dsl();
        Condition condition =
                ExternalObjectTable.MEMBER
                        .longField("company_id")
                        .eq(companyId)
                        .and(ExternalObjectTable.MEMBER.intField("deleted").eq(0));
        if (excludeId != null) {
            condition = condition.and(ExternalObjectTable.MEMBER.longField("id").ne(excludeId));
        }
        return dsl
                .select(
                        ExternalObjectTable.MEMBER.longField("id"),
                        ExternalObjectTable.MEMBER.textField("name"),
                        ExternalObjectTable.MEMBER.textField("mobile_phone_no"),
                        ExternalObjectTable.MEMBER.textField("identity_no"),
                        ExternalObjectTable.MEMBER.textField("type"))
                .from(ExternalObjectTable.MEMBER.table())
                .where(condition)
                .fetch()
                .stream()
                .map(ExternalObjectRealtimeRepository::toMember)
                .filter(member -> ExternalObjectWordValues.isEmployeeType(member.type()))
                .toList();
    }

    private static MemberRow toMember(Record record) {
        return new MemberRow(
                record.get(ExternalObjectTable.MEMBER.longField("id")),
                StringUtils.trimToEmpty(record.get(ExternalObjectTable.MEMBER.textField("name"))),
                StringUtils.trimToEmpty(
                        record.get(ExternalObjectTable.MEMBER.textField("mobile_phone_no"))),
                StringUtils.trimToEmpty(
                        record.get(ExternalObjectTable.MEMBER.textField("identity_no"))),
                record.get(ExternalObjectTable.MEMBER.textField("type")));
    }

    /**
     * 人员行关键字段。
     *
     * @param id 人员记录ID
     * @param name 姓名
     * @param mobilePhoneNo 手机号
     * @param identityNo 证件号
     * @param type 节点类型（EMPLOYEE/DEPT 或 员工/部门）
     */
    public record MemberRow(
            Long id, String name, String mobilePhoneNo, String identityNo, String type) {}
}
