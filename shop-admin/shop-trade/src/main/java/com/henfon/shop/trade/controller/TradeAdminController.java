package com.henfon.shop.trade.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.entity.TradeOrderItem;
import com.henfon.shop.trade.entity.TradeOrderLogistics;
import com.henfon.shop.trade.service.TradeOrderService;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台交易订单接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@RestController
@RequestMapping("/api/admin/trade")
public class TradeAdminController {

    private final TradeOrderService tradeOrderService;

    /**
     * 创建交易后台控制器。
     *
     * @param tradeOrderService 订单应用服务
     * @author Henfon
     * @date 2026-08-29
     */
    public TradeAdminController(TradeOrderService tradeOrderService) {
        this.tradeOrderService = tradeOrderService;
    }

    /**
     * 分页查询订单。
     *
     * @param keyword 订单号、会员或收货人关键字
     * @param orderStatus 订单状态
     * @param current 当前页
     * @param size 页大小
     * @return 订单分页数据
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('trade:order:query')")
    public ApiResponse<IPage<TradeOrder>> pageOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer orderStatus,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(tradeOrderService.page(keyword, orderStatus, current, size), MDC.get("requestId"));
    }

    /**
     * 查询订单明细。
     *
     * @param orderId 订单ID
     * @return 订单明细列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/orders/{orderId}/items")
    @PreAuthorize("hasAuthority('trade:order:query')")
    public ApiResponse<List<TradeOrderItem>> listOrderItems(@PathVariable Long orderId) {
        return ApiResponse.success(tradeOrderService.listItems(orderId), MDC.get("requestId"));
    }

    /**
     * 查询订单物流轨迹。
     *
     * @param orderId 订单ID
     * @return 物流轨迹列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/orders/{orderId}/logistics")
    @PreAuthorize("hasAuthority('trade:order:query')")
    public ApiResponse<List<TradeOrderLogistics>> listLogistics(@PathVariable Long orderId) {
        return ApiResponse.success(tradeOrderService.listLogistics(orderId), MDC.get("requestId"));
    }
}
