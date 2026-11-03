package com.jdec.platform.externalobject.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.api.SysMenuApi;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.response.SysMenuResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.externalobject.api.ExternalObjectRealtimeValidateApi;
import com.jdec.platform.externalobject.api.dto.request.CompanyBadgeNumReq;
import com.jdec.platform.externalobject.api.dto.request.CompanyRegStatusUpdateReq;
import com.jdec.platform.externalobject.api.dto.request.ExternalObjectRealtimeValidateReq;
import com.jdec.platform.externalobject.api.dto.response.ExternalObjectRealtimeValidateResp;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

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
    private final AddressMapper addressMapper;
    private final CompanyContactMapper companyContactMapper;
    private final DigitalAccountMapper digitalAccountMapper;
    private final FinancialAccountMapper financialAccountMapper;
    private final CreditRatingMapper creditRatingMapper;
    private final ExternalObjectRealtimeValidateApi realtimeValidateApi;

    private final ConfigApi configApi;

    private final SysMenuApi sysMenuApi;

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
            case "COMPANY-BASIC-INFO", "W_JBXX":
                LambdaQueryWrapper<BasicInfo> basicInfoLambdaQueryWrapper =
                        new LambdaQueryWrapper<>();
                basicInfoLambdaQueryWrapper.eq(BasicInfo::getCompanyId, id);
                return basicInfoMapper.selectCount(basicInfoLambdaQueryWrapper);
            case "COMPANY-MEMBER", "w_rygc":
                LambdaQueryWrapper<Member> memberLambdaQueryWrapper = new LambdaQueryWrapper<>();
                memberLambdaQueryWrapper.in(Member::getType, "employee", "员工");
                memberLambdaQueryWrapper.eq(Member::getCompanyId, id);
                return memberMapper.selectCount(memberLambdaQueryWrapper);
            case "COMPANY-MEMBER-V", "W_L_LXR":
                LambdaQueryWrapper<Address> addressLambdaQueryWrapper = new LambdaQueryWrapper<>();
                addressLambdaQueryWrapper.eq(Address::getCompanyId, id);
                Long addressCount = addressMapper.selectCount(addressLambdaQueryWrapper);

                LambdaQueryWrapper<CompanyContact> companyContactLambdaQueryWrapper =
                        new LambdaQueryWrapper<>();
                companyContactLambdaQueryWrapper.eq(CompanyContact::getCompanyId, id);
                Long companyContactCount =
                        companyContactMapper.selectCount(companyContactLambdaQueryWrapper);

                return companyContactCount + addressCount;
            case "COMPANY-FINANCE-V", "W_CWXX":
                LambdaQueryWrapper<FinancialAccount> financialAccountLambdaQueryWrapper =
                        new LambdaQueryWrapper<>();
                financialAccountLambdaQueryWrapper.eq(FinancialAccount::getCompanyId, id);
                Long financialAcountCount =
                        financialAccountMapper.selectCount(financialAccountLambdaQueryWrapper);

                LambdaQueryWrapper<DigitalAccount> digitalAccountLambdaQueryWrapper =
                        new LambdaQueryWrapper<>();
                digitalAccountLambdaQueryWrapper.eq(DigitalAccount::getCompanyId, id);
                Long digitalAccountCount =
                        digitalAccountMapper.selectCount(digitalAccountLambdaQueryWrapper);

                return financialAcountCount + digitalAccountCount;

            case "COMPANY-CREDIT", "W XYDJ":
                LambdaQueryWrapper<CreditRating> creditLambdaQueryWrapper =
                        new LambdaQueryWrapper<>();
                creditLambdaQueryWrapper.eq(CreditRating::getCompanyId, id);
                return creditRatingMapper.selectCount(creditLambdaQueryWrapper);
            default:
                return 0L;
        }
    }

    private void validateRegStatus(Info info, Object regStatus) {
        if (realtimeValidateApi == null || info == null) {
            return;
        }
        ExternalObjectRealtimeValidateReq validateReq =
                new ExternalObjectRealtimeValidateReq(
                        info.getId(), info.getId(), "info.reg_status", regStatus);
        ExternalObjectRealtimeValidateResp validateResp = realtimeValidateApi.validate(validateReq);
        if (validateResp != null && !validateResp.valid()) {
            throw new BusinessException(validateResp.message());
        }
    }
}
