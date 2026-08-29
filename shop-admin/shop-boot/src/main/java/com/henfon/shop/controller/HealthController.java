package com.henfon.shop.controller;

import com.henfon.shop.common.api.ApiResponse;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 应用基础健康检查接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * 返回应用存活状态。
     *
     * @return 应用健康状态
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping
    public ApiResponse<Map<String, String>> health() {
        // 该接口只检查应用进程，数据库和中间件状态交给 Actuator 及后续专项检查。
        return ApiResponse.success(Map.of("status", "UP", "service", "shop-admin"),
                MDC.get("requestId"));
    }
}
