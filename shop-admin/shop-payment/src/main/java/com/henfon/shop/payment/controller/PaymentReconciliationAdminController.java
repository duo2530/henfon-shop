package com.henfon.shop.payment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.payment.dto.PaymentReconciliationRecord;
import com.henfon.shop.payment.service.PaymentReconciliationService;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台财务对账查询接口。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@RestController
@RequestMapping("/api/admin/payment/reconciliation")
public class PaymentReconciliationAdminController {

    private final PaymentReconciliationService reconciliationService;

    /**
     * 创建财务对账控制器。
     *
     * @param reconciliationService 财务对账服务
     * @author Henfon
     * @date 2026-09-01
     */
    public PaymentReconciliationAdminController(PaymentReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * 分页查询财务流水。
     *
     * @param keyword 流水号、订单号或备注关键字
     * @param type 流水类型
     * @param status 对账状态
     * @param current 当前页
     * @param size 页大小
     * @return 财务流水分页结果
     * @author Henfon
     * @date 2026-09-01
     */
    @GetMapping
    @PreAuthorize("hasAuthority('payment:transaction:query')")
    public ApiResponse<IPage<PaymentReconciliationRecord>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(reconciliationService.page(keyword, type, status, current, size),
                MDC.get("requestId"));
    }
}
