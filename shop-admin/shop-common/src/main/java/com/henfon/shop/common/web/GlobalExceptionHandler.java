package com.henfon.shop.common.web;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常转换器，确保管理端和门户端使用统一错误格式。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理可预期业务异常。
     *
     * @param exception 业务异常
     * @return 业务错误响应
     * @author Henfon
     * @date 2026-08-29
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(exception.getCode(), exception.getMessage(), requestId()));
    }

    /**
     * 处理请求参数校验异常。
     *
     * @param exception 参数校验异常
     * @return 参数错误响应
     * @author Henfon
     * @date 2026-08-29
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("VALIDATION_ERROR", message, requestId()));
    }

    /**
     * 处理方法参数约束异常。
     *
     * @param exception 约束校验异常
     * @return 参数错误响应
     * @author Henfon
     * @date 2026-08-29
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("VALIDATION_ERROR", exception.getMessage(), requestId()));
    }

    /**
     * 处理未预期的系统异常。
     *
     * @param exception 系统异常
     * @param request 当前请求
     * @return 系统错误响应
     * @author Henfon
     * @date 2026-08-29
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception, HttpServletRequest request) {
        // 对外隐藏堆栈信息，完整异常交由日志系统记录。
        log.error("请求处理失败: {} {}", request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("SYSTEM_ERROR", "系统繁忙，请稍后重试", requestId()));
    }

    /**
     * 读取当前请求标识。
     *
     * @return 请求链路标识
     * @author Henfon
     * @date 2026-08-29
     */
    private String requestId() {
        return MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY);
    }
}
