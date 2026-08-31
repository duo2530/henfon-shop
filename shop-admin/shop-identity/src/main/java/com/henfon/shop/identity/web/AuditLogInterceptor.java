package com.henfon.shop.identity.web;

import com.henfon.shop.identity.security.AuthenticatedUser;
import com.henfon.shop.identity.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理端请求审计拦截器。
 *
 * @author Henfon
 * @date 2026-08-31
 */
@Component
public class AuditLogInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTRIBUTE = AuditLogInterceptor.class.getName() + ".START_TIME";
    private final AuditLogService auditLogService;

    /**
     * 创建审计拦截器。
     *
     * @param auditLogService 审计日志服务
     * @author Henfon
     * @date 2026-08-31
     */
    public AuditLogInterceptor(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * 记录请求开始时间。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param handler 请求处理器
     * @return 是否继续处理
     * @author Henfon
     * @date 2026-08-31
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        return true;
    }

    /**
     * 请求完成后记录管理端操作日志。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param handler 请求处理器
     * @param exception 请求异常
     * @author Henfon
     * @date 2026-08-31
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        String uri = request.getRequestURI();
        // 登录日志和审计查询不再重复记录，避免递归写日志和无效噪声。
        if (!uri.startsWith("/api/admin/") || uri.startsWith("/api/admin/auth/login")
                || uri.startsWith("/api/admin/audit/")) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long userId = null;
        String username = null;
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            userId = user.userId();
            username = user.username();
        }
        Object start = request.getAttribute(START_TIME_ATTRIBUTE);
        long duration = start instanceof Long value ? System.currentTimeMillis() - value : 0;
        String query = request.getQueryString();
        String params = query == null ? null : query.substring(0, Math.min(query.length(), 2000));
        String[] segments = uri.split("/");
        String module = segments.length > 3 ? segments[3] : "admin";
        String operation = request.getMethod() + " " + uri;
        auditLogService.recordOperation(MDC.get("requestId"), userId, username, module, operation,
                request.getMethod(), uri, params, response.getStatus(), request.getRemoteAddr(), duration);
    }
}
