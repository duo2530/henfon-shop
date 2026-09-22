package com.henfon.shop.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 工作台推送商品卡片的请求体。
 *
 * 客服只给出商品主键与一句说明，标题、图片与价格都由服务端查库补齐：图片地址在库里存的
 * 是对象键，必须换发成带签名的访问地址才能显示，客服手填的一定是过期链接。
 *
 * @param productId 商品主键
 * @param note 随卡片一起发出的说明，可为空
 * @author Henfon
 * @date 2026-09-22
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiAgentProductDraft(Long productId, String note) {
}
