package com.henfon.shop.trade.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.identity.security.MemberPrincipalResolver;
import com.henfon.shop.trade.dto.TradeAfterSaleCreateRequest;
import com.henfon.shop.trade.entity.TradeAfterSale;
import com.henfon.shop.trade.service.TradeAfterSaleService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 门户售后申请接口。
 *
 * @author Henfon
 * @date 2026-08-30
 */
@RestController
@RequestMapping("/api/portal/trade/after-sales")
public class TradeAfterSalePortalController {

    private final TradeAfterSaleService afterSaleService;

    /**
     * 创建门户售后控制器。
     *
     * @param afterSaleService 售后应用服务
     * @author Henfon
     * @date 2026-08-30
     */
    public TradeAfterSalePortalController(TradeAfterSaleService afterSaleService) {
        this.afterSaleService = afterSaleService;
    }

    /**
     * 查询当前会员售后单。
     *
     * @param memberId 会员ID
     * @param authentication 当前认证信息
     * @return 售后单列表
     * @author Henfon
     * @date 2026-08-30
     */
    @GetMapping
    public ApiResponse<List<TradeAfterSale>> list(@RequestParam(required = false) Long memberId,
                                                   Authentication authentication) {
        Long currentMemberId = MemberPrincipalResolver.requireMemberId(authentication, memberId);
        return ApiResponse.success(afterSaleService.listByMember(currentMemberId), MDC.get("requestId"));
    }

    /**
     * 提交售后申请。
     *
     * @param orderId 订单ID
     * @param memberId 会员ID
     * @param request 售后申请
     * @param authentication 当前认证信息
     * @return 新建售后单
     * @author Henfon
     * @date 2026-08-30
     */
    @PostMapping
    public ApiResponse<TradeAfterSale> create(@RequestParam Long orderId,
                                               @RequestParam(required = false) Long memberId,
                                               @Valid @RequestBody TradeAfterSaleCreateRequest request,
                                               Authentication authentication) {
        Long currentMemberId = MemberPrincipalResolver.requireMemberId(authentication, memberId);
        return ApiResponse.success(afterSaleService.create(currentMemberId, orderId, request), MDC.get("requestId"));
    }

    /**
     * 取消待审核售后申请。
     *
     * @param afterSaleId 售后单ID
     * @param memberId 会员ID
     * @param authentication 当前认证信息
     * @return 更新后的售后单
     * @author Henfon
     * @date 2026-08-30
     */
    @DeleteMapping("/{afterSaleId}")
    public ApiResponse<TradeAfterSale> cancel(@PathVariable Long afterSaleId,
                                              @RequestParam(required = false) Long memberId,
                                              Authentication authentication) {
        Long currentMemberId = MemberPrincipalResolver.requireMemberId(authentication, memberId);
        return ApiResponse.success(afterSaleService.cancel(currentMemberId, afterSaleId), MDC.get("requestId"));
    }
}
