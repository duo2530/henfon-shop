package com.henfon.shop.trade.controller;

import com.henfon.shop.common.api.ApiResponse;
import com.henfon.shop.trade.dto.TradeFreightTemplateSaveRequest;
import com.henfon.shop.trade.entity.TradeFreightTemplate;
import com.henfon.shop.trade.service.TradeFreightService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台运费模板接口。
 *
 * @author Henfon
 * @date 2026-09-01
 */
@RestController
@RequestMapping("/api/admin/trade/freight")
public class TradeFreightAdminController {

    private final TradeFreightService freightService;

    /**
     * 创建后台运费模板控制器。
     *
     * @param freightService 运费服务
     * @author Henfon
     * @date 2026-09-01
     */
    public TradeFreightAdminController(TradeFreightService freightService) {
        this.freightService = freightService;
    }

    /**
     * 查询当前默认运费模板。
     *
     * @return 默认运费模板
     * @author Henfon
     * @date 2026-09-01
     */
    @GetMapping("/template")
    @PreAuthorize("hasAuthority('system:config:view')")
    public ApiResponse<TradeFreightTemplate> getTemplate() {
        return ApiResponse.success(freightService.getDefaultTemplate(), MDC.get("requestId"));
    }

    /**
     * 保存当前默认运费模板。
     *
     * @param request 运费模板请求
     * @return 保存后的模板
     * @author Henfon
     * @date 2026-09-01
     */
    @PutMapping("/template")
    @PreAuthorize("hasAuthority('system:config:save')")
    public ApiResponse<TradeFreightTemplate> saveTemplate(@Valid @RequestBody TradeFreightTemplateSaveRequest request) {
        return ApiResponse.success(freightService.saveTemplate(request), MDC.get("requestId"));
    }
}
