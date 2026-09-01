package com.henfon.shop.trade.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.trade.dto.TradeFreightQuoteRequest;
import com.henfon.shop.trade.dto.TradeFreightQuoteResponse;
import com.henfon.shop.trade.service.TradeFreightService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门户运费试算接口。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@RestController
@RequestMapping("/api/portal/trade/freight")
public class TradeFreightPortalController {

    private final TradeFreightService freightService;

    /**
     * 创建门户运费试算控制器。
     *
     * @param freightService 运费服务
     * @author Henfon
     * @date 2026-09-01
     */
    public TradeFreightPortalController(TradeFreightService freightService) {
        this.freightService = freightService;
    }

    /**
     * 试算当前购物车的运费。
     *
     * @param request 运费试算请求
     * @return 运费试算结果
     * @author Henfon
     * @date 2026-09-01
     */
    @PostMapping("/quote")
    public ApiResponse<TradeFreightQuoteResponse> quote(@Valid @RequestBody TradeFreightQuoteRequest request) {
        return ApiResponse.success(freightService.quote(request), MDC.get("requestId"));
    }
}
