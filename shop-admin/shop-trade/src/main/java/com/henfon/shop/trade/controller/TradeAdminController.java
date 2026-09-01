package com.henfon.shop.trade.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.entity.TradeOrderItem;
import com.henfon.shop.trade.entity.TradeOrderLogistics;
import com.henfon.shop.trade.dto.TradeOrderShipRequest;
import com.henfon.shop.trade.dto.TradeOrderCancelRequest;
import com.henfon.shop.trade.dto.TradeOrderRemarkRequest;
import com.henfon.shop.trade.dto.TradeOrderRefundRequest;
import com.henfon.shop.trade.dto.TradeOrderLogisticsRequest;
import com.henfon.shop.trade.dto.TradeOrderLogisticsSyncResult;
import com.henfon.shop.trade.dto.TradeOrderBatchShipRequest;
import jakarta.validation.Valid;
import com.henfon.shop.trade.service.TradeOrderService;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /**
     * 调用已配置的物流服务商同步订单轨迹。
     *
     * @param orderId 订单ID
     * @return 同步结果
     * @author Henfon
     * @date 2026-09-01
     */
    @PostMapping("/orders/{orderId}/logistics/sync")
    @PreAuthorize("hasAuthority('trade:order:ship')")
    public ApiResponse<TradeOrderLogisticsSyncResult> syncLogistics(@PathVariable Long orderId) {
        return ApiResponse.success(tradeOrderService.syncLogistics(orderId), MDC.get("requestId"));
    }

    /**
     * 追加或更新订单物流节点。
     *
     * @param orderId 订单ID
     * @param request 物流节点信息
     * @return 保存后的物流节点
     * @author Henfon
     * @date 2026-08-31
     */
    @PostMapping("/orders/{orderId}/logistics")
    @PreAuthorize("hasAuthority('trade:order:ship')")
    public ApiResponse<TradeOrderLogistics> upsertLogistics(@PathVariable Long orderId,
                                                            @Valid @RequestBody TradeOrderLogisticsRequest request) {
        return ApiResponse.success(tradeOrderService.upsertLogistics(orderId, request), MDC.get("requestId"));
    }

    /**
     * 更新指定订单物流节点。
     *
     * @param orderId 订单ID
     * @param logisticsId 物流节点ID
     * @param request 物流节点信息
     * @return 更新后的物流节点
     * @author Henfon
     * @date 2026-08-31
     */
    @PutMapping("/orders/{orderId}/logistics/{logisticsId}")
    @PreAuthorize("hasAuthority('trade:order:ship')")
    public ApiResponse<TradeOrderLogistics> updateLogistics(@PathVariable Long orderId,
                                                            @PathVariable Long logisticsId,
                                                            @Valid @RequestBody TradeOrderLogisticsRequest request) {
        return ApiResponse.success(tradeOrderService.updateLogistics(orderId, logisticsId, request), MDC.get("requestId"));
    }

    /**
     * 后台订单发货。
     *
     * @param orderId 订单ID
     * @param request 发货信息
     * @return 空响应
     * @author Henfon
     * @date 2026-08-29
     */
    @PutMapping("/orders/{orderId}/ship")
    @PreAuthorize("hasAuthority('trade:order:ship')")
    public ApiResponse<Void> ship(@PathVariable Long orderId,
                                  @Valid @RequestBody TradeOrderShipRequest request) {
        tradeOrderService.ship(orderId, request);
        return ApiResponse.success(MDC.get("requestId"));
    }

    /**
     * 后台订单批量发货。
     *
     * @param request 批量发货信息
     * @return 成功发货订单数
     * @author Henfon
     * @date 2026-09-01
     */
    @PostMapping("/orders/batch-ship")
    @PreAuthorize("hasAuthority('trade:order:ship')")
    public ApiResponse<Integer> batchShip(@Valid @RequestBody TradeOrderBatchShipRequest request) {
        return ApiResponse.success(tradeOrderService.batchShip(request), MDC.get("requestId"));
    }

    /**
     * 后台取消订单。
     *
     * @param orderId 订单ID
     * @param request 取消原因
     * @return 空响应
     * @author Henfon
     * @date 2026-08-29
     */
    @PutMapping("/orders/{orderId}/cancel")
    @PreAuthorize("hasAuthority('trade:order:cancel')")
    public ApiResponse<Void> cancel(@PathVariable Long orderId,
                                    @Valid @RequestBody TradeOrderCancelRequest request) {
        tradeOrderService.cancel(orderId, request);
        return ApiResponse.success(MDC.get("requestId"));
    }

    /**
     * 更新订单卖家备注。
     *
     * @param orderId 订单ID
     * @param request 备注内容
     * @return 更新后的订单
     * @author Henfon
     * @date 2026-08-29
     */
    @PutMapping("/orders/{orderId}/remark")
    @PreAuthorize("hasAuthority('trade:order:remark')")
    public ApiResponse<TradeOrder> updateRemark(@PathVariable Long orderId,
                                                @Valid @RequestBody TradeOrderRemarkRequest request) {
        return ApiResponse.success(tradeOrderService.updateRemark(orderId, request), MDC.get("requestId"));
    }

    /**
     * 后台确认订单退款。
     *
     * @param orderId 订单ID
     * @param request 退款信息
     * @return 空响应
     * @author Henfon
     * @date 2026-08-29
     */
    @PutMapping("/orders/{orderId}/refund")
    @PreAuthorize("hasAuthority('trade:order:refund')")
    public ApiResponse<Void> refund(@PathVariable Long orderId,
                                    @Valid @RequestBody TradeOrderRefundRequest request) {
        tradeOrderService.refund(orderId, request);
        return ApiResponse.success(MDC.get("requestId"));
    }
}
