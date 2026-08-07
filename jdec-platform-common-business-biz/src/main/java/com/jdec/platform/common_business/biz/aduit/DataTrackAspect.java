package com.jdec.platform.common_business.biz.aduit;

import com.jdec.platform.common_business.biz.configcenter.client.openfeign.ConfigCenterApiOpenFeignClient;
import com.jdec.platform.common_business.biz.service.SystemModuleDataChangeLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;

/** 数据审计切面 拦截 @DataAudit 注解的方法，记录数据变更审计日志 */
@Slf4j
// @Aspect
// @Component
@RequiredArgsConstructor
public class DataTrackAspect {
    private final SystemModuleDataChangeLogService systemModuleDataChangeLogService;

    private final ConfigCenterApiOpenFeignClient configCenterApiClient;

    @Around("@annotation(dataTrack)")
    public Object around(ProceedingJoinPoint joinPoint, DataTrack dataTrack) throws Throwable {
        //        DataTrackTypeEnum type=dataTrack.type();
        //        DataUpdateContextHolder.openDataChangeFlag(type);
        //        Object result=joinPoint.proceed();
        //        try {
        //            if (DataUpdateContextHolder.getDataChangeFlagTop() ==
        // DataTrackTypeEnum.NORMAL) {
        //                Object[] paramValues = joinPoint.getArgs();
        //                Long subjectId = (Long) paramValues[0];
        //                Long roleId = (Long) paramValues[1];
        //                List<MenuSaveReq> list = (List) paramValues[2];
        //
        //                Long menuId = list.get(0).getMenuId();
        //                try {
        //                    ConfigApiResponse<List<MenuListResp>> response =
        // configCenterApiClient.getMenuList(subjectId, 1);
        //                    if (!ConfigApiResponse.isSuccessResponse(response)) {
        //                        throw new BusinessException(
        //                                ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
        //                                ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        //                    }
        //                    List<MenuListResp> menuRespList = response.getData();
        //                    if (CollectionUtil.isEmpty(menuRespList)) {
        //                        throw new BusinessException(
        //                                ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
        //                                ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        //                    }
        //
        //                    // 筛选业务菜单，去掉系统设置菜单
        //                    List<MenuListResp> bizMenuList = new ArrayList<>();
        //                    bizMenuList.addAll(
        //                            menuRespList.stream().filter(ele ->
        // ele.getCategory().equals(1)).toList());
        //                    bizMenuList.sort(Comparator.comparing(MenuListResp::getSortOrder));
        //
        //                    MenuListResp menuListResp = bizMenuList.stream().filter(x ->
        // x.getId().equals(menuId)).findFirst().orElse(null);
        //                    if (menuListResp == null) {
        //                        throw new BusinessException(
        //                                ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
        //                                ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        //                    }
        //                    Long moduleId = menuListResp.getModuleId();
        //                    List<Object> targets = DataUpdateContextHolder.getDataUpdateContext();
        //                    if (CollectionUtil.isNotEmpty(targets)) {
        //                        ModuleDataChangeContext context = new ModuleDataChangeContext();
        //                        context.setSubjectId(subjectId);
        //                        context.setMainModuleId(moduleId);
        //                        context.setMenuId(menuId);
        //                        context.setDataId((Long) result);
        //                        context.setRealUserId(LoginUserUtil.getRealLoginUserId());
        //                        context.setMockUserId(
        //
        // !LoginUserUtil.getRealLoginUserId().equals(LoginUserUtil.getLoginUserId())
        //                                        ? LoginUserUtil.getLoginUserId()
        //                                        : null);
        //                        context.setRoleId(roleId);
        //                        List<DataChangeContext> dataChangeContextList = new ArrayList<>();
        //                        for (Object object : targets) {
        //                            DataChangeContext dataChangeContext = (DataChangeContext)
        // object;
        //                            dataChangeContextList.add(dataChangeContext);
        //                        }
        //                        context.setCallType(CallTypeEnum.EDITE);
        //                        context.setDataChangeContextList(dataChangeContextList);
        //                        DataUpdateContextHolder.setModuleDataChangeContext(context);
        //                        if (DataUpdateContextHolder.getDataChangeFlagSize() == 1) {
        //                            systemModuleDataChangeLogService.saveLog(context);
        //                        }
        //                    }
        //                } catch (Exception e) {
        //                    log.error("DataTrackAspect error,e=", e);
        //                }
        //            }
        //        }finally {
        //            if(DataUpdateContextHolder.getDataChangeFlagSize()>1){
        //                DataUpdateContextHolder.removeDataChangeFlag();
        //            }else {
        //                DataUpdateContextHolder.removeDataChangeFlag();
        //                DataUpdateContextHolder.clear();
        //            }
        //        }
        return null;
    }

    //    @AfterReturning("@annotation(dataTrack)")
    //    public void after(JoinPoint joinPoint, DataTrack dataTrack) throws Throwable {
    //        if(DataUpdateContextHolder.getDataChangeFlagSize()>1){
    //            DataUpdateContextHolder.removeDataChangeFlag();
    //        }else {
    //            DataUpdateContextHolder.removeDataChangeFlag();
    //            DataUpdateContextHolder.clear();
    //        }
    //    }

}
