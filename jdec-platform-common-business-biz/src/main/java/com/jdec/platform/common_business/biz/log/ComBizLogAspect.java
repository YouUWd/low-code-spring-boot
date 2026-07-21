package com.jdec.platform.common_business.biz.log;

import com.jdec.platform.common_business.biz.configcenter.client.openfeign.MonitorLogApiOpenFeignClient;
import com.jdec.platform.common_business.biz.dto.BizLogDTO;
import com.jdec.platform.shared.security.SecurityUtils;
import com.jdec.platform.shared.security.model.LoginUser;
import com.jdec.platform.shared.utils.RequestContextUtils;
import java.lang.reflect.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class ComBizLogAspect {

    private final MonitorLogApiOpenFeignClient monitorLogApiOpenFeignClient;

    @Pointcut("@annotation(com.jdec.platform.common_business.biz.log.ComBizLog)")
    public void logPointcut() {}

    @Around(value = "logPointcut()")
    public Object interceptor(ProceedingJoinPoint pj) throws Throwable {
        MethodSignature signature = (MethodSignature) pj.getSignature();
        Method method = signature.getMethod();
        ComBizLog annotation = method.getAnnotation(ComBizLog.class);
        Object result = pj.proceed();

        BizLogDTO bizLogDTO = ComBizLogContext.getBizLog();
        if (bizLogDTO != null) {
            try {
                // 从 UserContext 补充缺失的字段
                enrichBizLogFromContext(bizLogDTO);

                // TODO: 实现实际的日志发送逻辑
                log.info("业务日志: {}", bizLogDTO);

                //                MonitorLogReq req = new MonitorLogReq();
                //                req.setSubjectId(bizLogDTO.getCurrentSubjectId());
                //                req.setLogType(bizLogDTO.getLogType());
                //                req.setMonitorType("switch-role");
                //                req.setLogContent("AAAAAA");
                //                req.setLogRemark("");
                //                req.setLoginUserId(bizLogDTO.getLoginUserId());
                //                req.setLoginUserName("");
                //                req.setMockUserId(bizLogDTO.getMockUserId());
                //                req.setMockUserName("");
                //                req.setMockFlag(
                //                        bizLogDTO.getMockUserId() != null
                //                                        && bizLogDTO
                //                                                .getLoginUserId()
                //                                                .equals(bizLogDTO.getMockUserId())
                //                                ? 1
                //                                : 0);
                //                req.setLoginRole(null);
                //                req.setMockRole(null);
                //                req.setRoleName(null);
                //                req.setUserInfo("ddddd");
                //                req.setIp(bizLogDTO.getIp());
                //                req.setBrowser(bizLogDTO.getBrowser());
                //                req.setPlatForm(bizLogDTO.getBrowser());
                //                req.setLogDate(DateTime.now());
                //                req.setCreatedDate(DateTime.now());
                //                ConfigApiResponse<Void> response =
                // monitorLogApiOpenFeignClient.add(req);
                //                response.getData();
            } catch (Exception e) {
                log.error("业务日志处理失败", e);

            } finally {
                ComBizLogContext.clear();
            }
        }
        return result;
    }

    /**
     * 从 UserContext 补充 BizLogDTO 中缺失的字段
     *
     * <p>如果 BizLogDTO 中的字段为空，则从 SecurityUtils 和 RequestContextUtils 获取并填充
     */
    private void enrichBizLogFromContext(BizLogDTO bizLogDTO) {
        LoginUser loginUser = SecurityUtils.getLoginUser();

        // 补充 loginUserId
        if (bizLogDTO.getLoginUserId() == null && loginUser != null) {
            bizLogDTO.setLoginUserId(loginUser.getRealUserId());
        }

        // 补充 mockUserId（仅在模拟登录时）
        if (bizLogDTO.getMockUserId() == null
                && loginUser != null
                && Boolean.TRUE.equals(loginUser.getIsMockLogin())) {
            bizLogDTO.setMockUserId(loginUser.getMockUserId());
        }

        // 补充 currentSubjectId
        if (bizLogDTO.getCurrentSubjectId() == null && loginUser != null) {
            bizLogDTO.setCurrentSubjectId(loginUser.getSubjectId());
        }

        // 补充 currentRoleId
        if (bizLogDTO.getCurrentRoleId() == null && loginUser != null) {
            bizLogDTO.setCurrentRoleId(loginUser.getRoleId());
        }

        // 补充 ip
        if (bizLogDTO.getIp() == null || bizLogDTO.getIp().isEmpty()) {
            bizLogDTO.setIp(RequestContextUtils.getClientIp());
        }

        // 补充 browser
        if (bizLogDTO.getBrowser() == null || bizLogDTO.getBrowser().isEmpty()) {
            bizLogDTO.setBrowser(RequestContextUtils.getBrowser());
        }

        // 补充 platform
        if (bizLogDTO.getPlatform() == null || bizLogDTO.getPlatform().isEmpty()) {
            bizLogDTO.setPlatform(RequestContextUtils.getPlatform());
        }
    }
}
