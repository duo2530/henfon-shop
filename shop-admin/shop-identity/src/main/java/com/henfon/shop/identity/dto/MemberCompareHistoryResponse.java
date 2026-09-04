package com.henfon.shop.identity.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门户商品对比历史响应。
 *
 * @author Henfon
 * @date 2026-09-04
 */
public record MemberCompareHistoryResponse(Long id, LocalDateTime comparedAt, List<Long> productIds) {
}
