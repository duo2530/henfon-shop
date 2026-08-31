package com.henfon.shop.config;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * 记录管理端和门户端 HTTP 请求指标，供 Actuator metrics 查询。
 *
 * @author Henfon
 * @date 2026-08-31
 */
public class ApiMetricsInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTRIBUTE = ApiMetricsInterceptor.class.getName() + ".startTime";

    private final MeterRegistry meterRegistry;

    /**
     * 创建 API 指标拦截器。
     *
     * @param meterRegistry Micrometer 指标注册表
     * @author Henfon
     * @date 2026-08-31
     */
    public ApiMetricsInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 记录请求开始时间。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param handler 当前处理器
     * @return 是否继续执行
     * @author Henfon
     * @date 2026-08-31
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTRIBUTE, System.nanoTime());
        return true;
    }

    /**
     * 在请求结束时记录计数器和耗时指标。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param handler 当前处理器
     * @param exception 请求异常
     * @author Henfon
     * @date 2026-08-31
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        if (request.getRequestURI().startsWith("/actuator")) {
            return;
        }
        String method = request.getMethod();
        String path = normalizePath(request.getRequestURI());
        String status = Integer.toString(response.getStatus());
        meterRegistry.counter("shop.http.requests", "method", method, "path", path, "status", status).increment();

        Object started = request.getAttribute(START_TIME_ATTRIBUTE);
        if (started instanceof Long startNanos) {
            long elapsedNanos = Math.max(0L, System.nanoTime() - startNanos);
            meterRegistry.timer("shop.http.request.duration", "method", method, "path", path, "status", status)
                    .record(elapsedNanos, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * 将数字资源 ID 归一化，避免指标标签产生无限基数。
     *
     * @param requestUri 请求路径
     * @return 归一化后的路径
     * @author Henfon
     * @date 2026-08-31
     */
    private String normalizePath(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return "/";
        }
        return requestUri.replaceAll("/\\d+(?=/|$)", "/{id}");
    }
}
