package com.jdec.platform.shared.context.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.model.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppContextInterceptor implements HandlerInterceptor {
    private static final String PROJECT_NO_HEADER = "X-Project-No";
    private static final String SUBJECT_ID_HEADER = "X-Subject-Id";
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String projectNo = request.getHeader(PROJECT_NO_HEADER);

        // 验证 project-no 必填
        if (projectNo == null || projectNo.isEmpty()) {
            log.error("请求缺少必需的 Header: {}", PROJECT_NO_HEADER);
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json;charset=UTF-8");
            ApiResponse<Void> apiResponse =
                    ApiResponse.error(500, "Missing required header: " + PROJECT_NO_HEADER);
            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
            return false;
        }

        String subjectIdStr = request.getHeader(SUBJECT_ID_HEADER);
        Long subjectId =
                (subjectIdStr != null && !subjectIdStr.isEmpty())
                        ? Long.valueOf(subjectIdStr)
                        : null;
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
