package com.henfon.shop.identity.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.identity.dto.MemberAddressRequest;
import com.henfon.shop.identity.entity.MemberAddress;
import com.henfon.shop.identity.entity.MemberCompareHistory;
import com.henfon.shop.identity.entity.MemberFavorite;
import com.henfon.shop.identity.entity.MemberUser;
import com.henfon.shop.identity.service.MemberPortalService;
import com.henfon.shop.identity.security.MemberPrincipalResolver;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * 门户会员接口。
 *
 * @author Henfon
 * @date 2026-08-29
 */
@RestController
@RequestMapping("/api/portal/member")
public class MemberPortalController {
    private final MemberPortalService service;

    /**
     * 创建门户会员控制器。
     *
     * @param service 门户会员服务
     * @author Henfon
     * @date 2026-08-29
     */
    public MemberPortalController(MemberPortalService service) {
        this.service = service;
    }

    /**
     * 查询会员资料。
     *
     * @param memberId 会员ID
     * @return 会员资料
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/profile")
    public ApiResponse<MemberUser> profile(@RequestParam(required = false) Long memberId,
                                          Authentication authentication) {
        return ApiResponse.success(service.profile(MemberPrincipalResolver.requireMemberId(authentication, memberId)),
                MDC.get("requestId"));
    }

    /**
     * 查询会员地址。
     *
     * @param memberId 会员ID
     * @return 地址列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/addresses")
    public ApiResponse<List<MemberAddress>> addresses(@RequestParam(required = false) Long memberId,
                                                     Authentication authentication) {
        return ApiResponse.success(service.addresses(MemberPrincipalResolver.requireMemberId(authentication, memberId)),
                MDC.get("requestId"));
    }

    /**
     * 保存会员地址。
     *
     * @param request 地址请求
     * @return 地址ID
     * @author Henfon
     * @date 2026-08-29
     */
    @PostMapping("/addresses")
    public ApiResponse<Long> saveAddress(@Valid @RequestBody MemberAddressRequest request,
                                         Authentication authentication) {
        Long memberId = MemberPrincipalResolver.requireMemberId(authentication, request.memberId());
        return ApiResponse.success(service.saveAddress(normalizeAddressRequest(request, memberId)), MDC.get("requestId"));
    }

    /**
     * 更新会员地址。
     *
     * @param addressId 地址ID
     * @param request 地址内容
     * @return 空响应
     * @author Henfon
     * @date 2026-08-29
     */
    @PutMapping("/addresses/{addressId}")
    public ApiResponse<Void> updateAddress(@PathVariable Long addressId,
                                            @Valid @RequestBody MemberAddressRequest request,
                                            Authentication authentication) {
        Long memberId = MemberPrincipalResolver.requireMemberId(authentication, request.memberId());
        service.updateAddress(addressId, normalizeAddressRequest(request, memberId));
        return ApiResponse.success(MDC.get("requestId"));
    }

    /**
     * 删除会员地址。
     *
     * @param addressId 地址ID
     * @param memberId 会员ID
     * @return 空响应
     * @author Henfon
     * @date 2026-08-29
     */
    @DeleteMapping("/addresses/{addressId}")
    public ApiResponse<Void> deleteAddress(@PathVariable Long addressId,
                                            @RequestParam(required = false) Long memberId,
                                            Authentication authentication) {
        memberId = MemberPrincipalResolver.requireMemberId(authentication, memberId);
        service.deleteAddress(memberId, addressId);
        return ApiResponse.success(MDC.get("requestId"));
    }

    /**
     * 设置会员默认地址。
     *
     * @param addressId 地址ID
     * @param memberId 会员ID
     * @return 空响应
     * @author Henfon
     * @date 2026-08-29
     */
    @PutMapping("/addresses/{addressId}/default")
    public ApiResponse<Void> setDefaultAddress(@PathVariable Long addressId,
                                                @RequestParam(required = false) Long memberId,
                                                Authentication authentication) {
        memberId = MemberPrincipalResolver.requireMemberId(authentication, memberId);
        service.setDefaultAddress(memberId, addressId);
        return ApiResponse.success(MDC.get("requestId"));
    }

    /**
     * 查询会员收藏。
     *
     * @param memberId 会员ID
     * @return 收藏列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/favorites")
    public ApiResponse<List<MemberFavorite>> favorites(@RequestParam(required = false) Long memberId,
                                                      Authentication authentication) {
        return ApiResponse.success(service.favorites(MemberPrincipalResolver.requireMemberId(authentication, memberId)),
                MDC.get("requestId"));
    }

    /**
     * 切换商品收藏状态。
     *
     * @param productId 商品ID
     * @param memberId 会员ID
     * @return 是否已收藏
     * @author Henfon
     * @date 2026-08-29
     */
    @PostMapping("/favorites/{productId}/toggle")
    public ApiResponse<Boolean> toggleFavorite(@PathVariable Long productId,
                                               @RequestParam(required = false) Long memberId,
                                               Authentication authentication) {
        memberId = MemberPrincipalResolver.requireMemberId(authentication, memberId);
        return ApiResponse.success(service.toggleFavorite(memberId, productId), MDC.get("requestId"));
    }

    /**
     * 保存商品对比记录。
     *
     * @param memberId 会员ID
     * @param productIds 商品ID列表
     * @return 历史记录ID
     * @author Henfon
     * @date 2026-08-29
     */
    @PostMapping("/compare/history")
    public ApiResponse<Long> saveCompare(@RequestParam(required = false) Long memberId,
                                         @RequestBody List<Long> productIds,
                                         Authentication authentication) {
        memberId = MemberPrincipalResolver.requireMemberId(authentication, memberId);
        return ApiResponse.success(service.saveCompare(memberId, productIds), MDC.get("requestId"));
    }

    /**
     * 查询商品对比历史。
     *
     * @param memberId 会员ID
     * @param limit 返回条数
     * @return 对比历史列表
     * @author Henfon
     * @date 2026-08-29
     */
    @GetMapping("/compare/history")
    public ApiResponse<List<MemberCompareHistory>> compareHistory(@RequestParam(required = false) Long memberId,
                                                                   @RequestParam(defaultValue = "20") int limit,
                                                                   Authentication authentication) {
        memberId = MemberPrincipalResolver.requireMemberId(authentication, memberId);
        return ApiResponse.success(service.compareHistory(memberId, limit), MDC.get("requestId"));
    }

    /**
     * 使用认证主体重建地址请求，忽略客户端伪造的会员ID。
     *
     * @param request 原始地址请求
     * @param memberId 认证会员ID
     * @return 归属已校正的地址请求
     * @author Henfon
     * @date 2026-08-30
     */
    private MemberAddressRequest normalizeAddressRequest(MemberAddressRequest request, Long memberId) {
        return new MemberAddressRequest(request.id(), memberId, request.receiverName(), request.receiverPhone(),
                request.province(), request.city(), request.district(), request.detailAddress(),
                request.addressTag(), request.isDefault());
    }
}
