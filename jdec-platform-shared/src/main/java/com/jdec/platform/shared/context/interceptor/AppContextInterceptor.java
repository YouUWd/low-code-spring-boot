package com.jdec.platform.shared.context.interceptor;

import com.jdec.platform.shared.context.AppContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class AppContextInterceptor implements HandlerInterceptor {

    private static final String SUBJECT_ID_HEADER = "X-Subject-Id";
    private static final String PROJECT_NO_HEADER = "X-Project-No";

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        String subjectIdStr = request.getHeader(SUBJECT_ID_HEADER);
        Long subjectId =
                (subjectIdStr != null && !subjectIdStr.isEmpty())
                        ? Long.valueOf(subjectIdStr)
                        : null;
        String projectNo = request.getHeader(PROJECT_NO_HEADER);

        AppContext.setContext(projectNo, subjectId);
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        AppContext.remove();
    }
}
