package com.henfon.shop.common.api;

/**
 * 统一接口响应结构。
 *
 * @param <T> 响应数据类型
 * @author Henfon
 * @date 2026-08-29
 */
public record ApiResponse<T>(String code, String message, T data, String requestId) {

    /**
     * 创建成功响应。
     *
     * @param data 响应数据
     * @param requestId 请求链路标识
     * @param <T> 响应数据类型
     * @return 成功响应
     * @author Henfon
     * @date 2026-08-29
     */
    public static <T> ApiResponse<T> success(T data, String requestId) {
        // 请求标识由 Web 层注入，便于前后端排查同一条请求。
        return new ApiResponse<>("0", "success", data, requestId);
    }

    /**
     * 创建无数据成功响应。
     *
     * @param requestId 请求链路标识
     * @return 成功响应
     * @author Henfon
     * @date 2026-08-29
     */
    public static ApiResponse<Void> success(String requestId) {
        return success(null, requestId);
    }

    /**
     * 创建失败响应。
     *
     * @param code 业务错误码
     * @param message 错误信息
     * @param requestId 请求链路标识
     * @return 失败响应
     * @author Henfon
     * @date 2026-08-29
     */
    public static <T> ApiResponse<T> failure(String code, String message, String requestId) {
        // 错误码由业务层定义，HTTP 状态码由异常处理器决定。
        return new ApiResponse<>(code, message, null, requestId);
    }
}
