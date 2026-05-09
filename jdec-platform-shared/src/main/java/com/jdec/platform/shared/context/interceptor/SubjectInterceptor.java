package com.jdec.platform.shared.context.interceptor;

import com.jdec.platform.shared.context.SubjectContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class SubjectInterceptor implements HandlerInterceptor {

    private static final String SUBJECT_ID_HEADER = "subject-id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,Object handler) {
        String subjectId = request.getHeader(SUBJECT_ID_HEADER);
        if (subjectId != null && !subjectId.isEmpty()) {
            SubjectContext.set(Long.valueOf(subjectId));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        SubjectContext.remove();
    }
}
