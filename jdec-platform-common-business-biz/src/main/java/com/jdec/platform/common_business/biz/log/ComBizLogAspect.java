package com.jdec.platform.common_business.biz.log;

import com.jdec.platform.common_business.biz.configcenter.client.openfeign.MonitorLogApiOpenFeignClient;
import com.jdec.platform.common_business.biz.dto.BizLogDTO;
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
                log.error("", e);

            } finally {
                ComBizLogContext.clear();
            }
        }
        return result;
    }
}
