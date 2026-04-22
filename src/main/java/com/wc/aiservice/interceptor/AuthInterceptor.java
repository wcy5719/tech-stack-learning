package com.wc.aiservice.interceptor;

import com.wc.aiservice.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        
        if (path.contains("/health") || path.contains("/api/public")) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        String token = jwtService.extractTokenFromHeader(authHeader);

        if (token == null || !jwtService.validateToken(token)) {
            log.warn("认证失败: path={}, token={}", path, token != null ? "present" : "null");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing token\"}");
            response.setContentType("application/json");
            return false;
        }

        String userId = jwtService.getUserId(token);
        request.setAttribute("userId", userId);
        request.setAttribute("username", jwtService.getUsername(token));
        
        log.debug("认证成功: userId={}, path={}", userId, path);
        return true;
    }
}
