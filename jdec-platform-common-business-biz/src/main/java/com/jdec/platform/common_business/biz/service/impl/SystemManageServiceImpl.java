package com.jdec.platform.common_business.biz.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.common_business.api.SystemManageServiceApi;
import com.jdec.platform.common_business.api.dto.common.SubjectRoleDTO;
import com.jdec.platform.common_business.api.dto.request.SelectRoleReq;
import com.jdec.platform.common_business.api.dto.response.MenuResp;
import com.jdec.platform.common_business.api.dto.response.RoleResp;
import com.jdec.platform.common_business.api.dto.response.SelectRoleResp;
import com.jdec.platform.common_business.api.dto.response.SubjectResp;
import com.jdec.platform.common_business.biz.common.CommonConstant;
import com.jdec.platform.common_business.biz.configcenter.client.ConfigCenterApiClient;
import com.jdec.platform.common_business.biz.dto.configcenter.requst.CheckUserSpecialPermissionReq;
import com.jdec.platform.common_business.biz.dto.configcenter.response.CheckUserSpecialPermissionResp;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import com.jdec.platform.common_business.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.common_business.biz.service.ConfigCenterApiService;
import com.jdec.platform.common_business.biz.util.JsonUtil;
import com.jdec.platform.common_business.biz.util.LoginUserUtil;
import com.jdec.platform.common_business.biz.util.ToolUtils;
import com.jdec.platform.common_business.biz.websocket.UserSessionHolder;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.ApiResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class SystemManageServiceImpl implements SystemManageServiceApi {

    private final RedisTemplate<String, String> redisTemplate;

    private final ConfigCenterApiService configCenterApiService;

    private final ConfigCenterApiClient configCenterApiClient;

    private final Integer DEFAULT_EMPTY_ROLE_ID = Integer.valueOf(0);

    @Override
    public synchronized ApiResponse<SelectRoleResp> selectRole(SelectRoleReq req) {

        Long realLoginUserId = LoginUserUtil.getRealLoginUserId();
        // 模拟用户或者 登录用户
        Long loginUserId = LoginUserUtil.getLoginUserId();

        // 是否 操作模拟用户 loginUserId已经校验是 合法的
        Boolean mockFlag = !realLoginUserId.equals(loginUserId);
        Boolean simulateLogin = Boolean.FALSE;

        // 当前主题, 当前角色ID，这两个值 都可能为空
        Long subjectId = req.getSubjectId();
        Long roleId = req.getRoleId();

        try {
            // 获取用户主题下,及角色,无论是否有模拟用户，主题列表永远查询 当前登录人的
            // 角色列表，查询 登录人的或者模拟人的
            List<SubjectResp> subjectRespList =
                    configCenterApiService.getUserSubjectList(
                            LoginUserUtil.getLoginSubjectId(), realLoginUserId);
            for (SubjectResp subjectResp : subjectRespList) {
                List<RoleResp> roleRespList =
                        configCenterApiService.getRoleListBySubjectAndUserId(
                                subjectResp.getId().longValue(), loginUserId);
                subjectResp.setRoleRespList(roleRespList);
            }

            if (CollectionUtil.isEmpty(subjectRespList)) {
                // 用户没有 主题列表，退出登录，跳转到登录页面
                // todo removeUserToken(r) 是否删除 用户登录token 模拟用户如何返回
                if (!mockFlag) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.USER_NO_RIGHT.getCode(),
                            ApiResponseCodeEnum.USER_NO_RIGHT.getMessage());
                } else {
                    throw new BusinessException(
                            ApiResponseCodeEnum.MOCK_USER_NO_RIGHT.getCode(),
                            ApiResponseCodeEnum.MOCK_USER_NO_RIGHT.getMessage());
                }
            }
            Long finalRoleId = null;
            Long finalSubjectId = null;
            // 这个地方只能用户 当前 登录用户 取查 当前登录用户的 角色切换信息
            SubjectRoleDTO oldSubjectRole = getUserCurrentRole(subjectId, realLoginUserId);
            // 如果角色没有传，那么从，缓存中获取
            if (roleId != null) {
                finalRoleId = roleId;
                finalSubjectId = subjectId; // subjectId,是跟着角色来的,如果角色有值，subjectID 一定有值
            } else {
                // 获取当前当前登录用户的 当前角色
                if (!mockFlag) {
                    // 如果缓存中没有 或者，缓存的主题 与 请求的主题 不一致，那么使用 请求的主题
                    if (oldSubjectRole == null
                            || !ToolUtils.equalsLongValue(
                                    oldSubjectRole.getSubjectId(), subjectId)) {
                        finalRoleId = roleId;
                        finalSubjectId = subjectId;
                    } else {
                        // 尝试从缓存中 获取用户的角色
                        finalRoleId = oldSubjectRole != null ? oldSubjectRole.getRoleId() : 0L;
                        subjectId = oldSubjectRole != null ? oldSubjectRole.getSubjectId() : 0L;
                        finalSubjectId = subjectId;
                    }
                } else {
                    // 当前 登录人 操作的模拟用户 在缓存中存在
                    if (oldSubjectRole != null
                            && loginUserId.equals(oldSubjectRole.getMockUserId())) {

                        // 如果缓存中没有 或者，缓存的主题 与 请求的主题 不一致，那么使用 请求的主题
                        if (!ToolUtils.equalsLongValue(
                                oldSubjectRole.getMockSubjectId(), subjectId)) {
                            finalRoleId = roleId;
                            finalSubjectId = subjectId;
                        } else {
                            finalRoleId =
                                    oldSubjectRole != null ? oldSubjectRole.getMockRoleId() : 0L;
                            subjectId =
                                    oldSubjectRole != null ? oldSubjectRole.getMockSubjectId() : 0L;
                            finalSubjectId = subjectId;
                        }
                    }
                }
            }
            // 这个地方 用户没有传入 subjectID,缓存也没有,获取用户有权限的第一个
            if (finalSubjectId == null) {
                subjectId = subjectRespList.stream().findFirst().get().getId().longValue();
                finalSubjectId = subjectId;
            }

            // 获取用户当前主题下的所有角色，这里的用户有可能是 mock用户
            final Integer f = subjectId.intValue();
            SubjectResp findSubjectResp =
                    subjectRespList.stream()
                            .filter(x -> x.getId().equals(f))
                            .findFirst()
                            .orElse(null);
            List<RoleResp> roleRespList =
                    findSubjectResp != null
                            ? CollectionUtil.isNotEmpty(findSubjectResp.getRoleRespList())
                                    ? findSubjectResp.getRoleRespList()
                                    : new ArrayList<>()
                            : new ArrayList<>();
            List<Long> roleIdList =
                    roleRespList.stream().map(x -> x.getRoleId()).collect(Collectors.toList());
            // 如果当前主题下没有角色 需要切换主题
            if (CollectionUtil.isEmpty(roleIdList)) {
                for (SubjectResp subjectResp : subjectRespList) {
                    Integer subject = subjectResp.getId();
                    if (!subjectId.equals(Long.valueOf(subject.longValue()))) {
                        roleRespList = subjectResp.getRoleRespList();
                        roleIdList =
                                roleRespList.stream()
                                        .map(x -> x.getRoleId())
                                        .collect(Collectors.toList());
                        finalSubjectId = subject.longValue();
                        if (CollectionUtil.isNotEmpty(roleIdList)) {
                            break;
                        }
                    }
                }
            }
            if (CollectionUtil.isEmpty(roleIdList)) {
                // todo removeUserToken(r) 是否删除 用户登录token
                // 用户所有主题下都没有角色
                if (!mockFlag) {
                    throw new BusinessException(
                            ApiResponseCodeEnum.USER_NO_RIGHT.getCode(),
                            ApiResponseCodeEnum.USER_NO_RIGHT.getMessage());
                } else {
                    throw new BusinessException(
                            ApiResponseCodeEnum.MOCK_USER_NO_RIGHT.getCode(),
                            ApiResponseCodeEnum.MOCK_USER_NO_RIGHT.getMessage());
                }
            }

            // 获取主题下 的第一个角色
            if (!roleIdList.contains(finalRoleId)
                    || finalRoleId == null
                    || DEFAULT_EMPTY_ROLE_ID.equals(finalRoleId)) {
                finalRoleId = roleIdList.getFirst();
            }

            // 这个地方无论如何  最终的 主题ID 和 RoleID 都已经拿到了
            ConfigApiResponse<CheckUserSpecialPermissionResp> checkUserSpecialResponse =
                    configCenterApiClient.checkUserSpecialPermission(
                            finalSubjectId,
                            CheckUserSpecialPermissionReq.builder()
                                    .userId(realLoginUserId)
                                    .rightCode(
                                            CommonConstant.CONFIG_CENTER_SIMULATE_LOGIN_RIGHT_CODE)
                                    .build());
            if (ConfigApiResponse.isSuccessResponse(checkUserSpecialResponse)
                    && checkUserSpecialResponse.getData() != null
                    && checkUserSpecialResponse.getData().getHasPermission() != null
                    && checkUserSpecialResponse.getData().getHasPermission()) {
                simulateLogin = true;
            }
            // 获取当前用户的模拟权限
            /** simulateLogin=true; */
            // 是在使用 模拟用户 // 校验当前登录用户是否有 模拟 权限
            if (mockFlag && !simulateLogin) {
                // subjectId 如何取
                // 校验当前用户是否有模拟权限
                // 用户没有模拟权限
                // ApiResponseCodeEnum.USER_NO_MOCK_RIGHT
                throw new BusinessException(
                        ApiResponseCodeEnum.USER_NO_MOCK_RIGHT.getCode(),
                        ApiResponseCodeEnum.USER_NO_MOCK_RIGHT.getMessage());
            }

            SelectRoleResp selectRoleResp = new SelectRoleResp();

            // 如果不是在模拟用户操作，过滤掉没有角色列表的主题
            // 如果是在模拟用户操作，不用过滤（主题列表是登录人的）
            if (!mockFlag) {
                selectRoleResp.setSubjectList(
                        subjectRespList.stream()
                                .filter(x -> CollectionUtil.isNotEmpty(x.getRoleRespList()))
                                .collect(Collectors.toList()));
            } else {
                selectRoleResp.setSubjectList(subjectRespList);
            }

            selectRoleResp.setSubjectId(finalSubjectId);
            selectRoleResp.setRoleId(finalRoleId);
            selectRoleResp.setSimulateLogin(simulateLogin);

            ApiResponse apiResponse = null;

            ConfigApiResponse<List<MenuResp>> response =
                    configCenterApiClient.getMenuListByRoleId(finalSubjectId, finalRoleId);
            // 构建用户菜单 角色下有菜单
            if (ConfigApiResponse.isSuccessResponse(response)
                    && CollectionUtil.isNotEmpty(response.getData())) {
                List<MenuResp> menuRespList = response.getData();
                menuRespList.sort(
                        Comparator.comparing(MenuResp::getCategory)
                                .thenComparing(MenuResp::getSortOrder));
                Long finalMenuId = req.getMenuId();
                final Long fm = finalMenuId;
                MenuResp finalMenuResp =
                        menuRespList.stream()
                                .filter(x -> x.getMenuId().equals(fm))
                                .findFirst()
                                .orElse(null);
                if (finalMenuResp == null) {
                    finalMenuResp =
                            menuRespList.stream()
                                    .filter(x -> x.getPid().equals(Long.valueOf(0)))
                                    .findFirst()
                                    .orElse(null);
                    finalMenuId = finalMenuResp.getMenuId();
                }
                selectRoleResp.setMenuId(finalMenuId);
                selectRoleResp.setMenuList(ToolUtils.buildTree(menuRespList, 0L, "menuId", "pid"));
                apiResponse = ApiResponse.success(selectRoleResp);
            } else {
                selectRoleResp.setMenuId(null);
                selectRoleResp.setMenuList(new ArrayList<>());
                apiResponse =
                        ApiResponse.customResponse(
                                ApiResponseCodeEnum.USER_ROLE_NO_PERMISSION.getCode(),
                                ApiResponseCodeEnum.USER_ROLE_NO_PERMISSION.getMessage(),
                                selectRoleResp);
            }
            // 检查这个角色 有没有权限
            ConfigApiResponse<Boolean> permissionResponse =
                    configCenterApiClient.checkRolePermission(finalSubjectId, finalRoleId);
            // 这个角色 有权限
            if (ConfigApiResponse.isSuccessResponse(permissionResponse)
                    && permissionResponse.getData()) {
                // 角色有权限不处理
            } else {
                // 角色没有权限
                apiResponse.setStatus(ApiResponseCodeEnum.USER_ROLE_NO_PERMISSION.getCode());
                apiResponse.setMsg(ApiResponseCodeEnum.USER_ROLE_NO_PERMISSION.getMessage());
                //                apiResponse =
                //                        ApiResponse.customResponse(
                //
                // ApiResponseCodeEnum.USER_ROLE_NO_PERMISSION.getCode(),
                //
                // ApiResponseCodeEnum.USER_ROLE_NO_PERMISSION.getMessage(),
                //                                selectRoleResp);
            }

            String key =
                    String.format(
                            CommonConstant.USER_CURRENT_ROLE_REDIS_KEY_PREFIX,
                            realLoginUserId.toString());
            SubjectRoleDTO subjectRoleDTO = null;
            if (!mockFlag) {
                subjectRoleDTO =
                        SubjectRoleDTO.builder()
                                .subjectId(finalSubjectId) // 登录人的主题每次刷
                                .roleId(finalRoleId)
                                .build();
            } else {
                subjectRoleDTO =
                        SubjectRoleDTO.builder()
                                .mockUserId(loginUserId)
                                .mockSubjectId(finalSubjectId)
                                .mockRoleId(finalRoleId)
                                .subjectId(finalSubjectId) // 登录人的主题每次刷
                                .build();
                if (oldSubjectRole != null) {
                    // subjectRoleDTO.setSubjectId(oldSubjectRole.getSubjectId());
                    subjectRoleDTO.setRoleId(oldSubjectRole.getRoleId());
                }
            }
            redisTemplate.opsForValue().set(key, JsonUtil.obj2Json(subjectRoleDTO));
            // 这个ws连接要处理

            // 非模拟操作
            if (!mockFlag) {
                // 上一次缓存中 有MockUser,说明是退出模拟操作
                if (oldSubjectRole != null && oldSubjectRole.getMockUserId() != null) {
                    // MockUserHolder.removeMapping(oldSubjectRole.getMockUserId(),
                    // realLoginUserId);
                    // 上一次的角色 是 mockRoleId
                    log.info(
                            "user quit-mock  selectRole  realLoginUserId={},mockUserId={},preRole={},currentRole={},preSubjectRole={}",
                            realLoginUserId,
                            oldSubjectRole.getMockUserId(),
                            oldSubjectRole.getMockRoleId(),
                            finalRoleId,
                            oldSubjectRole.toString());
                    UserSessionHolder.roleChangeSessionProcess(
                            false, realLoginUserId, finalSubjectId, finalRoleId, 0L, 0L, 0L);
                } else {
                    // 说明 是用户一直没有模拟操作
                    if (oldSubjectRole != null) {
                        log.info(
                                "user none-mock  selectRole  realLoginUserId={},preRole={},currentRole={},preSubjectRole={}",
                                realLoginUserId,
                                oldSubjectRole.getRoleId(),
                                finalRoleId,
                                oldSubjectRole.toString());
                        UserSessionHolder.roleChangeSessionProcess(
                                false, realLoginUserId, finalSubjectId, finalRoleId, 0L, 0L, 0L);
                    }
                }
            } else {
                // 如果是模拟操作
                // 进入当前的模拟用户
                if (oldSubjectRole != null) {
                    // 用户从上个 模拟用户 进入当前模拟用户
                    if (oldSubjectRole.getMockUserId() != null
                            && !loginUserId.equals(oldSubjectRole.getMockUserId())) {
                        // 移除上个模拟用户的 映射
                        // MockUserHolder.removeMapping(oldSubjectRole.getMockUserId(),
                        // realLoginUserId);
                        // 建立 当前 模拟用户 映射
                        // MockUserHolder.addMapping(loginUserId, realLoginUserId);

                        log.info(
                                "user switch-mock selectRole  realLoginUserId={} from preMockUserId={} to currentMockUserId={} ,preRole={},currentRole={},preSubjectRole={}",
                                realLoginUserId,
                                oldSubjectRole.getMockUserId(),
                                loginUserId,
                                oldSubjectRole.getMockRoleId(),
                                finalRoleId,
                                oldSubjectRole.toString());
                        UserSessionHolder.roleChangeSessionProcess(
                                true,
                                realLoginUserId,
                                finalSubjectId,
                                0L,
                                loginUserId,
                                finalSubjectId,
                                finalRoleId);
                    } else if (oldSubjectRole.getMockUserId() == null) {
                        // 单纯的进入 当前模拟用户
                        // 建立 当前 模拟用户 映射
                        // MockUserHolder.addMapping(loginUserId, realLoginUserId);
                        log.info(
                                "user first-mock selectRole  realLoginUserId={} to currentMockUserId={} ,preRole={},currentRole={},preSubjectRole={}",
                                realLoginUserId,
                                loginUserId,
                                oldSubjectRole.getRoleId(),
                                finalRoleId,
                                oldSubjectRole.toString());
                        UserSessionHolder.roleChangeSessionProcess(
                                true,
                                realLoginUserId,
                                finalSubjectId,
                                0L,
                                loginUserId,
                                finalSubjectId,
                                finalRoleId);
                    } else {
                        // loginUserId.equals(oldSubjectRole.getMockUserId())
                        // 用户一直在用户当前用户 mock
                        // MockUserHolder.addMapping(loginUserId, realLoginUserId); // 幂等操作
                        log.info(
                                "user always-mock selectRole  realLoginUserId={} to currentMockUserId={} ,preRole={},currentRole={},preSubjectRole={}",
                                realLoginUserId,
                                loginUserId,
                                oldSubjectRole.getMockRoleId(),
                                finalRoleId,
                                oldSubjectRole.toString());
                        UserSessionHolder.roleChangeSessionProcess(
                                true,
                                realLoginUserId,
                                finalSubjectId,
                                0L,
                                loginUserId,
                                finalSubjectId,
                                finalRoleId);
                    }
                } else {
                    // 这种情况 数据问题 ，mock 时 oldSubjectRole 不能为空
                    log.error(
                            "user mock selectRole preSubjectRole is empty,realLoginUserId={},currentMockUserId={}",
                            realLoginUserId,
                            loginUserId);
                    throw new BusinessException(
                            ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                            ApiResponseCodeEnum.SERVER_ERROR.getMessage());
                }
            }
            return apiResponse;
        } catch (Exception e) {
            log.error(
                    "selectRole error,userId="
                            + realLoginUserId
                            + ",subjectId="
                            + subjectId
                            + ",roleId="
                            + roleId
                            + ",e=",
                    e);
            if (e instanceof BusinessException be) {
                throw new BusinessException(be.getCode(), be.getMessage(), e);
            } else {
                throw new BusinessException(
                        ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                        ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                        e);
            }
        }
    }

    @Override
    public SubjectRoleDTO getUserCurrentRole(Long subjectId, Long userId) {
        String key =
                String.format(CommonConstant.USER_CURRENT_ROLE_REDIS_KEY_PREFIX, userId.toString());
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (StringUtils.isEmpty(value)) {
                return null;
            }
            return JsonUtil.json2Obj(value, SubjectRoleDTO.class);
        } catch (Exception e) {
            log.error("getUserCurrentRole from redis error,userId=" + userId + ",e=", e);
            return null;
            //            throw new BusinessException(
            //                    ApiResponseCodeEnum.SERVER_ERROR.getCode(),
            //                    ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
            //                    e);
        }
    }

    @Override
    public Boolean checkUserToken(String token, String phone) {
        try {
            String key = token + ":" + phone;
            String value = redisTemplate.opsForValue().get(key);
            if (StringUtils.isNotEmpty(value)) {
                return Boolean.TRUE;
            }
        } catch (Exception e) {
            log.error("checkUserToken error,e=", e);
        }
        return Boolean.FALSE;
    }

    @Override
    public Boolean removeUserToken(String token, String phone) {
        try {
            String key = token + ":" + phone;
            return redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("removeUserToken error,e=", e);
        }
        return Boolean.FALSE;
    }
}
