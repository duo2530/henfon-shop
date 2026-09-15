package com.henfon.shop.identity.dto;

/**
 * 物流承运商字典响应。
 *
 * @param code 承运商编码
 * @param name 承运商名称
 * @param sortNo 排序值
 * @author Henfon
 * @date 2026-09-15
 */
public record LogisticsCarrierResponse(String code, String name, Integer sortNo) {
}
