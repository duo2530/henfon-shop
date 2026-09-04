package com.henfon.shop.trade.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.trade.dto.TradeCartItemRequest;
import com.henfon.shop.trade.dto.TradeCartItemUpdateRequest;
import com.henfon.shop.trade.dto.TradeCartMergeItemRequest;
import com.henfon.shop.trade.entity.TradeCartItem;
import com.henfon.shop.trade.service.TradeCartService;
import com.henfon.shop.identity.security.MemberPrincipalResolver;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * 门户购物车接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@RestController
@RequestMapping("/api/portal/trade/cart")
public class TradeCartPortalController {
    private final TradeCartService service;

    /**
     * 创建门户购物车控制器。
     *
     * @param service 购物车服务
     * @author Henfon
     * @date 2026-08-29
     */
    public TradeCartPortalController(TradeCartService service) {
        this.service = service;
    }

    /**
     * 查询购物车。
     *
     * @param memberId 会员ID
     * @return 购物车明细
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping
    public ApiResponse<List<TradeCartItem>> list(@RequestParam(required = false) Long memberId,
                                                 Authentication authentication) {
        return ApiResponse.success(service.list(MemberPrincipalResolver.requireMemberId(authentication, memberId)),
                MDC.get("requestId"));
    }

    /**
     * 主动清理当前会员购物车失效商品。
     *
     * @param authentication 当前认证信息
     * @return 清理数量
     * @author Henfon
     * @date 2026-09-04
     */
    @PostMapping("/clean-invalid")
    public ApiResponse<Integer> cleanInvalid(Authentication authentication) {
        Long memberId = MemberPrincipalResolver.requireMemberId(authentication, null);
        // 返回清理数量，前端可据此提示用户并重新拉取购物车。
        return ApiResponse.success(service.cleanInvalid(memberId), MDC.get("requestId"));
    }

    /**
     * 合并登录前本地购物车明细。
     *
     * @param requests 本地购物车明细
     * @param authentication 当前认证信息
     * @return 成功合并的明细数量
     * @author Henfon
     * @date 2026-09-04
     */
    @PostMapping("/merge")
    public ApiResponse<Integer> merge(@Valid @RequestBody List<@Valid TradeCartMergeItemRequest> requests,
                                      Authentication authentication) {
        Long memberId = MemberPrincipalResolver.requireMemberId(authentication, null);
        return ApiResponse.success(service.merge(memberId, requests), MDC.get("requestId"));
    }

    /**
     * 添加购物车商品。
     *
     * @param request 购物车请求
     * @return 明细ID
     * @author Henfon
     * @date 2026-08-29
     */
    @PostMapping("/items")
    public ApiResponse<Long> add(@Valid @RequestBody TradeCartItemRequest request,
                                 Authentication authentication) {
        Long memberId = MemberPrincipalResolver.requireMemberId(authentication, request.memberId());
        TradeCartItemRequest normalized = new TradeCartItemRequest(memberId, request.productId(), request.skuId(),
                request.quantity(), request.selected());
        return ApiResponse.success(service.add(normalized), MDC.get("requestId"));
    }

    /**
     * 删除购物车商品。
     *
     * @param id 明细ID
     * @return 空响应
     * @author Henfon
     * @date 2026-08-29
     */
    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        service.delete(MemberPrincipalResolver.requireMemberId(authentication, null), id);
        return ApiResponse.success(MDC.get("requestId"));
    }

    /**
     * 更新购物车明细。
     *
     * @param id 明细ID
     * @param request 更新请求
     * @return 空响应
     * @author Henfon
     * @date 2026-08-29
     */
    @PutMapping("/items/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody TradeCartItemUpdateRequest request,
                                    Authentication authentication) {
        service.update(MemberPrincipalResolver.requireMemberId(authentication, null), id, request);
        return ApiResponse.success(MDC.get("requestId"));
    }
}
