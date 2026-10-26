package com.jdec.platform.externalobject.biz.service;

import com.jdec.platform.externalobject.api.ExternalObjectRealtimeValidateApi;
import com.jdec.platform.externalobject.api.dto.request.ExternalObjectRealtimeValidateReq;
import com.jdec.platform.externalobject.api.dto.response.ExternalObjectRealtimeValidateResp;
import com.jdec.platform.externalobject.biz.service.support.ExternalObjectRealtimeRepository;
import com.jdec.platform.externalobject.biz.service.support.ExternalObjectRealtimeRepository.MemberRow;
import com.jdec.platform.externalobject.biz.service.support.ExternalObjectTable;
import com.jdec.platform.externalobject.biz.service.support.ExternalObjectWordValues;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 外部对象字段实时校验实现（不含查重）。
 *
 * <p>规则来自《外部对象实时校验》：
 *
 * <ul>
 *   <li>来自词云的字段值取 {@code alias_value_title}；
 *   <li>依赖 {@code info.nature} 的校验，在对象性质无值时跳过；
 *   <li>关联字段一律按 {@code companyId}/{@code subId} 从数据库读取，前端只传当前字段与值；
 *   <li>查重由 {@link ExternalObjectDuplicateCheckService} 独立提供。
 * </ul>
 *
 * <p>字段以 {@code table.field} 标识，未配置规则的字段默认通过。
 */
@Service
@RequiredArgsConstructor
public class ExternalObjectRealtimeValidateService implements ExternalObjectRealtimeValidateApi {

    /** 合作类型中的“监管”选项 */
    private static final String COOPERATION_SUPERVISE = "监管";

    /** 统一社会信用代码：18 位数字/大写字母，剔除 I、O、S、V、Z */
    private static final Pattern USCC_PATTERN = Pattern.compile("^[0-9A-HJ-NPQRTUWXY]{18}$");

    /** 18 位身份证号 */
    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile(
                    "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$");

    /** 正整数 */
    private static final Pattern POSITIVE_INTEGER_PATTERN = Pattern.compile("^[1-9]\\d*$");

    private static final List<String> REG_STATUS_ENTERPRISE = List.of("存续", "注销", "破产");

    private static final List<String> REG_STATUS_INDIVIDUAL_BUSINESS = List.of("存续", "注销");

    private static final List<String> REG_STATUS_INSTITUTION = List.of("存续", "注销");

    private static final List<String> REG_STATUS_SOCIAL_GROUP = List.of("存续", "注销");

    private static final List<String> REG_STATUS_AGENCY = List.of("存续", "撤销");

    private final ExternalObjectRealtimeRepository repository;

    @Override
    public ExternalObjectRealtimeValidateResp validate(ExternalObjectRealtimeValidateReq req) {
        if (req == null) {
            throw new BusinessException("校验请求不能为空");
        }
        String field = normalizeField(req.field());
        ExternalObjectTable table = ExternalObjectTable.of(field.substring(0, field.indexOf('.')));
        return switch (field) {
            case "info.cooperation_types" ->
                    validateCooperationTypes(resolveNature(req, table), req.value());
            case "info.reg_status" -> validateRegStatus(resolveNature(req, table), req.value());
            case "basic_info.uscc" -> validateUscc(resolveNature(req, table), req.value());
            case "basic_info.id_card_no" ->
                    validateIdCardNo(resolveNature(req, table), req.value());
            case "basic_info.insured_count" -> validateInsuredCount(req.value());
            case "member.name", "member.mobile_phone_no", "member.identity_no" ->
                    validateMemberConsistency(req);
            default -> ExternalObjectRealtimeValidateResp.ok();
        };
    }

    /** 规范化字段标识：去空白、转小写，并校验 {@code table.field} 格式。 */
    private static String normalizeField(String field) {
        String text = ExternalObjectWordValues.trim(field).toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            throw new BusinessException("校验字段不能为空");
        }
        int index = text.indexOf('.');
        if (index <= 0 || index >= text.length() - 1) {
            throw new BusinessException("校验字段格式错误，应为 table.field");
        }
        return text;
    }

    /** 从数据库读取当前外部对象的对象性质（info.nature）；无法定位主单时返回 null。 */
    private String resolveNature(ExternalObjectRealtimeValidateReq req, ExternalObjectTable table) {
        if (table == null) {
            return null;
        }
        return repository.findInfoNature(table, req.companyId(), req.subId());
    }

    /** 合作类型：选择“监管”时对象性质必须为机关；对象性质无值时跳过。 */
    private static ExternalObjectRealtimeValidateResp validateCooperationTypes(
            String nature, Object value) {
        if (ExternalObjectWordValues.isBlank(nature)) {
            return ExternalObjectRealtimeValidateResp.ok();
        }
        String cooperationTypes = ExternalObjectWordValues.toText(value);
        if (!ExternalObjectWordValues.containsTitle(cooperationTypes, COOPERATION_SUPERVISE)) {
            return ExternalObjectRealtimeValidateResp.ok();
        }
        return ExternalObjectWordValues.isAgencyNature(nature)
                ? ExternalObjectRealtimeValidateResp.ok()
                : ExternalObjectRealtimeValidateResp.fail("非机关不可选择监管");
    }

    /** 登记状态：按对象性质限定可选值；对象性质无值或不在规则范围内时跳过。 */
    private static ExternalObjectRealtimeValidateResp validateRegStatus(
            String nature, Object value) {
        if (ExternalObjectWordValues.isBlank(nature)) {
            return ExternalObjectRealtimeValidateResp.ok();
        }
        RegStatusRule rule = regStatusRule(nature);
        if (rule == null) {
            return ExternalObjectRealtimeValidateResp.ok();
        }
        List<String> titles =
                ExternalObjectWordValues.titles(ExternalObjectWordValues.toText(value));
        for (String title : titles) {
            boolean allowed = rule.allowed().stream().anyMatch(title::contains);
            if (!allowed) {
                return ExternalObjectRealtimeValidateResp.fail(
                        rule.nature() + "只能选择" + String.join("、", rule.allowed()));
            }
        }
        return ExternalObjectRealtimeValidateResp.ok();
    }

    /** 对象性质对应的登记状态规则；未匹配（如个人）返回 {@code null}。 */
    private static RegStatusRule regStatusRule(String nature) {
        if (ExternalObjectWordValues.containsTitle(nature, "个体工商户")) {
            return new RegStatusRule("个体工商户", REG_STATUS_INDIVIDUAL_BUSINESS);
        }
        if (ExternalObjectWordValues.containsTitle(nature, "事业单位")) {
            return new RegStatusRule("事业单位", REG_STATUS_INSTITUTION);
        }
        if (ExternalObjectWordValues.containsTitle(nature, "社会团体")) {
            return new RegStatusRule("社会团体", REG_STATUS_SOCIAL_GROUP);
        }
        if (ExternalObjectWordValues.isAgencyNature(nature)) {
            return new RegStatusRule("机关", REG_STATUS_AGENCY);
        }
        if (ExternalObjectWordValues.containsTitle(nature, "企业")) {
            return new RegStatusRule("企业", REG_STATUS_ENTERPRISE);
        }
        return null;
    }

    /**
     * 统一社会信用代码：对象性质为个人时填写身份证号，跳过；其余性质按前缀与通用格式校验。
     *
     * <p>格式正确后是否重复由查重接口负责。
     */
    private static ExternalObjectRealtimeValidateResp validateUscc(String nature, Object value) {
        if (ExternalObjectWordValues.isBlank(nature)
                || ExternalObjectWordValues.isIndividualNature(nature)) {
            return ExternalObjectRealtimeValidateResp.ok();
        }
        String uscc = compactUpperCase(firstValue(value));
        if (uscc.isEmpty()) {
            return ExternalObjectRealtimeValidateResp.ok();
        }
        if (!USCC_PATTERN.matcher(uscc).matches()) {
            return ExternalObjectRealtimeValidateResp.fail("统一社会信用代码格式错误");
        }
        String prefix = usccPrefix(nature);
        if (prefix != null && !uscc.startsWith(prefix)) {
            return ExternalObjectRealtimeValidateResp.fail("统一社会信用代码格式错误");
        }
        return ExternalObjectRealtimeValidateResp.ok();
    }

    /** 对象性质对应的统一社会信用代码前缀；未收录的性质返回 {@code null}（只校验通用格式）。 */
    private static String usccPrefix(String nature) {
        if (ExternalObjectWordValues.containsTitle(nature, "个体工商户")) {
            return "92";
        }
        if (ExternalObjectWordValues.containsTitle(nature, "事业单位")) {
            return "12";
        }
        if (ExternalObjectWordValues.containsTitle(nature, "社会团体")) {
            return "51";
        }
        if (ExternalObjectWordValues.containsTitle(nature, "律师")) {
            return "31";
        }
        if (ExternalObjectWordValues.containsTitle(nature, "村民委员会")) {
            return "54";
        }
        if (ExternalObjectWordValues.containsTitle(nature, "居民委员会")) {
            return "55";
        }
        if (ExternalObjectWordValues.isAgencyNature(nature)) {
            return "11";
        }
        if (ExternalObjectWordValues.containsTitle(nature, "企业")) {
            return "91";
        }
        return null;
    }

    /** 身份证号：仅在对象性质为个人时按正则校验。 */
    private static ExternalObjectRealtimeValidateResp validateIdCardNo(
            String nature, Object value) {
        if (ExternalObjectWordValues.isBlank(nature)
                || !ExternalObjectWordValues.isIndividualNature(nature)) {
            return ExternalObjectRealtimeValidateResp.ok();
        }
        String idCardNo = compactUpperCase(firstValue(value));
        if (idCardNo.isEmpty()) {
            return ExternalObjectRealtimeValidateResp.ok();
        }
        return ID_CARD_PATTERN.matcher(idCardNo).matches()
                ? ExternalObjectRealtimeValidateResp.ok()
                : ExternalObjectRealtimeValidateResp.fail("身份证号错误");
    }

    /** 参保人数：正整数。 */
    private static ExternalObjectRealtimeValidateResp validateInsuredCount(Object value) {
        String count = compactUpperCase(firstValue(value));
        if (count.isEmpty()) {
            return ExternalObjectRealtimeValidateResp.ok();
        }
        return POSITIVE_INTEGER_PATTERN.matcher(count).matches()
                ? ExternalObjectRealtimeValidateResp.ok()
                : ExternalObjectRealtimeValidateResp.fail("参保人数必须为正整数");
    }

    /**
     * 同一主单下人员信息一致性校验。
     *
     * <p>当前人员的姓名、手机号、证件号三者都有值时才校验；与同一主单下其它员工逐一比对， 命中“两者相同、第三者不同”时给出提示，并排除当前记录。
     */
    private ExternalObjectRealtimeValidateResp validateMemberConsistency(
            ExternalObjectRealtimeValidateReq req) {
        Long companyId =
                repository.resolveInfoId(ExternalObjectTable.MEMBER, req.companyId(), req.subId());
        if (companyId == null) {
            return ExternalObjectRealtimeValidateResp.ok();
        }
        String type = memberType(req);
        if (!ExternalObjectWordValues.isBlank(type)
                && !ExternalObjectWordValues.isEmployeeType(type)) {
            return ExternalObjectRealtimeValidateResp.ok();
        }
        String name = memberValue(req, "name");
        String mobilePhoneNo = memberValue(req, "mobile_phone_no");
        String identityNo = memberValue(req, "identity_no");
        if (name.isEmpty() || mobilePhoneNo.isEmpty() || identityNo.isEmpty()) {
            return ExternalObjectRealtimeValidateResp.ok();
        }
        String normalizedMobile = normalizePhone(mobilePhoneNo);
        String normalizedIdentity = normalizeIdentity(identityNo);
        for (MemberRow other : repository.findEmployees(companyId, req.subId())) {
            String otherName = ExternalObjectWordValues.trim(other.name());
            String otherMobile = normalizePhone(other.mobilePhoneNo());
            String otherIdentity = normalizeIdentity(other.identityNo());
            if (otherName.isEmpty() || otherMobile.isEmpty() || otherIdentity.isEmpty()) {
                continue;
            }
            boolean sameName = name.equals(otherName);
            boolean sameMobile = normalizedMobile.equals(otherMobile);
            boolean sameIdentity = normalizedIdentity.equals(otherIdentity);
            if (sameName && sameMobile && !sameIdentity) {
                return ExternalObjectRealtimeValidateResp.fail("姓名和手机号相同，证件号不同！");
            }
            if (sameName && sameIdentity && !sameMobile) {
                return ExternalObjectRealtimeValidateResp.fail("姓名和证件号相同，手机号不同！");
            }
            if (sameMobile && sameIdentity && !sameName) {
                return ExternalObjectRealtimeValidateResp.fail("手机号和证件号相同，姓名不同！");
            }
        }
        return ExternalObjectRealtimeValidateResp.ok();
    }

    /** 从数据库读取当前人员类型；记录不存在时返回 null。 */
    private String memberType(ExternalObjectRealtimeValidateReq req) {
        MemberRow member = repository.findMember(req.subId());
        return member == null ? null : member.type();
    }

    /** 读取当前人员字段值：当前编辑字段取请求值，其余从数据库人员行补齐。 */
    private String memberValue(ExternalObjectRealtimeValidateReq req, String column) {
        String fieldKey = "member." + column;
        if (fieldKey.equalsIgnoreCase(ExternalObjectWordValues.trim(req.field()))) {
            return ExternalObjectWordValues.trim(ExternalObjectWordValues.toText(req.value()));
        }
        MemberRow member = repository.findMember(req.subId());
        if (member == null) {
            return "";
        }
        return ExternalObjectWordValues.trim(
                switch (column) {
                    case "name" -> member.name();
                    case "mobile_phone_no" -> member.mobilePhoneNo();
                    case "identity_no" -> member.identityNo();
                    default -> "";
                });
    }

    /** 取字段真实值：词云 JSON 取 {@code alias_value_title}，普通文本取原值。 */
    private static String firstValue(Object value) {
        List<String> titles =
                ExternalObjectWordValues.titles(ExternalObjectWordValues.toText(value));
        return titles.isEmpty() ? "" : titles.get(0);
    }

    private static String compactUpperCase(String value) {
        return value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private static String normalizePhone(String value) {
        return ExternalObjectWordValues.compact(value);
    }

    private static String normalizeIdentity(String value) {
        return ExternalObjectWordValues.compact(value).toUpperCase(Locale.ROOT);
    }

    /**
     * 对象性质对应的登记状态规则。
     *
     * @param nature 对象性质名称（用于提示语）
     * @param allowed 允许的登记状态
     */
    private record RegStatusRule(String nature, List<String> allowed) {}
}
