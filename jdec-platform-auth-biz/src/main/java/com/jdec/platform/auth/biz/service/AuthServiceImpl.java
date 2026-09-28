package com.jdec.platform.auth.biz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.auth.api.AuthApi;
import com.jdec.platform.auth.api.dto.request.SelectRoleReq;
import com.jdec.platform.auth.api.dto.response.*;
import com.jdec.platform.auth.biz.constant.AuthConstant;
import com.jdec.platform.auth.biz.dto.SubjectRoleDTO;
import com.jdec.platform.config.api.*;
import com.jdec.platform.config.api.dto.request.CheckUserSpecialPermissionReq;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.TokenContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.exception.ApiCodeEnum;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.security.JwtTokenProvider;
import com.jdec.platform.shared.security.model.LoginUser;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 认证 Service 实现（迁移自 student-manage 的 AuthServiceImpl）。
 *
 * <p>与源实现的主要差异：源项目通过外部「配置中心」HTTP 客户端 {@code ConfigCenterApiClient.getCurrentUserCheck(phone)}
 * 校验用户；本平台为模块化单体， 改为直接调用 {@link SysUserApi#checkUserPermissionByPhone(String)}。
 *
 * <p>注意：{@code checkUserPermissionByPhone} 仅在 {@code permissionFlag=true} 时才回填用户信息， 因此登录必须以 {@code
 * permissionFlag=true} 作为「用户存在且有本系统权限」的判定条件。
 *
 * <p><b>禁止加 {@code @Transactional}。</b>本类只做 Redis 读写与跨模块调用，自身不访问数据库。若加上事务， 事务切面会在进入本类时就从「默认数据源」
 * 取一条连接并绑定到当前线程，导致内层 {@link SysUserApi} 实现上的 {@code com.jdec.platform.shared.datasource.DataSource}
 * 切换失效（{@code DataSourceAspect} 虽是 {@code @Order(1)} 也只能在事务已经开启之后生效），最终把 {@code sys_user} 查询打到错误库上，
 * 报 {@code Table 'mysql.sys_user' doesn't exist}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthApi {

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final SysUserApi sysUserApi;
    private final SysRoleApi sysRoleApi;
    private final SysRolePermissionApi sysRolePermissionApi;
    private final SysProjectSubjectApi sysProjectSubjectApi;
    private final SysSpecialPermissionApi sysSpecialPermissionApi;
    private final ObjectMapper objectMapper;

    /** 源实现中的「空角色」占位值，命中时需回退到角色列表的第一个 */
    private static final Long DEFAULT_EMPTY_ROLE_ID = 0L;

    @Value("${app.internal.url:}")
    private String internalUrl;

    @Override
    public CurrentUserCheckResp getCurrentUserCheckInfo(String token) {
        String phone = requirePhone(token);

        CheckUserPermissionByPhoneResp resp = sysUserApi.checkUserPermissionByPhone(phone);
        if (resp == null) {
            log.debug("检查当前用户是否有角色失败, phone={}", phone);
            return CurrentUserCheckResp.builder().hasPermission(false).build();
        }
        return CurrentUserCheckResp.builder()
                .hasPermission(Boolean.TRUE.equals(resp.getPermissionFlag()))
                .sysInfo(buildSysInfo())
                .build();
    }

    @Override
    public LoginResult login(String authHeader) {
        if (!StringUtils.hasText(authHeader)) {
            throw loginForbidden();
        }

        String token = extractToken(authHeader);
        if (!StringUtils.hasText(token)) {
            throw loginForbidden();
        }

        // 本端点被 WebMvcConfig 排除在 JwtAuthenticationInterceptor 之外，拦截器不会替我们写 TokenContext；
        // 而 login 内部的配置中心调用会触发 ConfigChangeMessagePublisher 读取它，所以这里自行设置并在结束时清理。
        TokenContext.setToken(token);
        try {
            return doLogin(token);
        } finally {
            TokenContext.clear();
        }
    }

    /** {@link #login(String)} 的主体。入参 token 已剥离 Bearer 前缀并通过非空校验。 */
    private LoginResult doLogin(String token) {
        String phone = jwtTokenProvider.getUserPhone(token);
        if (!StringUtils.hasText(phone)) {
            throw loginForbidden();
        }

        // 校验用户是否已经在大系统登录
        if (!checkUserToken(token, phone)) {
            log.debug("token:{}已过期，手机号:{}请重新登录", token, phone);
            throw loginForbidden();
        }

        CheckUserPermissionByPhoneResp resp = sysUserApi.checkUserPermissionByPhone(phone);
        if (resp == null || !Boolean.TRUE.equals(resp.getPermissionFlag())) {
            log.debug("获取登录用户信息失败, phone={}", phone);
            throw loginForbidden();
        }

        log.debug("用户手机号:{}登录成功。", phone);

        // 写入登录上下文（Redis）
        try {
            LoginUser loginUser =
                    LoginUser.builder()
                            .userId(resp.getUserId())
                            .username(resp.getUserName())
                            .subjectId(resp.getSubjectId())
                            .phone(phone)
                            .token(token)
                            .build();
            redisTemplate
                    .opsForValue()
                    .set(
                            AuthConstant.LOGIN_KEY_PREFIX + phone,
                            objectMapper.writeValueAsString(loginUser));
        } catch (Exception e) {
            log.error("login redis save user info error, e=", e);
            throw new BusinessException(
                    ApiCodeEnum.WARNING.getCode(), ApiCodeEnum.WARNING.getMessage(), e);
        }

        return new LoginResult(
                token,
                UserInfo.builder()
                        .id(resp.getUserId())
                        .username(resp.getUserName())
                        .status(resp.getStatusFlag())
                        .subjectId(resp.getSubjectId())
                        .phone(phone)
                        .departId(resp.getDeptIds())
                        .departName(resp.getDepartName())
                        .build());
    }

    /**
     * 从 Authorization 请求头中提取 Bearer Token；非 Bearer 前缀或空值返回 null。
     *
     * <p>仅供本类的 {@link #login(String)} 与 {@link #logout(String)} 使用，因此不暴露在 {@link AuthApi}
     * 上（控制器不再需要它 —— {@code TokenContext} 的写入已下移到 {@link #login(String)} 内部）。
     */
    private String extractToken(String headString) {
        if (StringUtils.hasText(headString) && headString.startsWith(AuthConstant.BEARER_PREFIX)) {
            return headString.substring(AuthConstant.BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * 校验 Redis 中缓存的 token 与传入 token 是否一致，用于确认会话是否仍然有效。
     *
     * <p>仅供本类 {@link #login(String)} 使用，因此不暴露在 {@link AuthApi} 上。
     */
    private Boolean checkUserToken(String token, String phone) {
        try {
            String value = redisTemplate.opsForValue().get(AuthConstant.TOKEN_KEY_PREFIX + phone);
            if (StringUtils.hasText(value) && value.equals(token)) {
                return Boolean.TRUE;
            }
        } catch (Exception e) {
            log.error("checkUserToken error, e=", e);
        }
        return Boolean.FALSE;
    }

    /** 删除 Redis 中该手机号的 token 缓存。仅供本类 {@link #logout(String)} 使用。 */
    private Boolean removeUserToken(String phone) {
        try {
            return redisTemplate.delete(AuthConstant.TOKEN_KEY_PREFIX + phone);
        } catch (Exception e) {
            log.error("removeUserToken error, e=", e);
        }
        return Boolean.FALSE;
    }

    /** 删除 Redis 中该手机号的登录上下文缓存。仅供本类 {@link #logout(String)} 使用。 */
    private Boolean removeUserLogin(String phone) {
        try {
            return redisTemplate.delete(AuthConstant.LOGIN_KEY_PREFIX + phone);
        } catch (Exception e) {
            log.error("remove user info error, e=", e);
        }
        return Boolean.FALSE;
    }

    @Override
    public Boolean logout(String tokenHeader) {
        if (!StringUtils.hasText(tokenHeader)) {
            throw loginForbidden();
        }
        String token = extractToken(tokenHeader);
        String phone = jwtTokenProvider.getUserPhone(token);
        if (!StringUtils.hasText(phone)) {
            throw loginForbidden();
        }
        removeUserLogin(phone);
        return removeUserToken(phone);
    }

    /** 从 token 中解析手机号，任一环节缺失即视为未登录。 */
    private String requirePhone(String token) {
        if (!StringUtils.hasText(token)) {
            throw loginForbidden();
        }
        String phone = jwtTokenProvider.getUserPhone(token);
        if (!StringUtils.hasText(phone)) {
            throw loginForbidden();
        }
        return phone;
    }

    private static BusinessException loginForbidden() {
        return new BusinessException(
                ApiCodeEnum.OVERDUE.getCode(), ApiCodeEnum.OVERDUE.getMessage());
    }

    /** 登录页展示的系统信息（沿用源项目的固定值，后续可外部化为配置）。 */
    private SysInfoDTO buildSysInfo() {
        SysInfoDTO sysInfo = new SysInfoDTO();
        sysInfo.setTitle("外部对象管理系统");
        sysInfo.setLogo(
                "http://119.36.78.123:9077/storage/upload/LIdqTCsyOZIqYJ4fsxeBhntUy860blvL8ZSbN7ic.svg");
        sysInfo.setIcon(
                "http://119.36.78.123:9077/storage/upload/PY9vYNYUdPfhjDKJQa0tiAeLjyTP1KrgSK5w4OTU.svg");
        sysInfo.setIsSubject(2);
        sysInfo.setSubjectColor("rgb(29, 120, 250)");
        sysInfo.setLoginBgImg(
                "http://119.36.78.123:9077/storage/upload/CB5c5JVPOEIBoK6LKBL5PzZEll5dE27tD2AFU3uR.png");
        sysInfo.setInternalUrl("http://192.168.60.253:5008");
        return sysInfo;
    }

    // ========== 切换角色（迁移自 student-manage 的 SystemManageServiceImpl.selectRole） ==========

    /**
     * 切换角色：解析最终主体/角色，返回主体列表与菜单树，并刷新 Redis 中的当前角色缓存。
     *
     * <p>与原实现的差异：
     *
     * <ul>
     *   <li>登录态来自 {@link UserContext}（平台用 ThreadLocal，源项目用 Spring Security）。
     *   <li>原实现经 HTTP 调配置中心，这里改为进程内调用 config-api。
     *   <li><b>WebSocket 逻辑整体去掉</b>：平台暂无 WebSocket，源实现里的 {@code
     *       UserSessionHolder.roleChangeSessionProcess} 没有对应物，会话推送待平台引入后再补。
     *   <li>去掉了原方法上的 {@code synchronized}：它主要用于保护已删除的模拟用户映射状态，保留会把整个端点串行化。
     * </ul>
     */
    @Override
    public ApiResponse<SelectRoleResp> selectRole(SelectRoleReq req) {
        SelectRoleReq request = req != null ? req : new SelectRoleReq();

        Long realLoginUserId = UserContext.getRealUserId();
        if (realLoginUserId == null) {
            realLoginUserId = UserContext.getUserId();
        }
        Long mockUserId = UserContext.getMockUserId();
        // 模拟登录时取模拟用户，否则就是登录人自己
        Long loginUserId = (mockUserId != null && mockUserId > 0) ? mockUserId : realLoginUserId;
        boolean mockFlag = !Objects.equals(realLoginUserId, loginUserId);
        boolean simulateLogin = false;

        Long subjectId = request.getSubjectId();
        Long roleId = request.getRoleId();

        try {
            // 1. 主体列表永远查真实登录人的；角色列表查 loginUserId（可能是模拟用户）
            List<SubjectResp> subjectRespList = loadSubjectList(realLoginUserId, loginUserId);
            if (subjectRespList.isEmpty()) {
                throw noRight(mockFlag);
            }

            Long finalRoleId = null;
            Long finalSubjectId = null;
            // 当前角色缓存只能按真实登录人取
            SubjectRoleDTO oldSubjectRole = getUserCurrentRole(realLoginUserId);

            if (roleId != null) {
                // 角色有值则主体一定有值（主体跟着角色走）
                finalRoleId = roleId;
                finalSubjectId = subjectId;
            } else if (!mockFlag) {
                if (oldSubjectRole == null
                        || !equalsLongValue(oldSubjectRole.getSubjectId(), subjectId)) {
                    finalRoleId = roleId;
                    finalSubjectId = subjectId;
                } else {
                    finalRoleId = oldSubjectRole.getRoleId();
                    subjectId = oldSubjectRole.getSubjectId();
                    finalSubjectId = subjectId;
                }
            } else if (oldSubjectRole != null
                    && Objects.equals(loginUserId, oldSubjectRole.getMockUserId())) {
                if (!equalsLongValue(oldSubjectRole.getMockSubjectId(), subjectId)) {
                    finalRoleId = roleId;
                    finalSubjectId = subjectId;
                } else {
                    finalRoleId = oldSubjectRole.getMockRoleId();
                    subjectId = oldSubjectRole.getMockSubjectId();
                    finalSubjectId = subjectId;
                }
            }

            // 没有指定主体、缓存里也没有：取用户有权限的第一个主体
            if (finalSubjectId == null) {
                subjectId = subjectRespList.get(0).getSubjectId();
                finalSubjectId = subjectId;
            }

            // 2. 当前主体下的角色
            List<Long> roleIdList = roleIdsOf(rolesOf(findSubject(subjectRespList, subjectId)));

            // 当前主体没有角色 → 换到第一个有角色的主体
            if (roleIdList.isEmpty()) {
                for (SubjectResp candidate : subjectRespList) {
                    if (!Objects.equals(subjectId, candidate.getSubjectId())) {
                        roleIdList = roleIdsOf(rolesOf(candidate));
                        finalSubjectId = candidate.getSubjectId();
                        if (!roleIdList.isEmpty()) {
                            break;
                        }
                    }
                }
            }
            if (roleIdList.isEmpty()) {
                throw noRight(mockFlag);
            }

            // 3. 未指定 / 已失效 / 占位值 → 取第一个角色
            if (finalRoleId == null
                    || !roleIdList.contains(finalRoleId)
                    || DEFAULT_EMPTY_ROLE_ID.equals(finalRoleId)) {
                finalRoleId = roleIdList.get(0);
            }

            // 4. 校验真实登录人是否具备模拟登录权限
            if (hasSimulateRight(finalSubjectId, realLoginUserId)) {
                simulateLogin = true;
            }
            if (mockFlag && !simulateLogin) {
                throw new BusinessException(
                        ApiCodeEnum.USER_NO_MOCK_RIGHT.getCode(),
                        ApiCodeEnum.USER_NO_MOCK_RIGHT.getMessage());
            }

            // 5. 组装响应
            SelectRoleResp selectRoleResp = new SelectRoleResp();
            if (mockFlag) {
                // 模拟登录时主体列表是登录人的，不过滤
                selectRoleResp.setSubjectList(subjectRespList);
            } else {
                // 非模拟：过滤掉没有角色的主体
                selectRoleResp.setSubjectList(
                        subjectRespList.stream()
                                .filter(s -> !rolesOf(s).isEmpty())
                                .collect(Collectors.toList()));
            }
            selectRoleResp.setSubjectId(finalSubjectId);
            selectRoleResp.setRoleId(finalRoleId);
            selectRoleResp.setSimulateLogin(simulateLogin);

            // 6. 菜单树（category 传 null = 不按分类过滤，与源实现一致）
            List<RoleMenuResp> roleMenus =
                    sysRolePermissionApi.getRoleMenuByCategory(
                            finalRoleId, null, finalSubjectId, AppContext.getProjectNo());
            ApiResponse<SelectRoleResp> apiResponse;
            if (roleMenus == null || roleMenus.isEmpty()) {
                selectRoleResp.setMenuId(null);
                selectRoleResp.setMenuList(new ArrayList<>());
                apiResponse =
                        ApiResponse.customResponse(
                                ApiCodeEnum.USER_ROLE_NO_PERMISSION.getCode(),
                                ApiCodeEnum.USER_ROLE_NO_PERMISSION.getMessage(),
                                selectRoleResp);
            } else {
                List<MenuResp> menuList =
                        roleMenus.stream()
                                .map(AuthServiceImpl::toMenuResp)
                                .sorted(
                                        Comparator.comparing(
                                                        MenuResp::getCategory,
                                                        Comparator.nullsLast(Integer::compareTo))
                                                .thenComparing(
                                                        MenuResp::getSortOrder,
                                                        Comparator.nullsLast(Integer::compareTo)))
                                .collect(Collectors.toList());
                Long finalMenuId = request.getMenuId();
                MenuResp hit = findMenu(menuList, finalMenuId);
                if (hit == null) {
                    // 入参菜单不在当前角色可见范围内 → 回退到第一个根节点
                    hit = findRootMenu(menuList);
                    finalMenuId = hit != null ? hit.getMenuId() : null;
                }
                selectRoleResp.setMenuId(finalMenuId);
                selectRoleResp.setMenuList(buildMenuTree(menuList));
                apiResponse = ApiResponse.success(selectRoleResp);
            }

            // 7. 角色本身没有权限 → 覆盖业务码（仍保留 data）
            if (!sysRolePermissionApi.checkRoleHasPermission(
                    finalRoleId, finalSubjectId, AppContext.getProjectNo())) {
                apiResponse.setStatus(ApiCodeEnum.USER_ROLE_NO_PERMISSION.getCode());
                apiResponse.setMsg(ApiCodeEnum.USER_ROLE_NO_PERMISSION.getMessage());
            }

            // 8. 刷新当前角色缓存
            saveCurrentRole(
                    realLoginUserId,
                    loginUserId,
                    finalSubjectId,
                    finalRoleId,
                    mockFlag,
                    oldSubjectRole);

            return apiResponse;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                    "selectRole error, realLoginUserId={}, subjectId={}, roleId={}",
                    realLoginUserId,
                    subjectId,
                    roleId,
                    e);
            throw new BusinessException(
                    ApiCodeEnum.SERVER_ERROR.getCode(), ApiCodeEnum.SERVER_ERROR.getMessage(), e);
        }
    }

    /** 查询用户有角色的主体列表，并为每个主体填充该用户（可能是模拟用户）的角色列表。 */
    private List<SubjectResp> loadSubjectList(Long realLoginUserId, Long loginUserId) {
        List<Long> subjectIds = sysUserApi.getSubjectIdsByUserId(realLoginUserId);
        if (subjectIds == null || subjectIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<SysProjectSubjectResp> subjects = sysProjectSubjectApi.list();
        if (subjects == null || subjects.isEmpty()) {
            return new ArrayList<>();
        }
        List<SubjectResp> result = new ArrayList<>();
        for (SysProjectSubjectResp subject : subjects) {
            if (!subjectIds.contains(subject.getSubjectId())) {
                continue;
            }
            SubjectResp resp = new SubjectResp();
            resp.setSubjectName(subject.getSubjectName());
            resp.setCompanyName(subject.getCompanyName());
            resp.setAlias(subject.getAlias());
            resp.setSubjectId(subject.getSubjectId());
            resp.setRoleRespList(loadRoleList(subject.getSubjectId(), loginUserId));
            result.add(resp);
        }
        return result;
    }

    /** 查询某主体下某用户的角色列表。 */
    private List<RoleResp> loadRoleList(Long subjectId, Long userId) {
        List<UserRoleListResp> roles =
                withSubject(subjectId, () -> sysRoleApi.getUserRolesByUserIdAndSubjectId(userId));
        if (roles == null || roles.isEmpty()) {
            return new ArrayList<>();
        }
        return roles.stream().map(AuthServiceImpl::toRoleResp).collect(Collectors.toList());
    }

    /**
     * 在临时指定的主体上下文中执行查询，执行完严格还原 {@link AppContext}。
     *
     * <p>平台的 {@code SysRoleApi.getUserRolesByUserIdAndSubjectId} 与 {@code
     * SysSpecialPermissionApi.checkUserSpecialPermission} 都只从 {@link AppContext} 读主体、不接受主体参数，而本流程需要
     * 遍历多个主体，因此必须临时切换。全过程同线程同步执行，且 finally 还原，不会污染同一请求的后续逻辑。
     */
    private <T> T withSubject(Long subjectId, Supplier<T> action) {
        String originalProjectNo = AppContext.getProjectNo();
        Long originalSubjectId = AppContext.getSubjectId();
        try {
            AppContext.setContext(originalProjectNo, subjectId);
            return action.get();
        } finally {
            AppContext.setContext(originalProjectNo, originalSubjectId);
        }
    }

    /** 真实登录人是否具备模拟登录权限。 */
    private boolean hasSimulateRight(Long subjectId, Long realLoginUserId) {
        CheckUserSpecialPermissionResp resp =
                withSubject(
                        subjectId,
                        () -> {
                            CheckUserSpecialPermissionReq permissionReq =
                                    new CheckUserSpecialPermissionReq();
                            permissionReq.setUserId(realLoginUserId);
                            permissionReq.setRightCode(AuthConstant.SIMULATE_LOGIN_RIGHT_CODE);
                            return sysSpecialPermissionApi.checkUserSpecialPermission(
                                    permissionReq);
                        });
        return resp != null && Boolean.TRUE.equals(resp.getHasPermission());
    }

    /** 读取真实登录人的当前角色缓存；读取失败或不存在都返回 null（与源实现一致，不向上抛）。 */
    private SubjectRoleDTO getUserCurrentRole(Long realLoginUserId) {
        String key = String.format(AuthConstant.USER_CURRENT_ROLE_KEY_PREFIX, realLoginUserId);
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(value)) {
                return null;
            }
            return objectMapper.readValue(value, SubjectRoleDTO.class);
        } catch (Exception e) {
            log.error("getUserCurrentRole from redis error, userId={}", realLoginUserId, e);
            return null;
        }
    }

    /** 刷新真实登录人的当前角色缓存。写失败按源实现向上抛，由外层兜底成 500。 */
    private void saveCurrentRole(
            Long realLoginUserId,
            Long loginUserId,
            Long finalSubjectId,
            Long finalRoleId,
            boolean mockFlag,
            SubjectRoleDTO oldSubjectRole)
            throws JsonProcessingException {
        SubjectRoleDTO subjectRoleDTO;
        if (mockFlag) {
            subjectRoleDTO =
                    SubjectRoleDTO.builder()
                            .mockUserId(loginUserId)
                            .mockSubjectId(finalSubjectId)
                            .mockRoleId(finalRoleId)
                            .subjectId(finalSubjectId)
                            .build();
            if (oldSubjectRole != null) {
                subjectRoleDTO.setRoleId(oldSubjectRole.getRoleId());
            }
        } else {
            subjectRoleDTO =
                    SubjectRoleDTO.builder().subjectId(finalSubjectId).roleId(finalRoleId).build();
        }
        String key = String.format(AuthConstant.USER_CURRENT_ROLE_KEY_PREFIX, realLoginUserId);
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(subjectRoleDTO));
    }

    /** 无任何角色时的业务异常；模拟登录时用 805，否则用 803。 */
    private static BusinessException noRight(boolean mockFlag) {
        return mockFlag
                ? new BusinessException(
                        ApiCodeEnum.MOCK_USER_NO_RIGHT.getCode(),
                        ApiCodeEnum.MOCK_USER_NO_RIGHT.getMessage())
                : new BusinessException(
                        ApiCodeEnum.USER_NO_RIGHT.getCode(),
                        ApiCodeEnum.USER_NO_RIGHT.getMessage());
    }

    /** 与源 {@code ToolUtils.equalsLongValue} 保持一致：任一为 null 即视为不相等。 */
    private static boolean equalsLongValue(Long left, Long right) {
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }

    private static SubjectResp findSubject(List<SubjectResp> subjects, Long subjectId) {
        return subjects.stream()
                .filter(s -> Objects.equals(s.getSubjectId(), subjectId))
                .findFirst()
                .orElse(null);
    }

    private static List<RoleResp> rolesOf(SubjectResp subject) {
        if (subject == null || subject.getRoleRespList() == null) {
            return new ArrayList<>();
        }
        return subject.getRoleRespList();
    }

    private static List<Long> roleIdsOf(List<RoleResp> roles) {
        return roles.stream().map(RoleResp::getRoleId).collect(Collectors.toList());
    }

    private static RoleResp toRoleResp(UserRoleListResp role) {
        return RoleResp.builder()
                .roleId(role.getRoleId())
                .roleName(role.getRoleName())
                .roleSlug(role.getRoleSlug())
                .subjectId(role.getSubjectId())
                .effectiveType(role.getEffectiveType())
                .effectiveStartDate(toDateString(role.getEffectiveStartDate()))
                .effectiveEndDate(toDateString(role.getEffectiveEndDate()))
                .roleSource(role.getRoleSource())
                .build();
    }

    private static MenuResp toMenuResp(RoleMenuResp menu) {
        return MenuResp.builder()
                .menuId(menu.getMenuId())
                .menuTitle(menu.getMenuTitle())
                .pid(menu.getPid())
                .param(menu.getParam())
                .menuType(menu.getMenuType())
                .category(menu.getCategory())
                .imgDefault(menu.getImgDefault())
                .imgActive(menu.getImgActive())
                .moduleId(menu.getModuleId())
                .sortOrder(menu.getSortOrder())
                .showed(menu.getShowed())
                .docUrl(menu.getDocUrl())
                .docName(menu.getDocName())
                .videoUrl(menu.getVideoUrl())
                .videoName(menu.getVideoName())
                .build();
    }

    /** LocalDate → "yyyy-MM-dd" 字符串（源契约中该字段为 String）。 */
    private static String toDateString(LocalDate date) {
        return date == null ? null : date.toString();
    }

    /** 按 pid 建树：pid 为 null 或 0 视为根，level 从 0 起逐层递增。 */
    private static List<MenuResp> buildMenuTree(List<MenuResp> flatMenus) {
        Map<Long, List<MenuResp>> childrenByParent = new HashMap<>();
        for (MenuResp menu : flatMenus) {
            Long parentId = menu.getPid() == null ? 0L : menu.getPid();
            childrenByParent.computeIfAbsent(parentId, key -> new ArrayList<>()).add(menu);
        }
        List<MenuResp> roots = childrenByParent.getOrDefault(0L, new ArrayList<>());
        for (MenuResp root : roots) {
            fillChildren(root, 0, childrenByParent);
        }
        return roots;
    }

    private static void fillChildren(
            MenuResp node, int level, Map<Long, List<MenuResp>> childrenByParent) {
        node.setLevel(level);
        List<MenuResp> children = childrenByParent.get(node.getMenuId());
        if (children == null || children.isEmpty()) {
            return;
        }
        node.setChildren(children);
        for (MenuResp child : children) {
            fillChildren(child, level + 1, childrenByParent);
        }
    }

    private static MenuResp findMenu(List<MenuResp> menus, Long menuId) {
        if (menuId == null) {
            return null;
        }
        return menus.stream().filter(m -> menuId.equals(m.getMenuId())).findFirst().orElse(null);
    }

    private static MenuResp findRootMenu(List<MenuResp> menus) {
        return menus.stream()
                .filter(m -> m.getPid() == null || m.getPid() == 0L)
                .findFirst()
                .orElse(null);
    }
}
