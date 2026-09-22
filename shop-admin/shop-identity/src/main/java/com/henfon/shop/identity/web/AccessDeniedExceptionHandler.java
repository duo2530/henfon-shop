package com.henfon.shop.identity.web;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.common.web.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 方法级鉴权失败的响应处理。
 *
 * <p>{@code @PreAuthorize} 拒绝发生在控制器方法调用期，异常先进入 MVC 异常处理链，
 * 会被 shop-common 的兜底处理器当成系统异常返回 500「系统繁忙」。这里以更高优先级
 * 抢先接管，返回与 {@code SecurityConfiguration#accessDeniedHandler} 一致的 403 结果，
 * 避免权限不足被误报成服务故障、也不再把可预期结果打成错误堆栈。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessDeniedExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AccessDeniedExceptionHandler.class);

    /**
     * 将鉴权拒绝翻译为 403 响应。
     *
     * @param exception 鉴权拒绝异常
     * @return 无权访问响应
     * @author Henfon
     * @date 2026-09-21
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        // 归属正常的鉴权结果，只记摘要便于排查是谁被拦，不打堆栈。
        log.warn("拒绝访问: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure("AUTH_FORBIDDEN", "无权访问该资源", MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY)));
    }
}
