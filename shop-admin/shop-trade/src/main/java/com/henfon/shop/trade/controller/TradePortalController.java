package com.henfon.shop.trade.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.entity.TradeOrderItem;
import com.henfon.shop.trade.entity.TradeOrderLogistics;
import com.henfon.shop.trade.mapper.TradeOrderMapper;
import com.henfon.shop.trade.service.TradeOrderService;
import com.henfon.shop.trade.dto.TradeOrderCreateRequest;
import com.henfon.shop.trade.dto.TradeOrderCancelRequest;
import com.henfon.shop.identity.security.MemberPrincipalResolver;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门户订单查询接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@RestController
@RequestMapping("/api/portal/trade")
public class TradePortalController {
    private final TradeOrderMapper orderMapper;
    private final TradeOrderService orderService;

    /**
     * 创建门户订单控制器。
     *
     * @param orderMapper 订单数据访问对象
     * @param orderService 订单服务
     * @author Henfon
     * @date 2026-08-29
     */
    public TradePortalController(TradeOrderMapper orderMapper, TradeOrderService orderService) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
    }

    /**
     * 查询会员订单。
     *
     * @param memberId 会员ID
     * @return 订单列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/orders")
    public ApiResponse<List<TradeOrder>> orders(@RequestParam(required = false) Long memberId,
                                                Authentication authentication) {
        Long currentMemberId = MemberPrincipalResolver.requireMemberId(authentication, memberId);
        return ApiResponse.success(orderMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getMemberId, currentMemberId).orderByDesc(TradeOrder::getCreatedAt)), MDC.get("requestId"));
    }

    /**
     * 查询门户订单详情及物流轨迹。
     *
     * @param orderId 订单ID
     * @return 订单详情聚合数据
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/orders/{orderId}")
    public ApiResponse<Map<String, Object>> order(@PathVariable Long orderId, Authentication authentication) {
        Long memberId = MemberPrincipalResolver.requireMemberId(authentication, null);
        TradeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return ApiResponse.failure("TRADE_ORDER_NOT_FOUND", "订单不存在", MDC.get("requestId"));
        }
        if (!memberId.equals(order.getMemberId())) {
            return ApiResponse.failure("TRADE_ORDER_FORBIDDEN", "无权查看该订单", MDC.get("requestId"));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", order);
        result.put("items", orderService.listItems(orderId));
        result.put("logistics", orderService.listLogistics(orderId));
        return ApiResponse.success(result, MDC.get("requestId"));
    }

    /**
     * 查询门户会员订单物流轨迹。
     *
     * @param orderId 订单ID
     * @param authentication 当前会员认证信息
     * @return 物流轨迹列表
     * @author Henfon
     * @date 2026-08-31
     */
    @GetMapping("/orders/{orderId}/logistics")
    public ApiResponse<List<TradeOrderLogistics>> logistics(@PathVariable Long orderId,
                                                            Authentication authentication) {
        Long memberId = MemberPrincipalResolver.requireMemberId(authentication, null);
        return ApiResponse.success(orderService.listMemberLogistics(memberId, orderId), MDC.get("requestId"));
    }

    /**
     * 创建门户订单。
     *
     * @param request 订单创建请求
     * @return 新订单
     * @author Henfon
     * @date 2026-08-29
     */
    @PostMapping("/orders")
    public ApiResponse<TradeOrder> create(@Valid @RequestBody TradeOrderCreateRequest request,
                                          Authentication authentication) {
        Long memberId = MemberPrincipalResolver.requireMemberId(authentication, request.memberId());
        TradeOrderCreateRequest normalized = new TradeOrderCreateRequest(memberId, request.items(),
                request.receiverName(), request.receiverPhone(), request.receiverProvince(), request.receiverCity(),
                request.receiverDistrict(), request.receiverAddress(), request.paymentMethod(), request.subtotalAmount(),
                request.discountAmount(), request.freightAmount(), request.payableAmount(), request.idempotencyKey());
        return ApiResponse.success(orderService.create(normalized), MDC.get("requestId"));
    }

    /**
     * 门户会员取消订单。
     *
     * @param orderId 订单ID
     * @param memberId 会员ID
     * @param request 取消原因
     * @return 空响应
     * @author Henfon
     * @date 2026-08-30
     */
    @PutMapping("/orders/{orderId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long orderId, @RequestParam(required = false) Long memberId,
                                    @Valid @RequestBody TradeOrderCancelRequest request,
                                    Authentication authentication) {
        orderService.cancelByMember(MemberPrincipalResolver.requireMemberId(authentication, memberId), orderId,
                request.reason());
        return ApiResponse.success(MDC.get("requestId"));
    }

    /**
     * 门户会员确认收货。
     *
     * @param orderId 订单ID
     * @param memberId 会员ID
     * @return 空响应
     * @author Henfon
     * @date 2026-08-30
     */
    @PutMapping("/orders/{orderId}/confirm")
    public ApiResponse<Void> confirmReceive(@PathVariable Long orderId, @RequestParam(required = false) Long memberId,
                                            Authentication authentication) {
        orderService.confirmReceive(MemberPrincipalResolver.requireMemberId(authentication, memberId), orderId);
        return ApiResponse.success(MDC.get("requestId"));
    }
}
