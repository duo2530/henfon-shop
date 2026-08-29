package com.henfon.shop.common.exception;

/**
 * 可预期的业务异常。
 *
 * @author Henfon
 * @date 2026-08-29
 */
public class BusinessException extends RuntimeException {

    private final String code;

    /**
     * 创建业务异常。
     *
     * @param code 业务错误码
     * @param message 业务错误信息
     * @author Henfon
     * @date 2026-08-29
     */
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 获取业务错误码。
     *
     * @return 业务错误码
     * @author Henfon
     * @date 2026-08-29
     */
    public String getCode() {
        return code;
    }
}
