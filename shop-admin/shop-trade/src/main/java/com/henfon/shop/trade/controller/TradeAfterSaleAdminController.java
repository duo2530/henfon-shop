package com.henfon.shop.trade.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.trade.dto.TradeAfterSaleAuditRequest;
import com.henfon.shop.trade.entity.TradeAfterSale;
import com.henfon.shop.trade.service.TradeAfterSaleService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台售后审核接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/admin/trade/after-sales")
public class TradeAfterSaleAdminController {

    private final TradeAfterSaleService afterSaleService;

    /**
     * 创建后台售后控制器。
     *
     * @param afterSaleService 售后应用服务
     * @author Henfon
     * @date 2026-08-30
     */
    public TradeAfterSaleAdminController(TradeAfterSaleService afterSaleService) {
        this.afterSaleService = afterSaleService;
    }

    /**
     * 分页查询售后单。
     *
     * @param status 售后状态
     * @param orderId 订单ID
     * @param current 当前页
     * @param size 页大小
     * @return 售后单分页结果
     * @author Henfon
     * @date 2026-08-30
     */
    @GetMapping
    @PreAuthorize("hasAuthority('trade:after-sale:query')")
    public ApiResponse<IPage<TradeAfterSale>> page(@RequestParam(required = false) Integer status,
                                                    @RequestParam(required = false) Long orderId,
                                                    @RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(afterSaleService.page(status, orderId, current, size), MDC.get("requestId"));
    }

    /**
     * 审核通过售后单。
     *
     * @param afterSaleId 售后单ID
     * @param request 审核备注
     * @return 更新后的售后单
     * @author Henfon
     * @date 2026-08-30
     */
    @PutMapping("/{afterSaleId}/approve")
    @PreAuthorize("hasAuthority('trade:after-sale:audit')")
    public ApiResponse<TradeAfterSale> approve(@PathVariable Long afterSaleId,
                                                @Valid @RequestBody(required = false) TradeAfterSaleAuditRequest request) {
        return ApiResponse.success(afterSaleService.approve(afterSaleId, request), MDC.get("requestId"));
    }

    /**
     * 驳回售后单。
     *
     * @param afterSaleId 售后单ID
     * @param request 驳回备注
     * @return 更新后的售后单
     * @author Henfon
     * @date 2026-08-30
     */
    @PutMapping("/{afterSaleId}/reject")
    @PreAuthorize("hasAuthority('trade:after-sale:audit')")
    public ApiResponse<TradeAfterSale> reject(@PathVariable Long afterSaleId,
                                               @Valid @RequestBody(required = false) TradeAfterSaleAuditRequest request) {
        return ApiResponse.success(afterSaleService.reject(afterSaleId, request), MDC.get("requestId"));
    }

    /**
     * 确认退货入库并触发退款。
     *
     * @param afterSaleId 售后单ID
     * @param request 入库备注
     * @return 更新后的售后单
     * @author Henfon
     * @date 2026-09-01
     */
    @PutMapping("/{afterSaleId}/return-received")
    @PreAuthorize("hasAuthority('trade:after-sale:audit')")
    public ApiResponse<TradeAfterSale> confirmReturn(@PathVariable Long afterSaleId,
                                                       @Valid @RequestBody(required = false) TradeAfterSaleAuditRequest request) {
        return ApiResponse.success(afterSaleService.confirmReturn(afterSaleId, request == null ? null : request.remark()),
                MDC.get("requestId"));
    }
}
