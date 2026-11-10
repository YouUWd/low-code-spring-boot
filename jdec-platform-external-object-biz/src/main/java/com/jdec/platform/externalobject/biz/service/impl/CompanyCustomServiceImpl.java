package com.jdec.platform.externalobject.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.SysMenuApi;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.response.SysMenuResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.externalobject.api.ExternalObjectRealtimeValidateApi;
import com.jdec.platform.externalobject.api.dto.request.CompanyBadgeNumReq;
import com.jdec.platform.externalobject.api.dto.request.CompanyRegStatusUpdateReq;
import com.jdec.platform.externalobject.api.dto.request.ExternalObjectRealtimeValidateReq;
import com.jdec.platform.externalobject.api.dto.response.CompanyTreeNodeResp;
import com.jdec.platform.externalobject.api.dto.response.ExternalObjectRealtimeValidateResp;
import com.jdec.platform.externalobject.api.dto.response.MemberContactResp;
import com.jdec.platform.externalobject.api.dto.response.MemberTreeNodeResp;
import com.jdec.platform.externalobject.biz.entity.*;
import com.jdec.platform.externalobject.biz.mapper.*;
import com.jdec.platform.externalobject.biz.service.CompanyCustomService;
import com.jdec.platform.externalobject.biz.service.support.ExternalObjectWordValues;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/** 外部对象特定业务定制服务实现类 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.COMPANY)
public class CompanyCustomServiceImpl implements CompanyCustomService {

    /** 业务状态：启用 (99) */
    public static final int STATUS_ACTIVE = 99;

    /** 业务状态：取消 (-99) */
    public static final int STATUS_CANCEL = -99;

    private final InfoMapper infoMapper;
    private final BasicInfoMapper basicInfoMapper;
    private final MemberMapper memberMapper;
    private final MemberContactMapper memberContactMapper;
    private final AddressMapper addressMapper;
    private final CompanyContactMapper companyContactMapper;
    private final DigitalAccountMapper digitalAccountMapper;
    private final FinancialAccountMapper financialAccountMapper;
    private final CreditRatingMapper creditRatingMapper;
    private final ExternalObjectRealtimeValidateApi realtimeValidateApi;

    private final ConfigApi configApi;

    private final SysMenuApi sysMenuApi;

    private final ObjectMapper objectMapper;

    @Override
    public void updateRegStatus(CompanyRegStatusUpdateReq req) {
        if (req == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        if (req.getId() == null) {
            throw new IllegalArgumentException("单据 ID 不能为空");
        }
        if (req.getRegStatus() == null) {
            throw new IllegalArgumentException("登记状态 regStatus 不能为空");
        }
        String regStatusText = ExternalObjectWordValues.toText(req.getRegStatus()).trim();
        if (ExternalObjectWordValues.isBlank(regStatusText)) {
            throw new IllegalArgumentException("登记状态 regStatus 不能为空");
        }

        // 1. 根据物理主键 id 定位目标单据
        Info info = infoMapper.selectById(req.getId());
        if (info == null || (info.getDeleted() != null && info.getDeleted() != 0)) {
            throw new BusinessException("未找到对应的外部对象单据记录");
        }

        // 2. 状态检查：仅启用 (99) 或取消 (-99) 状态允许修改登记状态
        Integer businessStatus = info.getBusinessStatus();
        if (businessStatus == null
                || (businessStatus != STATUS_ACTIVE && businessStatus != STATUS_CANCEL)) {
            throw new BusinessException("只有处于【已启用 (99)】或【已取消 (-99)】状态的单据才允许修改登记状态");
        }

        // 3. 关联对象性质 (nature) 执行登记状态规则校验 (传入原始 Object 或转换文本)
        validateRegStatus(info, req.getRegStatus());

        // 4. 执行更新 (数据库中保存 JSON 字符串)
        String oldRegStatus = info.getRegStatus();
        info.setRegStatus(regStatusText);
        info.setUpdatedBy(UserContext.getOperateUserId());
        info.setUpdatedDate(LocalDateTime.now());
        infoMapper.updateById(info);

        log.info(
                "[CompanyCustom-UpdateRegStatus] 登记状态修改成功: id={}, businessNo={}, businessStatus={}, 原状态={}, 新状态={}, 操作人={}",
                info.getId(),
                info.getBusinessNo(),
                info.getBusinessStatus(),
                oldRegStatus,
                info.getRegStatus(),
                UserContext.getOperateUserId());
    }

    @Override
    public Object getNature(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("单据 ID 不能为空");
        }
        Info info = infoMapper.selectById(id);
        if (info == null || (info.getDeleted() != null && info.getDeleted() != 0)) {
            throw new BusinessException("未找到对应的外部对象单据记录: id=" + id);
        }
        String nature = info.getNature();
        if (!StringUtils.hasText(nature)) {
            return null;
        }
        try {
            return objectMapper.readTree(nature);
        } catch (Exception e) {
            return nature;
        }
    }

    @Override
    public Map<Long, Long> getMenuNum(CompanyBadgeNumReq req) {
        if (req == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        if (req.getId() == null) {
            throw new IllegalArgumentException("单据 ID 不能为空");
        }
        if (req.getRootMenuId() == null) {
            throw new IllegalArgumentException("顶层菜单ID 不能为空");
        }
        if (req.getMenuIds() == null) {
            throw new IllegalArgumentException("菜单ID集合 不能为空");
        }

        // 获取业务菜单树
        List<SysMenuResp> menuList = sysMenuApi.list(1);

        if (CollectionUtils.isEmpty(menuList)) {
            return null;
        }

        SysMenuResp rootMenu =
                menuList.stream()
                        .filter(ele -> req.getRootMenuId().equals(ele.getId()))
                        .findFirst()
                        .orElse(null);

        if (rootMenu == null) {
            return null;
        }

        List<SysMenuResp> targetMenus =
                menuList.stream()
                        .filter(ele -> req.getMenuIds().contains(ele.getId()))
                        .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(targetMenus)) {
            return null;
        }

        Map<Long, Long> moduleToMenuMap =
                targetMenus.stream()
                        .collect(Collectors.toMap(SysMenuResp::getModuleId, SysMenuResp::getId));

        ModuleTreeDTO rootNode =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootMenu.getModuleId());

        return getModuleBadgeNum(moduleToMenuMap, rootNode, req.getId());
    }

    /**
     * 获取菜单对应的角标数目
     *
     * @param moduleToMenuMap 模块和菜单ID映射map
     * @param rootNode 根节点
     * @param id 单据ID
     * @return
     */
    private Map<Long, Long> getModuleBadgeNum(
            Map<Long, Long> moduleToMenuMap, ModuleTreeDTO rootNode, Long id) {
        Map<Long, Long> menuToBadgeNumMap = new HashMap<>();

        Set<Long> targetModules = moduleToMenuMap.keySet();

        for (Long targetModule : targetModules) {
            if (rootNode.containsModule(targetModule)) {
                ModuleInfo moduleInfo = rootNode.getModule(targetModule);
                Long moduleBadgeNum = getBadgeNumByModuleSlug(moduleInfo.getModuleCode(), id);
                menuToBadgeNumMap.put(moduleToMenuMap.get(targetModule), moduleBadgeNum);
            }
        }
        return menuToBadgeNumMap;
    }

    /**
     * 定制化接口，返回外部对象子模块标识对应的数据数量
     *
     * @param moduleSlug 模块标识
     * @param id 单据ID
     * @return
     */
    private Long getBadgeNumByModuleSlug(String moduleSlug, Long id) {
        switch (moduleSlug) {
            case "COMPANY-MEMBER", "w_rygc":
                LambdaQueryWrapper<Member> memberLambdaQueryWrapper = new LambdaQueryWrapper<>();
                memberLambdaQueryWrapper.in(Member::getType, "employee", "员工");
                memberLambdaQueryWrapper.eq(Member::getCompanyId, id);
                memberLambdaQueryWrapper.eq(Member::getDeleted, 0);
                return memberMapper.selectCount(memberLambdaQueryWrapper);
            case "COMPANY-MEMBER-V", "W_L_LXR":
                LambdaQueryWrapper<Address> addressLambdaQueryWrapper = new LambdaQueryWrapper<>();
                addressLambdaQueryWrapper.eq(Address::getCompanyId, id);
                addressLambdaQueryWrapper.eq(Address::getDeleted, 0);
                Long addressCount = addressMapper.selectCount(addressLambdaQueryWrapper);

                LambdaQueryWrapper<CompanyContact> companyContactLambdaQueryWrapper =
                        new LambdaQueryWrapper<>();
                companyContactLambdaQueryWrapper.eq(CompanyContact::getCompanyId, id);
                companyContactLambdaQueryWrapper.eq(CompanyContact::getDeleted, 0);
                Long companyContactCount =
                        companyContactMapper.selectCount(companyContactLambdaQueryWrapper);

                return companyContactCount + addressCount;
            case "COMPANY-FINANCE-V", "W_CWXX":
                LambdaQueryWrapper<FinancialAccount> financialAccountLambdaQueryWrapper =
                        new LambdaQueryWrapper<>();
                financialAccountLambdaQueryWrapper.eq(FinancialAccount::getDeleted, 0);
                financialAccountLambdaQueryWrapper.eq(FinancialAccount::getCompanyId, id);
                Long financialAcountCount =
                        financialAccountMapper.selectCount(financialAccountLambdaQueryWrapper);

                LambdaQueryWrapper<DigitalAccount> digitalAccountLambdaQueryWrapper =
                        new LambdaQueryWrapper<>();
                digitalAccountLambdaQueryWrapper.eq(DigitalAccount::getDeleted, 0);
                digitalAccountLambdaQueryWrapper.eq(DigitalAccount::getCompanyId, id);
                Long digitalAccountCount =
                        digitalAccountMapper.selectCount(digitalAccountLambdaQueryWrapper);

                return financialAcountCount + digitalAccountCount;
            default:
                return 0L;
        }
    }

    private void validateRegStatus(Info info, Object regStatus) {
        if (realtimeValidateApi == null || info == null) {
            return;
        }
        if (ExternalObjectWordValues.isIndividualNature(info.getNature())) {
            throw new BusinessException("个人性质的外部对象不允许修改登记状态");
        }
        ExternalObjectRealtimeValidateReq validateReq =
                new ExternalObjectRealtimeValidateReq(
                        info.getId(), info.getId(), "info.reg_status", regStatus);
        ExternalObjectRealtimeValidateResp validateResp = realtimeValidateApi.validate(validateReq);
        if (validateResp != null && !validateResp.valid()) {
            throw new BusinessException(validateResp.message());
        }
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private record ParentMeta(String value, Integer versionNo, String label) {}

    @Override
    public CompanyTreeNodeResp getCompanyTree(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("单据 ID 不能为空");
        }

        // 1. 获取当前单据作为锚点
        Info current = infoMapper.selectById(id);
        if (current == null || (current.getDeleted() != null && current.getDeleted() != 0)) {
            throw new BusinessException("未找到对应的外部对象单据记录");
        }

        // 2. 节点池：Key 为 "business_no:version_no"
        Map<String, Info> pool = new LinkedHashMap<>();
        String currentKey = buildNodeKey(current.getBusinessNo(), current.getVersionNo());
        pool.put(currentKey, current);

        // 3. 向上追溯所有祖先节点，获取顶级根节点
        Info topRoot = traceAncestors(current, pool);

        // 4. 从顶级根节点向下收集所有子孙节点
        traceDescendants(topRoot, current, pool);

        // 5. 内存统一组装为完整公司树
        return assembleCompanyTree(pool, currentKey, topRoot);
    }

    private Info traceAncestors(Info current, Map<String, Info> pool) {
        Info pointer = current;
        Set<String> visited = new HashSet<>();
        visited.add(buildNodeKey(pointer.getBusinessNo(), pointer.getVersionNo()));

        while (true) {
            ParentMeta parentMeta = parseParentId(pointer.getParentId());
            if (parentMeta == null
                    || !StringUtils.hasText(parentMeta.value())
                    || parentMeta.versionNo() == null) {
                return pointer;
            }

            LambdaQueryWrapper<Info> queryWrapper =
                    new LambdaQueryWrapper<Info>()
                            .eq(Info::getBusinessNo, parentMeta.value())
                            .eq(Info::getVersionNo, parentMeta.versionNo())
                            .eq(Info::getDeleted, 0);
            Info parent = infoMapper.selectOne(queryWrapper);
            if (parent == null) {
                return pointer;
            }

            String pKey = buildNodeKey(parent.getBusinessNo(), parent.getVersionNo());
            if (!visited.add(pKey)) {
                log.warn("检测到父级企业版本循环引用: {}", pKey);
                return parent;
            }

            pool.put(pKey, parent);
            pointer = parent;
        }
    }

    private void traceDescendants(Info topRoot, Info currentSingle, Map<String, Info> pool) {
        List<Info> currentLevel = Collections.singletonList(topRoot);
        Set<String> visitedBizNos = new HashSet<>();
        if (StringUtils.hasText(topRoot.getBusinessNo())) {
            visitedBizNos.add(topRoot.getBusinessNo());
        }

        while (!CollectionUtils.isEmpty(currentLevel)) {
            List<String> parentBizNos =
                    currentLevel.stream()
                            .map(Info::getBusinessNo)
                            .filter(StringUtils::hasText)
                            .distinct()
                            .collect(Collectors.toList());

            if (CollectionUtils.isEmpty(parentBizNos)) {
                break;
            }

            List<Info> candidates = infoMapper.selectCandidatesByParentBizNos(parentBizNos);
            if (CollectionUtils.isEmpty(candidates)) {
                break;
            }

            Map<String, List<Info>> childGroup =
                    candidates.stream().collect(Collectors.groupingBy(Info::getBusinessNo));

            List<Info> nextLevel = new ArrayList<>();

            for (Info parentNode : currentLevel) {
                for (List<Info> versions : childGroup.values()) {
                    Info selectedChild = pickBestVersion(versions, currentSingle);
                    if (selectedChild == null) {
                        continue;
                    }
                    ParentMeta childParentMeta = parseParentId(selectedChild.getParentId());

                    if (childParentMeta != null
                            && parentNode.getBusinessNo().equals(childParentMeta.value())
                            && parentNode.getVersionNo() != null
                            && parentNode.getVersionNo().equals(childParentMeta.versionNo())) {

                        String childKey =
                                buildNodeKey(
                                        selectedChild.getBusinessNo(),
                                        selectedChild.getVersionNo());
                        if (!pool.containsKey(childKey)) {
                            pool.put(childKey, selectedChild);
                            if (visitedBizNos.add(selectedChild.getBusinessNo())) {
                                nextLevel.add(selectedChild);
                            }
                        }
                    }
                }
            }

            currentLevel = nextLevel;
        }
    }

    private Info pickBestVersion(List<Info> versions, Info currentSingle) {
        if (CollectionUtils.isEmpty(versions)) {
            return null;
        }
        for (Info v : versions) {
            if (v.getId() != null && v.getId().equals(currentSingle.getId())) {
                return v;
            }
        }
        return versions.stream()
                .filter(v -> Integer.valueOf(STATUS_ACTIVE).equals(v.getBusinessStatus()))
                .max(Comparator.comparingInt(v -> v.getVersionNo() != null ? v.getVersionNo() : 0))
                .orElseGet(
                        () ->
                                versions.stream()
                                        .max(
                                                Comparator.comparingInt(
                                                        v ->
                                                                v.getVersionNo() != null
                                                                        ? v.getVersionNo()
                                                                        : 0))
                                        .orElse(versions.get(0)));
    }

    private CompanyTreeNodeResp assembleCompanyTree(
            Map<String, Info> pool, String currentKey, Info topRoot) {
        if (CollectionUtils.isEmpty(pool)) {
            return null;
        }

        Map<String, CompanyTreeNodeResp> respMap = new LinkedHashMap<>();
        for (Map.Entry<String, Info> entry : pool.entrySet()) {
            Info info = entry.getValue();
            boolean isCur = entry.getKey().equals(currentKey);
            CompanyTreeNodeResp nodeResp =
                    CompanyTreeNodeResp.builder()
                            .id(info.getId())
                            .businessNo(info.getBusinessNo())
                            .versionNo(info.getVersionNo())
                            .companyName(info.getCompanyName())
                            .businessStatus(info.getBusinessStatus())
                            .regStatus(info.getRegStatus())
                            .nature(info.getNature())
                            .isCurrent(isCur)
                            .parentId(info.getParentId())
                            .children(new ArrayList<>())
                            .build();
            respMap.put(entry.getKey(), nodeResp);
        }

        String topRootKey = buildNodeKey(topRoot.getBusinessNo(), topRoot.getVersionNo());
        CompanyTreeNodeResp root = respMap.get(topRootKey);

        for (CompanyTreeNodeResp node : respMap.values()) {
            ParentMeta parentMeta = parseParentId(node.getParentId());
            String parentKey =
                    (parentMeta != null
                                    && StringUtils.hasText(parentMeta.value())
                                    && parentMeta.versionNo() != null)
                            ? buildNodeKey(parentMeta.value(), parentMeta.versionNo())
                            : null;

            CompanyTreeNodeResp parentNode = (parentKey != null) ? respMap.get(parentKey) : null;
            if (parentNode != null && parentNode != node) {
                parentNode.getChildren().add(node);
            } else if (root == null) {
                root = node;
            }
        }

        return root != null ? root : respMap.values().iterator().next();
    }

    private ParentMeta parseParentId(String parentIdJson) {
        if (!StringUtils.hasText(parentIdJson)
                || "null".equalsIgnoreCase(parentIdJson.trim())
                || "{}".equals(parentIdJson.trim())
                || "[]".equals(parentIdJson.trim())) {
            return null;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(parentIdJson);
            if (node == null || !node.isObject()) {
                return null;
            }
            JsonNode valueNode = node.get("value");
            String value =
                    (valueNode != null && !valueNode.isNull()) ? valueNode.asText().trim() : null;
            if (!StringUtils.hasText(value)) {
                return null;
            }
            JsonNode verNode = node.get("version_no");
            Integer versionNo = (verNode != null && verNode.isNumber()) ? verNode.asInt() : null;
            JsonNode labelNode = node.get("label");
            String label =
                    (labelNode != null && !labelNode.isNull()) ? labelNode.asText().trim() : null;
            return new ParentMeta(value, versionNo, label);
        } catch (Exception e) {
            log.warn("解析 parent_id 异常: {}, err={}", parentIdJson, e.getMessage());
            return null;
        }
    }

    private String buildNodeKey(String businessNo, Integer versionNo) {
        return (businessNo != null ? businessNo : "") + ":" + (versionNo != null ? versionNo : 0);
    }

    @Override
    public List<CompanyContact> listCompanyContacts(Long companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("公司 ID (companyId) 不能为空");
        }
        LambdaQueryWrapper<CompanyContact> queryWrapper =
                new LambdaQueryWrapper<CompanyContact>()
                        .eq(CompanyContact::getCompanyId, companyId)
                        .eq(CompanyContact::getDeleted, 0)
                        .orderByAsc(CompanyContact::getSortOrder)
                        .orderByAsc(CompanyContact::getId);
        return companyContactMapper.selectList(queryWrapper);
    }

    @Override
    public List<MemberTreeNodeResp> getMemberTree(Long companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("公司 ID (companyId) 不能为空");
        }

        // 1. 查询该公司的所有未删除人员/部门/组织节点，按 sort_order, id 升序
        List<Member> memberList =
                memberMapper.selectList(
                        new LambdaQueryWrapper<Member>()
                                .eq(Member::getCompanyId, companyId)
                                .eq(Member::getDeleted, 0)
                                .orderByAsc(Member::getSortOrder)
                                .orderByAsc(Member::getId));
        if (CollectionUtils.isEmpty(memberList)) {
            return Collections.emptyList();
        }

        // 2. 收集所有成员ID，批量查询关联的人员联系方式
        List<Long> memberIds =
                memberList.stream()
                        .map(Member::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

        Map<Long, List<MemberContactResp>> contactMap = new HashMap<>();
        if (!memberIds.isEmpty()) {
            List<MemberContact> contactList =
                    memberContactMapper.selectList(
                            new LambdaQueryWrapper<MemberContact>()
                                    .in(MemberContact::getMemberId, memberIds)
                                    .eq(MemberContact::getDeleted, 0)
                                    .orderByAsc(MemberContact::getSortOrder)
                                    .orderByAsc(MemberContact::getId));
            if (!CollectionUtils.isEmpty(contactList)) {
                contactMap =
                        contactList.stream()
                                .collect(
                                        Collectors.groupingBy(
                                                MemberContact::getMemberId,
                                                Collectors.mapping(
                                                        this::toMemberContactResp,
                                                        Collectors.toList())));
            }
        }

        // 3. 构建节点响应对象并建立索引
        List<MemberTreeNodeResp> allNodes = new ArrayList<>(memberList.size());
        Map<String, MemberTreeNodeResp> nodeKeyMap = new HashMap<>();

        for (Member m : memberList) {
            MemberTreeNodeResp resp = toMemberTreeNodeResp(m);
            List<MemberContactResp> contacts = contactMap.get(m.getId());
            if (contacts != null) {
                resp.setContacts(contacts);
            }
            allNodes.add(resp);

            if (StringUtils.hasText(m.getNodeId())) {
                nodeKeyMap.put(m.getNodeId().trim(), resp);
            }
        }

        // 4. 组装父子层级树
        List<MemberTreeNodeResp> rootNodes = new ArrayList<>();
        for (MemberTreeNodeResp node : allNodes) {
            String parentNodeId = node.getParentNodeId();
            if (StringUtils.hasText(parentNodeId) && nodeKeyMap.containsKey(parentNodeId.trim())) {
                MemberTreeNodeResp parent = nodeKeyMap.get(parentNodeId.trim());
                if (parent != node) {
                    parent.getChildren().add(node);
                    continue;
                }
            }
            rootNodes.add(node);
        }

        return rootNodes;
    }

    private MemberContactResp toMemberContactResp(MemberContact c) {
        return MemberContactResp.builder()
                .id(c.getId())
                .businessNo(c.getBusinessNo())
                .versionNo(c.getVersionNo())
                .subjectId(c.getSubjectId())
                .memberId(c.getMemberId())
                .contactType(c.getContactType())
                .contactValue(c.getContactValue())
                .isPrimary(c.getIsPrimary())
                .sortOrder(c.getSortOrder())
                .build();
    }

    private MemberTreeNodeResp toMemberTreeNodeResp(Member m) {
        return MemberTreeNodeResp.builder()
                .id(m.getId())
                .businessNo(m.getBusinessNo())
                .versionNo(m.getVersionNo())
                .subjectId(m.getSubjectId())
                .companyId(m.getCompanyId())
                .type(m.getType())
                .memberName(m.getMemberName())
                .name(m.getName())
                .code(m.getCode())
                .deptLeader(m.getDeptLeader())
                .position(m.getPosition())
                .gender(m.getGender())
                .sortOrder(m.getSortOrder())
                .status(m.getStatus())
                .nodeId(m.getNodeId())
                .parentNodeId(m.getParentNodeId())
                .identityType(m.getIdentityType())
                .identityNo(m.getIdentityNo())
                .mobilePhoneNo(m.getMobilePhoneNo())
                .workField(m.getWorkField())
                .expertiseField(m.getExpertiseField())
                .photoUrl(m.getPhotoUrl())
                .contacts(new ArrayList<>())
                .children(new ArrayList<>())
                .build();
    }
}
