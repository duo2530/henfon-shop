package com.henfon.shop.trade.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.trade.entity.TradeOrder;
import com.henfon.shop.trade.entity.TradeOrderItem;
import com.henfon.shop.trade.entity.TradeOrderLogistics;
import com.henfon.shop.trade.mapper.TradeOrderMapper;
import com.henfon.shop.trade.service.TradeOrderService;
import com.henfon.shop.trade.dto.TradeOrderCreateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public ApiResponse<List<TradeOrder>> orders(@RequestParam Long memberId) {
        return ApiResponse.success(orderMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getMemberId, memberId).orderByDesc(TradeOrder::getCreatedAt)), MDC.get("requestId"));
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
    public ApiResponse<Map<String, Object>> order(@PathVariable Long orderId) {
        TradeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return ApiResponse.failure("TRADE_ORDER_NOT_FOUND", "订单不存在", MDC.get("requestId"));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", order);
        result.put("items", orderService.listItems(orderId));
        result.put("logistics", orderService.listLogistics(orderId));
        return ApiResponse.success(result, MDC.get("requestId"));
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
    public ApiResponse<TradeOrder> create(@Valid @RequestBody TradeOrderCreateRequest request) {
        return ApiResponse.success(orderService.create(request), MDC.get("requestId"));
    }
}
