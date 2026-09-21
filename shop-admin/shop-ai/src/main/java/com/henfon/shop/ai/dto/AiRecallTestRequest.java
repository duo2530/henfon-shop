package com.henfon.shop.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识库召回测试请求。
 *
 * @author Henfon
 * @date 2026-09-21
 */
@Data
public class AiRecallTestRequest {

    /** 测试问句，按买家的原话写最有参考价值。 */
    @NotBlank(message = "测试问句不能为空")
    @Size(max = 500, message = "测试问句不能超过 500 字")
    private String question;

    /** 类目编码，为空时不按类目过滤；商品类问题可填类目看过滤后的召回。 */
    @Size(max = 32, message = "类目编码不能超过 32 字")
    private String categoryCode;
}
