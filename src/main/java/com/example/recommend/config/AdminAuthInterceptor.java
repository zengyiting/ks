package com.example.recommend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String SESSION_KEY = "adminLoggedIn";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();

        if (path.equals("/api/admin/login") || path.equals("/api/admin/status")) {
            return true;
        }

        if (path.startsWith("/api/admin")) {
            HttpSession session = request.getSession(false);
            if (session == null || !Boolean.TRUE.equals(session.getAttribute(SESSION_KEY))) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                try {
                    response.getWriter().write("{\"success\":false,\"message\":\"未登录或登录已过期\"}");
                } catch (Exception ignored) {
                }
                return false;
            }
        }
        return true;
    }
}
